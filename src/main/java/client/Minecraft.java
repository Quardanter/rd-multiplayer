package client;

import client.hud.Chat;
import client.hud.Crosshair;
import client.hud.Info;
import client.level.Chunk;
import client.level.Level;
import client.level.LevelRenderer;
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

    //for versioning and shit
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
    private FontRenderer font;
    private Font minecraftFont;
    public Chat chat;
    public SocketClient socket;
    public Thread socketThread;

    private Crosshair crosshair;
    private Info info;
    public int fps;

    private final FloatBuffer fogColor = BufferUtils.createFloatBuffer(4);

    /**
     * Screen resolution
     */
    private final int width = 1280;
    private final int height = 720;

    /**
     * Tile picking
     */
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final IntBuffer selectBuffer = BufferUtils.createIntBuffer(2000);
    private HitResult hitResult;

    public int pendingWidth = -1;
    public int pendingHeight = -1;
    public int pendingDepth = -1;
    public byte[] pendingBlocks = null;
    public boolean levelUpdatePending = false;

    private void applyPendingLevel() {
        if (!levelUpdatePending) return;
        this.level = new client.level.Level(pendingWidth, pendingHeight, pendingDepth);
        this.level.loadLevel(pendingWidth, pendingHeight, pendingDepth, pendingBlocks);
        this.levelRenderer = new client.level.LevelRenderer(this.level);
        this.player = new Player(this.level);

        levelUpdatePending = false;
        System.out.println("Level loaded from server!");
    }

    public Minecraft(String ip, int port, String username) throws IOException {
        mc = this;
        this.username = username;
        this.socket = new SocketClient(ip, port, username);
        this.socketThread = new Thread(socket);
    }

    /**
     * Initialize the game.
     * Setup display, keyboard, mouse, rendering and camera
     *
     * @throws LWJGLException Game could not be initialized
     */
    public void init() throws LWJGLException {
        // Write fog color
        this.fogColor.put(new float[]{
                14 / 255.0F,
                11 / 255.0F,
                10 / 255.0F,
                255 / 255.0F
        }).flip();

        // Display cfg
        Display.setDisplayMode(new DisplayMode(this.width, this.height));
        Display.setTitle("rd-multiplayer " + GIT_HASH);
        Display.setVSyncEnabled(true);

        // Setup I/O
        Display.create();
        Keyboard.create();
        Mouse.create();

        // Setup rendering
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



        // Grab mouse cursor
        Mouse.setGrabbed(true);
    }

    /**
     * Destroy mouse, keyboard and display
     */
    public void destroy() {
        Mouse.destroy();
        Keyboard.destroy();
        Display.destroy();
        System.exit(0);
    }

    /**
     * Main game thread
     * Responsible for the game loop
     */
    @Override
    public void run() {
        socketThread.start();

        System.out.println("Waiting for server authentication and level...");
        while (!socket.isConnected() || !levelUpdatePending) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {}
        }

        keepAlive();

        try {
            // Initialize OpenGL immediately (dummy level)
            init();
            System.out.println("Game initialized, waiting for server level...");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e, "Failed to start Minecraft", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        int frames = 0;
        long lastTime = System.currentTimeMillis();

        try {
            // Main game loop
            while (!Display.isCloseRequested()) {

                // Update the timer
                this.timer.advanceTime();

                // Tick updates (20 times per second)
                for (int i = 0; i < this.timer.ticks; ++i) {
                    tick();
                }

                // Render the game
                render(this.timer.partialTicks);

                frames++;

                // Print FPS every second
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


    /**
     * Game tick, called exactly 20 times per second
     */
    private void tick() throws IOException {
        applyPendingLevel();
        this.player.tick();
    }

    /**
     * Move and rotate the camera to players location and rotation
     *
     * @param partialTicks Overflow ticks to calculate smooth a movement
     */
    private void moveCameraToPlayer(float partialTicks) {
        Player player = this.player;

        // Eye height
        glTranslatef(0.0f, 0.0f, -0.3f);

        // Rotate camera
        glRotatef(player.xRotation, 1.0f, 0.0f, 0.0f);
        glRotatef(player.yRotation, 0.0f, 1.0f, 0.0f);

        // Smooth movement
        double x = this.player.prevX + (this.player.x - this.player.prevX) * partialTicks;
        double y = this.player.prevY + (this.player.y - this.player.prevY) * partialTicks;
        double z = this.player.prevZ + (this.player.z - this.player.prevZ) * partialTicks;

        // Move camera to players location
        glTranslated(-x, -y, -z);
    }


    /**
     * Setup the normal player camera
     *
     * @param partialTicks Overflow ticks to calculate smooth a movement
     */
    private void setupCamera(float partialTicks) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        // Set camera perspective
        gluPerspective(70, width / (float) height, 0.05F, 1000);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Move camera to middle of level
        moveCameraToPlayer(partialTicks);
    }

    /**
     * Setup tile picking camera
     *
     * @param partialTicks Overflow ticks to calculate smooth a movement
     * @param x            Screen position x
     * @param y            Screen position y
     */
    private void setupPickCamera(float partialTicks, int x, int y) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        // Reset buffer
        this.viewportBuffer.clear();

        // Get viewport value
        glGetInteger(GL_VIEWPORT, this.viewportBuffer);

        // Flip
        this.viewportBuffer.flip();
        this.viewportBuffer.limit(16);

        // Set matrix and camera perspective
        gluPickMatrix(x, y, 5.0f, 5.0f, this.viewportBuffer);
        gluPerspective(70.0f, this.width / (float) this.height, 0.05f, 1000.0f);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Move camera to middle of level
        moveCameraToPlayer(partialTicks);
    }

    /**
     * @param partialTicks Overflow ticks to calculate smooth a movement
     */
    private void pick(float partialTicks) {
        // Reset select buffer
        this.selectBuffer.clear();

        glSelectBuffer(this.selectBuffer);
        glRenderMode(GL_SELECT);

        // Setup pick camera
        this.setupPickCamera(partialTicks, this.width / 2, this.height / 2);

        // Render all possible pick selection faces to the target
        this.levelRenderer.pick(this.player);

        // Flip buffer
        this.selectBuffer.flip();
        this.selectBuffer.limit(this.selectBuffer.capacity());

        long closest = 0L;
        int[] names = new int[10];
        int hitNameCount = 0;

        // Get amount of hits
        int hits = glRenderMode(GL_RENDER);
        for (int hitIndex = 0; hitIndex < hits; hitIndex++) {

            // Get name count
            int nameCount = this.selectBuffer.get();
            long minZ = this.selectBuffer.get();
            this.selectBuffer.get();

            // Check if the hit is closer to the camera
            if (minZ < closest || hitIndex == 0) {
                closest = minZ;
                hitNameCount = nameCount;

                // Fill names
                for (int nameIndex = 0; nameIndex < nameCount; nameIndex++) {
                    names[nameIndex] = this.selectBuffer.get();
                }
            } else {
                // Skip names
                for (int nameIndex = 0; nameIndex < nameCount; ++nameIndex) {
                    this.selectBuffer.get();
                }
            }
        }

        // Update hit result
        if (hitNameCount > 0) {
            this.hitResult = new HitResult(names[0], names[1], names[2], names[3], names[4]);
        } else {
            this.hitResult = null;
        }
    }


    /**
     * Rendering the game
     *
     * @param partialTicks Overflow ticks to calculate smooth a movement
     */
    private void render(float partialTicks) throws IOException {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (!levelUpdatePending && level.getWidth() > 0 && level.getHeight() > 0 && level.getDepth() > 0) {
            // Normal game rendering
            // Get mouse motion
            float motionX = Mouse.getDX();
            float motionY = Mouse.getDY();
            this.player.turn(motionX, motionY);

            // Tile picking
            pick(partialTicks);

            // Mouse input for breaking/placing blocks
            while (Mouse.next()) {
                if (Mouse.getEventButtonState() && hitResult != null) {
                    if (Mouse.getEventButton() == 0)
                        SocketClient.sendBlock(Packets.BLOCK_BREAK, hitResult.x, hitResult.y, hitResult.z);
                    if (Mouse.getEventButton() == 1) {
                        int x = hitResult.x;
                        int y = hitResult.y;
                        int z = hitResult.z;
                        if (hitResult.face == 0) y--;
                        if (hitResult.face == 1) y++;
                        if (hitResult.face == 2) z--;
                        if (hitResult.face == 3) z++;
                        if (hitResult.face == 4) x--;
                        if (hitResult.face == 5) x++;

                        float pMinX = (float) (player.x - player.width);
                        float pMaxX = (float) (player.x + player.width);
                        float pMinY = (float) (player.y - player.height);
                        float pMaxY = (float) (player.y + player.height);
                        float pMinZ = (float) (player.z - player.width);
                        float pMaxZ = (float) (player.z + player.width);

                        float bMinX = x;
                        float bMaxX = x + 1;
                        float bMinY = y;
                        float bMaxY = y + 1;
                        float bMinZ = z;
                        float bMaxZ = z + 1;

                        boolean intersects = pMaxX > bMinX && pMinX < bMaxX && pMaxY > bMinY && pMinY < bMaxY && pMaxZ > bMinZ && pMinZ < bMaxZ;

                        if (intersects) return;

                        SocketClient.sendBlock(Packets.BLOCK_PLACE, x, y, z);
                    }

                }
            }

            // Setup camera
            setupCamera(partialTicks);

            // Render fog and level
            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, -10);
            glFogf(GL_FOG_END, 20);
            glFog(GL_FOG_COLOR, this.fogColor);
            glDisable(GL_FOG);

            levelRenderer.render(0);
            glEnable(GL_FOG);
            levelRenderer.render(1);
            glDisable(GL_TEXTURE_2D);

            if (hitResult != null)
                levelRenderer.renderHit(hitResult);

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
        Thread keepAliveThread = new Thread(() -> {
            while (true) {
                try {
                    if (socket.isConnected()) {
                        long timestamp = System.currentTimeMillis();
                        SocketClient.sendKeepalive(timestamp);
                    }
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "KeepAliveThread");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
    }

    public Level getLevel() {return level;}
}
