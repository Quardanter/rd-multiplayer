package client.net;

import client.Minecraft;
import client.gui.screen.impl.LoadingScreen;
import client.level.Level;
import global.Packets;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SocketClient implements Runnable {
    private final String host;
    private final int port;
    private final String username;
    /** Identifier used in the .auth file ("host:port"). */
    private final String serverId;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    public volatile boolean authenticated;
    private volatile boolean running = true;
    public final ConcurrentLinkedQueue<int[]> pendingBlocks = new ConcurrentLinkedQueue<>();
    private final Object writeLock = new Object();

    public SocketClient(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.serverId = host + ":" + port;
    }

    private void setLoading(String text, Color color) {
        if(Minecraft.mc.currentScreen instanceof LoadingScreen) {
            ((LoadingScreen) Minecraft.mc.currentScreen).loadingText = text;
            ((LoadingScreen) Minecraft.mc.currentScreen).loadingColor = color;
        }
    }

    @Override
    public void run() {
        try {
            setLoading("Connecting to server...", Color.WHITE);
            socket = new Socket(host, port);
            setLoading("Connected to server!", Color.WHITE);

            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 8192));

            setLoading("Creating network streams...", Color.WHITE);

            String storedToken = AuthStore.getToken(serverId, username);
            if (storedToken == null) storedToken = "";

            synchronized (writeLock) {
                out.writeByte(Packets.AUTH_REQUEST);
                out.writeUTF(username);
                out.writeUTF(storedToken);
                out.flush();
            }

            setLoading("Sending authentication request...", Color.WHITE);

            byte response = in.readByte();
            setLoading("Waiting for authentication response...", Color.WHITE);

            if (response != Packets.AUTH_SUCCESS) {
                if (response == Packets.AUTH_FAILED) {
                    String reason = in.readUTF();
                    setLoading("Auth failed: " + reason, Color.RED);
                }
                System.err.println("Authentication failed!");
                closeSocketQuietly();
                return;
            }

            System.out.println("Authenticated successfully as " + username);
            setLoading("Authentication successful!", Color.GREEN);
            authenticated = true;

            client.mods.ModRegistry.get().fire(
                new client.mods.ModEvents.ServerConnected(host, port));

            uploadSkinIfPresent();

            sendRenderDistance(client.Settings.getRenderDistance());

            setLoading("Requesting level...", Color.WHITE);
            synchronized (writeLock) {
                out.writeByte(Packets.REQUEST_LEVEL);
                out.flush();
            }

            setLoading("Waiting for chunks...", Color.WHITE);

            while (running) {
                byte packetId = in.readByte();
                if (!running) break;

                Minecraft mc = Minecraft.mc;
                if (mc == null) break;

                switch (packetId) {

                    case Packets.AUTH_TOKEN: {
                        String newToken = in.readUTF();
                        AuthStore.saveToken(serverId, username, newToken);
                        System.out.println("Saved new auth token for " + username + " on " + serverId);
                        break;
                    }

                    case Packets.CHUNK_DATA: {
                        int cx = in.readInt();
                        int cy = in.readInt();
                        int cz = in.readInt();
                        byte[] data = new byte[16 * 16 * 16];
                        in.readFully(data);
                        Level level = mc.level;
                        if (level != null) {
                            level.loadChunk(cx, cy, cz, data);
                            if (!mc.levelReady) mc.levelReady = true;
                        }
                        break;
                    }

                    case Packets.CHUNK_UNLOAD: {
                        int cx = in.readInt();
                        int cy = in.readInt();
                        int cz = in.readInt();
                        Level level = mc.level;
                        if (level != null) level.unloadChunk(cx, cy, cz);
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        int id = in.readByte() & 0xFF;
                        pendingBlocks.add(new int[]{x, y, z, id});
                        break;
                    }

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        pendingBlocks.add(new int[]{x, y, z, 0});
                        break;
                    }

                    case Packets.SET_POS: {
                        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                        mc.spawnX = x;
                        mc.spawnY = y;
                        mc.spawnZ = z;
                        mc.spawnReceived = true;
                        if (mc.localPlayer != null) mc.localPlayer.forcePosition(x, y, z);
                        break;
                    }

                    case Packets.POS: {
                        String uname = in.readUTF();
                        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                        float yaw = in.readFloat();
                        float pitch = in.readFloat();
                        client.player.remote.PlayerManager pm = mc.getPlayerManager();
                        if (pm != null) pm.updatePlayer(uname, x, y, z, yaw, pitch);
                        break;
                    }

                    case Packets.PING_INFO: {
                        String uname = in.readUTF();
                        int pingMs = in.readInt();
                        client.player.remote.PlayerManager pm = mc.getPlayerManager();
                        if (pm != null) pm.updatePing(uname, pingMs);
                        break;
                    }

                    case Packets.CHAT: {
                        String author  = in.readUTF();
                        String message = in.readUTF();
                        if (mc.chat != null) mc.chat.addMessage(author, message);
                        client.mods.ModRegistry.get().fire(
                            new client.mods.ModEvents.ChatReceived(author, message));
                        break;
                    }

                    case Packets.KEEPALIVE: {
                        long time = in.readLong();
                        boolean isResponse = in.readBoolean();
                        if (isResponse) {
                            mc.rtt = System.currentTimeMillis() - time;
                        } else {
                            sendKeepaliveResponse(time);
                        }
                        break;
                    }

                    case Packets.CONNECTION: {
                        int type  = in.readInt();
                        String uname = in.readUTF();
                        if (mc.chat != null) mc.chat.addConnectionMessage(uname, type);
                        if (type == 1) {
                            client.player.remote.PlayerManager pm = mc.getPlayerManager();
                            if (pm != null) pm.removePlayer(uname);
                        } else {
                            if (mc.localPlayer != null) mc.localPlayer.sendPosition();
                        }
                        client.mods.ModRegistry.get().fire(
                            new client.mods.ModEvents.PlayerConnection(type, uname));
                        break;
                    }

                    case Packets.SKIN_DATA: {
                        String uname = in.readUTF();
                        int len = in.readInt();
                        byte[] png = new byte[len];
                        in.readFully(png);
                        client.player.remote.PlayerManager pm = mc.getPlayerManager();
                        if (pm != null) pm.setPendingSkin(uname, png);
                        break;
                    }

                    case Packets.TIME_OF_DAY: {
                        float fraction = in.readFloat();
                        long  cycleLen = in.readLong();
                        client.world.WorldTime.syncFromServer(fraction, cycleLen);
                        break;
                    }

                    default:
                        throw new IOException("Unknown packet id: " + packetId + " stream desynced, closing connection");
                }
            }

        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
                setLoading("Connection error: " + e.getMessage(), Color.RED);
                if(!(Minecraft.mc.currentScreen instanceof LoadingScreen)) {
                    Minecraft.mc.disconnectPending = true;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            if (Minecraft.mc != null) Minecraft.mc.disconnectPending = true;
        } finally {
            boolean wasAuthed = authenticated;
            authenticated = false;
            closeSocketQuietly();
            if (wasAuthed) {
                try {
                    client.mods.ModRegistry.get().fire(new client.mods.ModEvents.ServerDisconnected());
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void closeSocketQuietly() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try {
            synchronized (writeLock) {
                if (out != null) out.close();
            }
        } catch (IOException ignored) {}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    private static SocketClient current() {
        Minecraft mc = Minecraft.mc;
        if (mc == null) return null;
        SocketClient s = mc.socket;
        if (s == null || !s.authenticated || s.out == null) return null;
        return s;
    }

    public static void sendBlock(int packet, int x, int y, int z, int blockId) throws IOException {
        SocketClient s = current();
        if (s == null) return;
        synchronized (s.writeLock) {
            s.out.writeByte(packet);
            s.out.writeInt(x); s.out.writeInt(y); s.out.writeInt(z);
            if (packet == Packets.BLOCK_PLACE) s.out.writeByte(blockId);
            s.out.flush();
        }
    }

    public static void sendBlock(int packet, int x, int y, int z) throws IOException {
        sendBlock(packet, x, y, z, 0);
    }

    public static void sendPos(int packet, double x, double y, double z, float yaw, float pitch, int ping)
            throws IOException {
        SocketClient s = current();
        if (s == null) return;
        synchronized (s.writeLock) {
            s.out.writeByte(packet);
            s.out.writeDouble(x); s.out.writeDouble(y); s.out.writeDouble(z);
            s.out.writeFloat(yaw); s.out.writeFloat(pitch); s.out.writeInt(ping);
            s.out.flush();
        }
    }

    public static void sendKeepalive(long timestamp) throws IOException {
        SocketClient s = current();
        if (s == null) return;
        synchronized (s.writeLock) {
            s.out.writeByte(Packets.KEEPALIVE);
            s.out.writeLong(timestamp);
            s.out.writeBoolean(false);
            s.out.flush();
        }
    }

    public static void sendRenderDistance(int chunks) {
        SocketClient s = current();
        if (s == null) return;
        try {
            synchronized (s.writeLock) {
                s.out.writeByte(Packets.CLIENT_RENDER_DISTANCE);
                s.out.writeInt(chunks);
                s.out.flush();
            }
        } catch (IOException e) {
            System.err.println("sendRenderDistance failed: " + e.getMessage());
        }
    }

    public static void sendKeepaliveResponse(long serverTimestamp) throws IOException {
        SocketClient s = current();
        if (s == null) return;
        synchronized (s.writeLock) {
            s.out.writeByte(Packets.KEEPALIVE);
            s.out.writeLong(serverTimestamp);
            s.out.writeBoolean(true);
            s.out.flush();
        }
    }

    public static void sendChat(String author, String message) throws IOException {
        SocketClient s = current();
        if (s == null) return;
        synchronized (s.writeLock) {
            s.out.writeByte(Packets.CHAT);
            s.out.writeUTF(author);
            s.out.writeUTF(message);
            s.out.flush();
        }
    }

    public static void sendSkin(byte[] png) throws IOException {
        SocketClient s = current();
        if (s == null) return;
        synchronized (s.writeLock) {
            s.out.writeByte(Packets.SKIN_UPLOAD);
            s.out.writeInt(png.length);
            s.out.write(png);
            s.out.flush();
        }
    }

    public void disconnect() {
        running = false;
        authenticated = false;
        closeSocketQuietly();
    }

    private void uploadSkinIfPresent() {
        Path p = Paths.get("skins/skin.png");
        if (!Files.exists(p)) return;
        try {
            byte[] png = Files.readAllBytes(p);
            sendSkin(png);
            System.out.println("Uploaded skin from " + p + " (" + png.length + " bytes)");
        } catch (IOException e) {
            System.err.println("Failed to read/upload rd-skin.png: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}