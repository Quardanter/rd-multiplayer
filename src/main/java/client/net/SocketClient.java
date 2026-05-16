package client.net;

import client.Minecraft;
import client.level.Level;
import global.Packets;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SocketClient implements Runnable {
    private final String host;
    private final int port;
    private final String username;
    private Socket socket;
    private static DataOutputStream out;
    private DataInputStream in;
    public boolean authenticated;

    public static final ConcurrentLinkedQueue<int[]> pendingBlocks = new ConcurrentLinkedQueue<>();

    private static final Object writeLock = new Object();

    public SocketClient(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    private void setLoading(String text, Color color) {
        Minecraft.mc.loadingText = text;
        Minecraft.mc.loadingColor = color;
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

            out.writeByte(Packets.AUTH_REQUEST);
            out.writeUTF(username);
            out.flush();

            setLoading("Sending authentication request...", Color.WHITE);

            byte response = in.readByte();

            setLoading("Waiting for authentication response...", Color.WHITE);

            if (response != Packets.AUTH_SUCCESS) {
                if(response == Packets.AUTH_FAILED) {

                    String reason = in.readUTF();
                    setLoading("Auth failed: " + reason, Color.RED);
                }
                System.err.println("Authentication failed!");
                socket.close();
                return;
            }

            System.out.println("Authenticated successfully as " + username);
            setLoading("Authentication successful!", Color.GREEN);
            authenticated = true;

            setLoading("Requesting level...", Color.WHITE);
            out.writeByte(Packets.REQUEST_LEVEL);
            out.flush();

            setLoading("Waiting for level data...", Color.WHITE);

            while (true) {
                byte packetId = in.readByte();

                switch (packetId) {


                    case Packets.CHUNK_DATA: {
                        int cx    = in.readInt();
                        int cz    = in.readInt();
                        int depth = in.readInt();
                        int len   = in.readInt();
                        byte[] data = new byte[len];
                        in.readFully(data);

                        Level level = Minecraft.mc.level;
                        if (level != null) {
                            level.loadChunk(cx, cz, depth, data);
                            if (!Minecraft.mc.levelReady) {
                                Minecraft.mc.levelReady = true;
                            }
                        }
                        break;
                    }

                    case Packets.CHUNK_UNLOAD: {
                        int cx = in.readInt();
                        int cz = in.readInt();
                        Level level = Minecraft.mc.level;
                        if (level != null) {
                            level.unloadChunk(cx, cz);
                        }
                        break;
                    }

                    case Packets.LEVEL_DATA: {
                        setLoading("Receiving level metadata...", Color.WHITE);

                        int w   = in.readInt();
                        int h   = in.readInt();
                        int d   = in.readInt();
                        int len = in.readInt();

                        setLoading("Downloading world (" + len + " bytes)...", Color.WHITE);

                        byte[] blocks = new byte[len];
                        in.readFully(blocks);

                        setLoading("Applying world...", Color.WHITE);

                        Minecraft.mc.pendingWidth  = w;
                        Minecraft.mc.pendingHeight = h;
                        Minecraft.mc.pendingDepth  = d;
                        Minecraft.mc.pendingBlocks = blocks;
                        Minecraft.mc.levelUpdatePending = true;

                        setLoading("Level loaded successfully!", Color.GREEN);
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        pendingBlocks.add(new int[]{x, y, z, 1});
                        break;
                    }

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        pendingBlocks.add(new int[]{x, y, z, 0});
                        break;
                    }

                    case Packets.SET_POS: {
                        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                        Minecraft.mc.spawnX = x;
                        Minecraft.mc.spawnY = y;
                        Minecraft.mc.spawnZ = z;
                        Minecraft.mc.spawnReceived = true;
                        if (Minecraft.mc.player != null) Minecraft.mc.player.forcePosition(x, y, z);
                        break;
                    }

                    case Packets.POS: {
                        String uname = in.readUTF();
                        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                        float  yaw  = in.readFloat();
                        int    ping = in.readInt();
                        Minecraft.mc.getPlayerManager().updatePlayer(uname, x, y, z, yaw, ping);
                        break;
                    }

                    case Packets.CHAT: {
                        String author  = in.readUTF();
                        String message = in.readUTF();
                        Minecraft.mc.chat.addMessage(author, message);
                        break;
                    }

                    case Packets.KEEPALIVE: {
                        long time = in.readLong();
                        Minecraft.mc.rtt = System.currentTimeMillis() - time;
                        break;
                    }

                    case Packets.CONNECTION: {
                        int    type  = in.readInt();
                        String uname = in.readUTF();
                        Minecraft.mc.chat.addConnectionMessage(uname, type);
                        if (type == 1) {
                            Minecraft.mc.getPlayerManager().removePlayer(uname);
                        } else {
                            if (Minecraft.mc.player != null) Minecraft.mc.player.sendPosition();
                        }
                        break;
                    }

                    default: {
                        System.err.println("Unknown packet: " + packetId);
                        break;
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            setLoading("Connection error: " + e.getMessage(), Color.RED);
        }
    }

    public static void sendBlock(int packet, int x, int y, int z) throws IOException {
        synchronized (writeLock) {
            out.writeByte(packet);
            out.writeInt(x); out.writeInt(y); out.writeInt(z);
            out.flush();
        }
    }

    public static void sendPos(int packet, double x, double y, double z, float yaw, int ping)
            throws IOException {
        synchronized (writeLock) {
            out.writeByte(packet);
            out.writeDouble(x); out.writeDouble(y); out.writeDouble(z);
            out.writeFloat(yaw); out.writeInt(ping);
            out.flush();
        }
    }

    public static void sendKeepalive(long timestamp) throws IOException {
        synchronized (writeLock) {
            out.writeByte(Packets.KEEPALIVE);
            out.writeLong(timestamp);
            out.flush();
        }
    }

    public static void sendChat(String author, String message) throws IOException {
        synchronized (writeLock) {
            out.writeByte(Packets.CHAT);
            out.writeUTF(author);
            out.writeUTF(message);
            out.flush();
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}