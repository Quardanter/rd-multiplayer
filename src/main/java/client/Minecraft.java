package client;

import client.hud.Chat;
import client.hud.Crosshair;
import client.hud.Info;
import client.level.Chunk;
import client.level.Level;
import client.level.LevelRenderer;
import client.net.PlayerManager;
import global.Packets;
import client.net.SocketClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Properties;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.util.glu.GLU.gluPerspective;
import static org.lwjgl.util.glu.GLU.gluPickMatrix;

public class Minecraft implements Runnable {
    public static Minecraft mc;

    public String username;

    public static final String GIT_HASH;
    static {
        String hash = "unknown";
        try (InputStream in = Minecraft.class.getResourceAsStream("/git.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                hash = props.getProperty("git.commit", hash);
            }
        } catch (Exception ignored) {}
        GIT_HASH = hash;
    }

    public long rtt;

    private final Timer timer = new Timer(60);

    public Level level;
    public LevelRenderer levelRenderer;
    public Player player;

    public volatile boolean levelReady = false;

    public volatile double spawnX = 128.0, spawnY = 67.0, spawnZ = 128.0;
    public volatile boolean spawnReceived = false;

    public int pendingWidth = -1;
    public int pendingHeight = -1;
    public int pendingDepth = -1;
    public byte[] pendingBlocks = null;
    public volatile boolean levelUpdatePending = false;

    public volatile String loadingText = "";
    public volatile Color loadingColor = Color.WHITE;

    private FontRenderer font;
    private Font minecraftFont;
    public Chat chat;
    public SocketClient socket;
    public Thread socketThread;
    public PlayerManager playerManager;

    private Crosshair crosshair;
    public Info info;
    public  int fps;

    private final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);

    public final int width  = 1280;
    private final int height = 720;

    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    private HitResult hitResult;

    public Minecraft(String ip, int port, String username) throws IOException {
        mc = this;
        this.username = username;
        this.socket = new SocketClient(ip, port, username);
        this.socketThread  = new Thread(socket);
        this.playerManager = new PlayerManager();
        this.level = new Level(64);
    }

    public void init() throws LWJGLException {
        fogColor.put(new float[]{14/255f, 11/255f, 10/255f, 1f}).flip();

        Display.setDisplayMode(new DisplayMode(width, height));
        Display.setTitle("rd-multiplayer " + GIT_HASH);
        Display.setVSyncEnabled(true);
        Display.create();
        Keyboard.create();
        Mouse.create();

        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearColor(0.5F, 0.8F, 1.0F, 0.0F);
        glClearDepth(1.0);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);

        try (InputStream in = FontRenderer.class.getResourceAsStream("/client/fonts/Minecraft.ttf")) {
            minecraftFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(16f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        font = new FontRenderer(minecraftFont);
        crosshair = new Crosshair(16, "/client/textures/crosshair.png");
        info = new Info(font);
        chat = new Chat(font, 50, 0, height - 150 - 16, 500, 150);

        Mouse.setGrabbed(true);
    }

    public void destroy() {
        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
        System.exit(0);
    }

    @Override
    public void run() {
        socketThread.start();

        try {
            init();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e, "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        System.out.println("Waiting for chunks from server...");
        while (!levelReady) {
            if (levelUpdatePending) {
                applyPendingLevel();
                break;
            }
            renderLoadingScreen();
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }

        if (levelRenderer == null) {
            levelRenderer = new LevelRenderer(level);
            player = new Player(level);
            level.forEachLoadedChunk((cx, cz) -> levelRenderer.chunkLoaded(cx, cz));
        }

        keepAlive();

        int  frames   = 0;
        long lastTime = System.currentTimeMillis();

        try {
            while (!Display.isCloseRequested()) {
                timer.advanceTime();
                for (int i = 0; i < timer.ticks; i++) tick();
                render(timer.partialTicks);

                frames++;
                while (System.currentTimeMillis() >= lastTime + 1000L) {
                    System.out.println(frames + " fps, " + Chunk.updates);
                    fps = frames;
                    Chunk.updates = 0;
                    lastTime += 1000L;
                    frames = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            destroy();
        }
    }
  
    private void applyPendingLevel() {
        if (!levelUpdatePending) return;
        this.level = new Level(pendingDepth);
        this.level.loadLevel(pendingWidth, pendingHeight, pendingDepth, pendingBlocks);
        this.levelRenderer = new LevelRenderer(this.level);
        this.player = new Player(this.level);
        this.levelRenderer.rebuildAll();
        levelUpdatePending = false;
        System.out.println("Level loaded from server (legacy LEVEL_DATA)!");
    }

    private void tick() throws IOException {
        info.tickKeys();
        info.tickScroll();
        int[] update;
        while ((update = SocketClient.pendingBlocks.poll()) != null) {
            if (level != null) level.setTile(update[0], update[1], update[2], update[3]);
        }
        if (player != null) player.tick();
    }

    private void moveCameraToPlayer(float pt) {
        glTranslatef(0f, 0f, -0.3f);
        glRotatef(player.xRotation, 1f, 0f, 0f);
        glRotatef(player.yRotation, 0f, 1f, 0f);
        double x = player.prevX + (player.x - player.prevX) * pt;
        double y = player.prevY + (player.y - player.prevY) * pt;
        double z = player.prevZ + (player.z - player.prevZ) * pt;
        glTranslated(-x, -y, -z);
    }

    private void setupCamera(float pt) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective(70, width / (float) height, 0.05F, 1000);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        moveCameraToPlayer(pt);
    }

    private void setupPickCamera(float pt, int x, int y) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        viewportBuffer.clear();
        glGetInteger(GL_VIEWPORT, viewportBuffer);
        viewportBuffer.flip();
        viewportBuffer.limit(16);
        gluPickMatrix(x, y, 5f, 5f, viewportBuffer);
        gluPerspective(70f, width / (float) height, 0.05f, 1000f);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        moveCameraToPlayer(pt);
    }

    private void pick(float pt) {
        selectBuffer.clear();
        glSelectBuffer(selectBuffer);
        glRenderMode(GL_SELECT);
        setupPickCamera(pt, width / 2, height / 2);
        levelRenderer.pick(player);
        selectBuffer.flip();
        selectBuffer.limit(selectBuffer.capacity());

        long closest = 0L;
        int[] names  = new int[10];
        int   hitNameCount = 0;

        int hits = glRenderMode(GL_RENDER);
        for (int hi = 0; hi < hits; hi++) {
            int  nameCount = selectBuffer.get();
            long minZ      = selectBuffer.get();
            selectBuffer.get();
            if (minZ < closest || hi == 0) {
                closest      = minZ;
                hitNameCount = nameCount;
                for (int ni = 0; ni < nameCount; ni++) names[ni] = selectBuffer.get();
            } else {
                for (int ni = 0; ni < nameCount; ni++) selectBuffer.get();
            }
        }

        hitResult = hitNameCount > 0
                ? new HitResult(names[0], names[1], names[2], names[3], names[4])
                : null;
    }

    private void render(float pt) throws IOException {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        boolean worldReady = (levelReady || !levelUpdatePending)
                && level != null && levelRenderer != null && player != null
                && level.hasAnyChunk();

        if (worldReady) {
            float motionX = Mouse.getDX();
            float motionY = Mouse.getDY();
            player.turn(motionX, motionY);

            pick(pt);

            while (Mouse.next()) {
                if (Mouse.getEventButtonState() && hitResult != null) {
                    if (Mouse.getEventButton() == 0) {
                        SocketClient.sendBlock(Packets.BLOCK_BREAK, hitResult.x, hitResult.y, hitResult.z);
                    }
                    if (Mouse.getEventButton() == 1) {
                        int x = hitResult.x, y = hitResult.y, z = hitResult.z;
                        if (hitResult.face == 0) y--;
                        if (hitResult.face == 1) y++;
                        if (hitResult.face == 2) z--;
                        if (hitResult.face == 3) z++;
                        if (hitResult.face == 4) x--;
                        if (hitResult.face == 5) x++;

                        float pMinX = (float)(player.x - player.width),  pMaxX = (float)(player.x + player.width);
                        float pMinY = (float)(player.y - player.height), pMaxY = (float)(player.y + player.height);
                        float pMinZ = (float)(player.z - player.width),  pMaxZ = (float)(player.z + player.width);
                        boolean intersects =
                                pMaxX > x && pMinX < x+1 &&
                                pMaxY > y && pMinY < y+1 &&
                                pMaxZ > z && pMinZ < z+1;
                        if (!intersects) SocketClient.sendBlock(Packets.BLOCK_PLACE, x, y, z, info.getSelectedBlockId());
                    }
                }
            }

            setupCamera(pt);

            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, -10);
            glFogf(GL_FOG_END, 20);
            glFog(GL_FOG_COLOR, fogColor);
            glDisable(GL_FOG);

            levelRenderer.render(0);
            glEnable(GL_FOG);
            levelRenderer.render(1);
            levelRenderer.renderPlayers(playerManager);
            levelRenderer.renderNameTags(playerManager, player, font);
            glDisable(GL_TEXTURE_2D);

            if (hitResult != null) levelRenderer.renderHit(hitResult);

            glDisable(GL_FOG);
            crosshair.render(width, height);
            info.render(width, height);
            chat.render(width, height);

        } else {
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        Display.update();
    }

    private void keepAlive() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    if (socket.isConnected()) SocketClient.sendKeepalive(System.currentTimeMillis());
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "KeepAliveThread");
        t.setDaemon(true);
        t.start();
    }

    private int loadingBackground = -1;

    private void renderLoadingScreen() {
        if (loadingBackground == -1) {
            loadingBackground = Textures.loadTexture("/client/textures/background.png", GL_NEAREST);
        }
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix(); glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix(); glLoadIdentity();

        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);  glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(1f, 1f, 1f, 1f);
        Textures.bind(loadingBackground);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(0,     0);
        glTexCoord2f(1, 0); glVertex2f(width, 0);
        glTexCoord2f(1, 1); glVertex2f(width, height);
        glTexCoord2f(0, 1); glVertex2f(0,     height);
        glEnd();

        int textWidth  = font.getStringWidth(loadingText);
        int textHeight = font.getStringHeight();
        int tx = (width  / 2) - (textWidth  / 2);
        int ty = (height / 2) - (textHeight / 2);
        glColor4f(1f, 1f, 1f, 1f);
        font.drawString(loadingText, tx, ty, loadingColor, true);

        glDisable(GL_BLEND); glEnable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION); glPopMatrix();
        glMatrixMode(GL_MODELVIEW);

        Display.update();
    }
  
    public PlayerManager getPlayerManager() { return playerManager; }
    public Level getLevel() { return level; }
}