package client;

import client.gui.screen.impl.LoadingScreen;
import client.gui.screen.impl.MenuScreen;
import client.gui.screen.Screen;
import client.hud.Chat;
import client.hud.Crosshair;
import client.hud.Info;
import client.level.Chunk;
import client.level.Level;
import client.level.LevelRenderer;
import client.level.SkyRenderer;
import client.player.local.Camera;
import client.player.local.LocalPlayer;
import client.player.remote.PlayerManager;
import client.world.WorldTime;
import global.Packets;
import client.net.SocketClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import static org.lwjgl.opengl.GL11.*;

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
    public LocalPlayer localPlayer;

    public volatile boolean levelReady = false;

    public volatile double spawnX = 128.0, spawnY = 67.0, spawnZ = 128.0;
    public volatile boolean spawnReceived = false;

    public int pendingWidth = -1;
    public int pendingHeight = -1;
    public int pendingDepth = -1;
    public byte[] pendingBlocks = null;
    public volatile boolean levelUpdatePending = false;

    private FontRenderer font;
    private Font minecraftFont;
    public Chat chat;
    public SocketClient socket;
    public Thread socketThread;
    public PlayerManager playerManager;
    public Screen currentScreen;

    private Crosshair crosshair;
    public Info info;
    public int fps;

    private final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer sunPosBuf  = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer ambientBuf = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer diffuseBuf = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer zeroAmbientBuf = BufferUtils.createFloatBuffer(4);

    public int width = 1280;
    public int height = 720;
    private boolean fullscreen = false;

    public Camera camera;

    private final IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    private HitResult hitResult;

    private final SkyRenderer skyRenderer = new SkyRenderer();

    public static void main(String[] args) throws Exception {
        extractNativesIfJar();
        new Thread(new Minecraft()).start();
    }

    private static void extractNativesIfJar() throws Exception {
        URL location = Minecraft.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null || !location.getFile().endsWith(".jar")) return;

        Path nativesDir = Files.createTempDirectory("lwjgl-natives-");
        nativesDir.toFile().deleteOnExit();

        String[] libs = getNativeLibs();
        for (String lib : libs) {
            try (InputStream in = Minecraft.class.getResourceAsStream("/natives/" + lib)) {
                if (in != null) {
                    Files.copy(in, nativesDir.resolve(lib), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        System.setProperty("org.lwjgl.librarypath", nativesDir.toAbsolutePath().toString());
    }

    private static String[] getNativeLibs() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win"))  return new String[]{"lwjgl.dll", "lwjgl64.dll", "OpenAL32.dll", "OpenAL64.dll"};
        if (os.contains("mac"))  return new String[]{"liblwjgl.jnilib", "openal.dylib"};
        return new String[]{"liblwjgl.so", "liblwjgl64.so", "libopenal.so", "libopenal64.so"};
    }

    public Minecraft() throws IOException {
        mc = this;
    }

    public void connect(String ip, int port, String username) {
        this.username = username;
        this.socket = new SocketClient(ip, port, username);
        this.socketThread = new Thread(socket);
        this.playerManager = new PlayerManager();
        this.level = new Level(64);

        socketThread.start();
        this.currentScreen = new LoadingScreen();
    }

    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
        if (socketThread != null) {
            socketThread.interrupt();
            socketThread = null;
        }

        level = null;
        levelRenderer = null;
        localPlayer = null;
        camera = null;
        playerManager = null;
        levelReady = false;
        levelUpdatePending = false;

        glClearColor(0, 0, 0, 1);
        Mouse.setGrabbed(false);
        currentScreen = new MenuScreen();
    }

    public void init() throws LWJGLException {
        Display.setDisplayMode(new DisplayMode(width, height));
        Display.setResizable(true);
        Display.setTitle("rd-multiplayer " + GIT_HASH);
        Display.setVSyncEnabled(true);
        Display.create();
        glViewport(0, 0, width, height);
        Keyboard.create();
        Mouse.create();

        glEnable(GL_TEXTURE_2D);
        glShadeModel(GL_SMOOTH);
        glClearColor(0.5F, 0.8F, 1.0F, 0.0F);
        glClearDepth(1.0);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LEQUAL);

        glLightModeli(GL_LIGHT_MODEL_LOCAL_VIEWER, 0);
        glLightModeli(GL_LIGHT_MODEL_TWO_SIDE, 0);

        zeroAmbientBuf.put(new float[]{0f, 0f, 0f, 1f}).flip();
        glLightModel(GL_LIGHT_MODEL_AMBIENT, zeroAmbientBuf);

        glColorMaterial(GL_FRONT, GL_AMBIENT_AND_DIFFUSE);

        try (InputStream in = FontRenderer.class.getResourceAsStream("/client/fonts/Minecraft.ttf")) {
            minecraftFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(16f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        font = new FontRenderer(minecraftFont);
        crosshair = new Crosshair(16, "/client/textures/crosshair.png");
        info = new Info(font);
        chat = new Chat(font, 50, 0, height - 150 - 16, 500, 150);
        skyRenderer.init();

        Mouse.setGrabbed(false);
    }

    public void destroy() {
        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
        System.exit(0);
    }

    boolean lastGrabbed = false;
    @Override
    public void run() {
        try {
            init();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e, "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        if(!(currentScreen instanceof LoadingScreen) && !levelReady) {
            currentScreen = new MenuScreen();
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            currentScreen.render(font, width, height);
            Display.update();
        }

        while (!levelReady) {
            if (Display.isCloseRequested()) destroy();
            if (levelUpdatePending) {
                applyPendingLevel();
                break;
            }
            if (Display.wasResized()) {
                width = Display.getWidth();
                height = Display.getHeight();
                if (height <= 0) height = 1;
                glViewport(0, 0, width, height);
            }
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            currentScreen.render(font, width, height);
            Display.update();
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }

        if (levelRenderer == null) {
            levelRenderer = new LevelRenderer(level);
            localPlayer = new LocalPlayer(level);
            camera = new Camera(this);
            level.forEachLoadedChunk((cx, cz) -> levelRenderer.chunkLoaded(cx, cz));
            currentScreen = null;

            Mouse.setGrabbed(true);
        }

        keepAlive();

        int frames = 0;
        long lastTime = System.currentTimeMillis();

        try {
            while (!Display.isCloseRequested()) {
                if (socket == null || !socket.isConnected()) {
                    disconnect();
                    runMenuLoop();
                    return;
                }

                if (Display.wasResized()) {
                    width = Display.getWidth();
                    height = Display.getHeight();
                    if (height <= 0) height = 1;
                    glViewport(0, 0, width, height);
                }

                timer.advanceTime();
                for (int i = 0; i < timer.ticks; i++) tick();
                render(timer.partialTicks);

                boolean shouldGrab = !chat.toggled;
                if (shouldGrab != lastGrabbed) {
                    Mouse.setGrabbed(shouldGrab);
                    if (shouldGrab) while (Mouse.next()) {}
                    lastGrabbed = shouldGrab;
                }

                frames++;
                while (System.currentTimeMillis() >= lastTime + 1000L) {
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

    private void runMenuLoop() {
        while (!Display.isCloseRequested()) {
            if (Display.wasResized()) {
                width = Display.getWidth();
                height = Display.getHeight();
                if (height <= 0) height = 1;
                glViewport(0, 0, width, height);
            }
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            currentScreen.render(font, width, height);
            Display.update();
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }
        destroy();
    }

    private void applyPendingLevel() {
        if (!levelUpdatePending) return;

        this.level = new Level(pendingDepth);
        this.level.loadLevel(pendingWidth, pendingHeight, pendingDepth, pendingBlocks);
        this.levelRenderer = new LevelRenderer(this.level);
        this.localPlayer = new LocalPlayer(this.level);
        this.camera = new Camera(this);
        this.levelRenderer.rebuildAll();

        levelUpdatePending = false;
        currentScreen = null;
    }

    boolean f2WasDown = false;

    private void tick() throws IOException {
        info.tickKeys();
        info.tickScroll();

        if (Keyboard.isKeyDown(Keyboard.KEY_F11)) {
            toggleFullscreen();
        }

        boolean f2Down = Keyboard.isKeyDown(Keyboard.KEY_F2);
        if (f2Down && !f2WasDown) screenshot();
        f2WasDown = f2Down;

        int[] update;
        while ((update = SocketClient.pendingBlocks.poll()) != null) {
            if (level != null) level.setTile(update[0], update[1], update[2], update[3]);
        }
        if (localPlayer != null) localPlayer.tick();
    }

    private void toggleFullscreen() {
        try {
            fullscreen = !fullscreen;
            if (fullscreen) {
                Display.setDisplayModeAndFullscreen(Display.getDesktopDisplayMode());
            } else {
                Display.setFullscreen(false);
                Display.setDisplayMode(new DisplayMode(1280, 720));
            }
            width = Display.getWidth();
            height = Display.getHeight();
            glViewport(0, 0, width, height);
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
    }

    private void pick(float pt) {
        selectBuffer.clear();
        glSelectBuffer(selectBuffer);
        glRenderMode(GL_SELECT);
        camera.setupPick(pt, width / 2, height / 2);
        levelRenderer.pick(localPlayer);
        selectBuffer.flip();
        selectBuffer.limit(selectBuffer.capacity());

        long closest = 0L;
        int[] names = new int[10];
        int hitNameCount = 0;

        int hits = glRenderMode(GL_RENDER);
        for (int hi = 0; hi < hits; hi++) {
            int nameCount = selectBuffer.get();
            long minZ = selectBuffer.get();
            selectBuffer.get();
            if (minZ < closest || hi == 0) {
                closest = minZ;
                hitNameCount = nameCount;
                for (int ni = 0; ni < nameCount; ni++) names[ni] = selectBuffer.get();
            } else {
                for (int ni = 0; ni < nameCount; ni++) selectBuffer.get();
            }
        }

        hitResult = hitNameCount > 0 ? new HitResult(names[0], names[1], names[2], names[3], names[4]) : null;
    }

    private void render(float pt) throws IOException {
        float[] skyRgb  = WorldTime.skyColor();
        float[] fogRgb  = WorldTime.fogColor();
        float[] sun     = WorldTime.sunDirection();
        float[] ambient = WorldTime.ambientLight();
        float[] diffuse = WorldTime.diffuseLight();

        glClearColor(skyRgb[0], skyRgb[1], skyRgb[2], 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        fogColor.clear();
        fogColor.put(fogRgb[0]).put(fogRgb[1]).put(fogRgb[2]).put(1f).flip();

        boolean worldReady = (levelReady || !levelUpdatePending)
                && level != null && levelRenderer != null && localPlayer != null
                && level.hasAnyChunk();

        if (worldReady && currentScreen == null) {
            pick(pt);

            while (Mouse.next()) {
                if(Mouse.isGrabbed()) {
                    localPlayer.turn(Mouse.getEventDX(), Mouse.getEventDY());
                }

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

                        float pMinX = (float)(localPlayer.x - localPlayer.width), pMaxX = (float)(localPlayer.x + localPlayer.width);
                        float pMinY = (float)(localPlayer.y - localPlayer.height), pMaxY = (float)(localPlayer.y + localPlayer.height);
                        float pMinZ = (float)(localPlayer.z - localPlayer.width), pMaxZ = (float)(localPlayer.z + localPlayer.width);
                        boolean intersects =
                                pMaxX > x && pMinX < x+1 &&
                                        pMaxY > y && pMinY < y+1 &&
                                        pMaxZ > z && pMinZ < z+1;
                        if (!intersects) SocketClient.sendBlock(Packets.BLOCK_PLACE, x, y, z, info.getSelectedBlockId());
                    }
                }
            }

            camera.setup(pt);

            skyRenderer.render();

            sunPosBuf.clear();
            sunPosBuf.put(sun[0]).put(sun[1]).put(sun[2]).put(0f).flip();
            glLight(GL_LIGHT0, GL_POSITION, sunPosBuf);

            ambientBuf.clear();
            ambientBuf.put(ambient[0]).put(ambient[1]).put(ambient[2]).put(1f).flip();
            glLight(GL_LIGHT0, GL_AMBIENT, ambientBuf);

            diffuseBuf.clear();
            diffuseBuf.put(diffuse[0]).put(diffuse[1]).put(diffuse[2]).put(1f).flip();
            glLight(GL_LIGHT0, GL_DIFFUSE, diffuseBuf);

            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, 60);
            glFogf(GL_FOG_END, 120);
            glFog(GL_FOG_COLOR, fogColor);
            glDisable(GL_FOG);

            glEnable(GL_LIGHTING);
            glEnable(GL_LIGHT0);
            glEnable(GL_COLOR_MATERIAL);

            levelRenderer.render(0);
            glEnable(GL_FOG);
            levelRenderer.render(1);

            glDisable(GL_COLOR_MATERIAL);
            glDisable(GL_LIGHT0);
            glDisable(GL_LIGHTING);
            glColor4f(1f, 1f, 1f, 1f);

            levelRenderer.renderPlayers(playerManager);
            if (camera.mode != Camera.FIRST) {
                levelRenderer.renderSelf(localPlayer, playerManager);
            }
            levelRenderer.renderNameTags(playerManager, localPlayer, font);
            glDisable(GL_TEXTURE_2D);

            if (hitResult != null) levelRenderer.renderHit(hitResult);

            glDisable(GL_FOG);
            crosshair.render(width, height);
            info.render(width, height);
            chat.render(width, height);
        } else if (currentScreen != null) {
            currentScreen.render(font, width, height);
        }

        Display.update();
    }

    //TODO: prob put this somewhere else
    private void screenshot() {
        try {
            ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = buf.get() & 0xFF;
                    int g = buf.get() & 0xFF;
                    int b = buf.get() & 0xFF;
                    int a = buf.get() & 0xFF;
                    img.setRGB(x, height - 1 - y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            new File("screenshots").mkdirs();
            String name = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            File out = new File("screenshots/" + name + ".png");
            ImageIO.write(img, "PNG", out);

            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.imageFlavor};
                }
                public boolean isDataFlavorSupported(DataFlavor f) {
                    return f.equals(DataFlavor.imageFlavor);
                }
                public Object getTransferData(DataFlavor f) {
                    return img;
                }
            }, null);

            chat.addMessage("Screenshot", " saved and copied!", true);
        } catch (Exception e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    private void keepAlive() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    if (socket.isConnected()) SocketClient.sendKeepalive(System.currentTimeMillis());
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "KeepAliveThread");
        t.setDaemon(true);
        t.start();
    }

    public void setScreen(Screen screen) {this.currentScreen = screen;}
    public PlayerManager getPlayerManager() { return playerManager; }
    public Level getLevel() { return level; }
}