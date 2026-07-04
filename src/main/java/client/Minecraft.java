package client;

import client.gfx.GL;
import client.gui.screen.impl.LoadingScreen;
import client.gui.screen.impl.MenuScreen;
import client.gui.screen.Screen;
import client.hud.Chat;
import client.hud.Crosshair;
import client.hud.Info;
import client.hud.PauseMenu;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

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

    public volatile boolean disconnectPending = false;

    public FontRenderer font;
    private Font minecraftFont;
    public Chat chat;
    public SocketClient socket;
    public Thread socketThread;
    public PlayerManager playerManager;
    public Screen currentScreen;

    private Crosshair crosshair;
    public Info info;
    public PauseMenu pauseMenu;
    public int fps;

    private final FloatBuffer sunPosBuf  = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer ambientBuf = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer diffuseBuf = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer zeroAmbientBuf = BufferUtils.createFloatBuffer(4);

    public int width = 1280;
    public int height = 720;
    private boolean fullscreen = false;

    public Camera camera;

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
        this.socketThread = new Thread(socket, "SocketClient");
        this.playerManager = new PlayerManager();
        this.level = new Level();

        socketThread.start();
        this.currentScreen = new LoadingScreen();
    }

    public void disconnect() {
        disconnectPending = true;
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
        if (socketThread != null) {
            socketThread.interrupt();
            socketThread = null;
        }
    }

    public void applyDisconnect() {
        disconnectPending = false;
        level = null;
        levelRenderer = null;
        localPlayer = null;
        camera = null;
        playerManager = null;
        levelReady = false;
        spawnReceived = false;
        Mouse.setGrabbed(false);
        currentScreen = new MenuScreen();
    }

    public void init() throws LWJGLException {
        Display.setDisplayMode(new DisplayMode(width, height));
        Display.setResizable(true);
        Display.setTitle("rd-multiplayer " + GIT_HASH);
        Display.setVSyncEnabled(true);
        Display.create();
        GL.viewport(0, 0, width, height);
        Keyboard.create();
        Mouse.create();

        GL.enable(GL.TEXTURE_2D);
        GL.shadeModel(GL.SMOOTH);
        GL.clearColor(0.5F, 0.8F, 1.0F, 0.0F);
        GL.clearDepth(1.0);
        GL.enable(GL.DEPTH_TEST);
        GL.enable(GL.CULL_FACE);
        GL.depthFunc(GL.LEQUAL);

        GL.lightModeli(GL.LIGHT_MODEL_LOCAL_VIEWER, 0);
        GL.lightModeli(GL.LIGHT_MODEL_TWO_SIDE, 0);

        zeroAmbientBuf.put(new float[]{0f, 0f, 0f, 1f}).flip();
        GL.lightModel(GL.LIGHT_MODEL_AMBIENT, zeroAmbientBuf);

        GL.colorMaterial(GL.FRONT, GL.AMBIENT_AND_DIFFUSE);

        try (InputStream in = FontRenderer.class.getResourceAsStream("/client/fonts/Minecraft.ttf")) {
            minecraftFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(16f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        font = new FontRenderer(minecraftFont);
        crosshair = new Crosshair(16, "/client/textures/crosshair.png");
        info = new Info(font);
        pauseMenu = new PauseMenu();
        chat = new Chat(font, 50, 0, height - 150 - 16, 500, 150);
        skyRenderer.init();

        Mouse.setGrabbed(false);
    }

    public void destroy() {
        try { client.mods.ModLoader.disposeAll(); } catch (Throwable ignored) {}
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

        client.mods.ModLoader.loadAll();

        if (currentScreen == null) currentScreen = new MenuScreen();

        while (!Display.isCloseRequested()) {
            while (!Display.isCloseRequested() && !levelReady) {
                if (disconnectPending) {
                    applyDisconnect();
                }
                if (Display.wasResized()) {
                    width = Display.getWidth();
                    height = Display.getHeight();
                    if (height <= 0) height = 1;
                    GL.viewport(0, 0, width, height);
                }
                GL.clearColor(0, 0, 0, 1);
                GL.clear(GL.COLOR_BUFFER_BIT | GL.DEPTH_BUFFER_BIT);
                if (currentScreen != null) currentScreen.render(font, width, height);
                Display.update();
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
            if (Display.isCloseRequested()) break;

            if (levelRenderer == null) {
                levelRenderer = new LevelRenderer(level);
                localPlayer = new LocalPlayer(level);
                camera = new Camera(this);
                level.forEachLoadedChunk((cx, cy, cz) -> levelRenderer.chunkLoaded(cx, cy, cz));
                currentScreen = null;
                Mouse.setGrabbed(true);
            }

            startKeepAlive(socket);

            int frames = 0;
            long lastTime = System.currentTimeMillis();
            try {
                while (!Display.isCloseRequested() && !disconnectPending) {
                    if (Display.wasResized()) {
                        width = Display.getWidth();
                        height = Display.getHeight();
                        if (height <= 0) height = 1;
                        GL.viewport(0, 0, width, height);
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
                disconnectPending = true;
            }
            if (Display.isCloseRequested()) break;

            applyDisconnect();
        }

        destroy();
    }

    boolean f2WasDown = false;
    boolean EscWasDown = false;

    private final java.util.Set<Integer> modKeysDown = new java.util.HashSet<>();

    private void tick() throws IOException {
        if (Keyboard.isKeyDown(Keyboard.KEY_F11)) {
            toggleFullscreen();
        }

        boolean escDown = Keyboard.isKeyDown(Keyboard.KEY_ESCAPE);
        if (escDown && !EscWasDown) {
            if (pauseMenu.visible) {
                pauseMenu.visible = false;
                Mouse.setGrabbed(true);
            } else if (chat.toggled) {
                chat.setToggled(false);
            } else {
                pauseMenu.visible = true;
                Mouse.setGrabbed(false);
            }
        }
        EscWasDown = escDown;

        boolean f2Down = Keyboard.isKeyDown(Keyboard.KEY_F2);
        if (f2Down && !f2WasDown) screenshot();
        f2WasDown = f2Down;

        if (pauseMenu.visible) {
            while (Keyboard.next()) { /* drop */ }
            while (Mouse.next())    { /* drop */ }
            return;
        }

        info.tickKeys();
        info.tickScroll();

        int[] update;
        SocketClient s = socket;
        if (s != null) {
            while ((update = s.pendingBlocks.poll()) != null) {
                if (level != null) level.setTile(update[0], update[1], update[2], update[3]);
            }
        }
        if (localPlayer != null && socket != null && socket.isConnected()) localPlayer.tick();
        client.mods.ModRegistry.get().dispatchTick();
        if (!chat.toggled && currentScreen == null) {
            dispatchModKeybinds();
        }
    }

    private void dispatchModKeybinds() {
        client.mods.ModRegistry reg = client.mods.ModRegistry.get();
        java.util.Set<Integer> nowDown = new java.util.HashSet<>();
        for (Integer key : modKeybindKeys()) {
            if (Keyboard.isKeyDown(key)) {
                nowDown.add(key);
                if (!modKeysDown.contains(key)) reg.dispatchKeyPress(key);
            }
        }
        modKeysDown.clear();
        modKeysDown.addAll(nowDown);
    }

    private static java.util.Set<Integer> modKeybindKeys() {
        return client.mods.ModRegistry.get().keybindKeys();
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
            GL.viewport(0, 0, width, height);
        } catch (LWJGLException e) {
            e.printStackTrace();
        }
    }

    private void pick(float pt) {
        hitResult = levelRenderer.pick(localPlayer);
    }

    private void render(float pt) throws IOException {
        float[] skyRgb  = WorldTime.skyColor();
        float[] sun     = WorldTime.sunDirection();
        float[] ambient = WorldTime.ambientLight();
        float[] diffuse = WorldTime.diffuseLight();

        GL.clearColor(skyRgb[0], skyRgb[1], skyRgb[2], 1f);
        GL.clear(GL.COLOR_BUFFER_BIT | GL.DEPTH_BUFFER_BIT);

        boolean worldReady = levelReady
                && level != null && levelRenderer != null && localPlayer != null
                && level.hasAnyChunk();

        if (worldReady && currentScreen == null && localPlayer != null && levelRenderer != null) {
            pick(pt);

            while (Mouse.next()) {
                if (pauseMenu.visible) continue;

                if (Mouse.isGrabbed() && !chat.toggled) {
                    localPlayer.turn(Mouse.getEventDX(), Mouse.getEventDY());
                }

                if (Mouse.getEventButtonState() && hitResult != null && !chat.toggled) {
                    if (Mouse.getEventButton() == 0) {
                        SocketClient.sendBlock(Packets.BLOCK_BREAK, hitResult.x, hitResult.y, hitResult.z);
                        client.mods.ModRegistry.get().fire(
                            new client.mods.ModEvents.BlockBreak(hitResult.x, hitResult.y, hitResult.z));
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
                        if (!intersects) {
                            int bid = info.getSelectedBlockId();
                            SocketClient.sendBlock(Packets.BLOCK_PLACE, x, y, z, bid);
                            client.mods.ModRegistry.get().fire(new client.mods.ModEvents.BlockPlace(x, y, z, bid));
                        }
                    }
                }
            }

            camera.setup(pt);

            skyRenderer.render();

            float gamma = Settings.gammaMultiplier();

            sunPosBuf.clear();
            sunPosBuf.put(sun[0]).put(sun[1]).put(sun[2]).put(0f).flip();
            GL.light(GL.LIGHT0, GL.POSITION, sunPosBuf);

            ambientBuf.clear();
            ambientBuf.put(ambient[0] * gamma).put(ambient[1] * gamma).put(ambient[2] * gamma).put(1f).flip();
            GL.light(GL.LIGHT0, GL.AMBIENT, ambientBuf);

            diffuseBuf.clear();
            diffuseBuf.put(diffuse[0] * gamma).put(diffuse[1] * gamma).put(diffuse[2] * gamma).put(1f).flip();
            GL.light(GL.LIGHT0, GL.DIFFUSE, diffuseBuf);

            GL.enable(GL.LIGHTING);
            GL.enable(GL.LIGHT0);
            GL.enable(GL.COLOR_MATERIAL);

            levelRenderer.render(0);
            levelRenderer.render(1);
            levelRenderer.renderTntOverlay();

            GL.disable(GL.COLOR_MATERIAL);
            GL.disable(GL.LIGHT0);
            GL.disable(GL.LIGHTING);
            GL.color4f(1f, 1f, 1f, 1f);

            levelRenderer.renderPlayers(playerManager);
            if (camera.mode != Camera.FIRST) {
                levelRenderer.renderSelf(localPlayer, playerManager);
            }
            levelRenderer.renderNameTags(playerManager, localPlayer, font);
            GL.disable(GL.TEXTURE_2D);

            if (hitResult != null) levelRenderer.renderHit(hitResult);

            crosshair.render(width, height);
            info.render(width, height);
            pauseMenu.render(font, width, height);

            renderModHud();

            chat.render(width, height);
        } else if (currentScreen != null) {
            currentScreen.render(font, width, height);
        }

        Display.update();
    }

    private void renderModHud() {
        GL.matrixMode(GL.PROJECTION);
        GL.pushMatrix();
        GL.loadIdentity();
        GL.ortho(0, width, height, 0, -1, 1);
        GL.matrixMode(GL.MODELVIEW);
        GL.pushMatrix();
        GL.loadIdentity();

        GL.disable(GL.DEPTH_TEST);
        GL.disable(GL.TEXTURE_2D);
        GL.enable(GL.BLEND);
        GL.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA);

        try {
            client.mods.ModRegistry.get().dispatchHud(width, height);
        } finally {
            GL.color4f(1f, 1f, 1f, 1f);
            GL.disable(GL.BLEND);
            GL.enable(GL.DEPTH_TEST);

            GL.matrixMode(GL.MODELVIEW);
            GL.popMatrix();
            GL.matrixMode(GL.PROJECTION);
            GL.popMatrix();
            GL.matrixMode(GL.MODELVIEW);
        }
    }

    //TODO: prob put this somewhere else
    private void screenshot() {
        try {
            ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
            GL.readPixels(0, 0, width, height, GL.RGBA, GL.UNSIGNED_BYTE, buf);
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

    private void keepAlive(final SocketClient session) {
        if (session == null) return;
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    if (Minecraft.mc.socket != session) return;
                    if (!session.isConnected() || !session.authenticated) return;
                    SocketClient.sendKeepalive(System.currentTimeMillis());
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }, "KeepAliveThread");
        t.setDaemon(true);
        t.start();
    }

    private void startKeepAlive(SocketClient session) {
        keepAlive(session);
    }

    public void setScreen(Screen screen) {this.currentScreen = screen;}
    public PlayerManager getPlayerManager() { return playerManager; }
    public Level getLevel() { return level; }
}
