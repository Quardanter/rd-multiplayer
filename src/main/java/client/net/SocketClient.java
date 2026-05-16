package client.net;

import client.Minecraft;
import global.Packets;

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

    public static final ConcurrentLinkedQueue<int[]> pendingBlocks = new ConcurrentLinkedQueue<>();

    public SocketClient(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(new java.io.BufferedOutputStream(socket.getOutputStream(), 8192));

            out.writeByte(Packets.AUTH_REQUEST);
            out.writeUTF(username);
            out.flush();

            byte response = in.readByte();
            if (response != Packets.AUTH_SUCCESS) {
                System.err.println("Authentication failed!");
                socket.close();
                return;
            }

            System.out.println("Authenticated successfully as " + username);

            out.writeByte(Packets.REQUEST_LEVEL);
            out.flush();

            while (true) {
                byte packetId = in.readByte();

                switch (packetId) {

                    case Packets.SET_POS: {
                        double x = in.readDouble();
                        double y = in.readDouble();
                        double z = in.readDouble();
                        if (Minecraft.mc.player != null) {
                            Minecraft.mc.player.forcePosition(x, y, z);
                        }
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();
                        pendingBlocks.add(new int[]{x, y, z, 1});
                        break;
                    }

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();
                        pendingBlocks.add(new int[]{x, y, z, 0});
                        break;
                    }

                    case Packets.LEVEL_DATA: {
                        int w   = in.readInt();
                        int h   = in.readInt();
                        int d   = in.readInt();
                        int len = in.readInt();

                        byte[] blocks = new byte[len];
                        in.readFully(blocks);

                        Minecraft.mc.pendingWidth  = w;
                        Minecraft.mc.pendingHeight = h;
                        Minecraft.mc.pendingDepth  = d;
                        Minecraft.mc.pendingBlocks = blocks;
                        Minecraft.mc.levelUpdatePending = true;
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
                        int    type     = in.readInt();
                        String uname    = in.readUTF();
                        Minecraft.mc.chat.addConnectionMessage(uname, type);

                        if (type == 1) {
                            Minecraft.mc.getPlayerManager().removePlayer(uname);
                        } else {
                            Minecraft.mc.player.sendPosition();
                        }
                        break;
                    }

                    case Packets.POS: {
                        String uname = in.readUTF();
                        double x     = in.readDouble();
                        double y     = in.readDouble();
                        double z     = in.readDouble();
                        float  yaw   = in.readFloat();
                        int    ping  = in.readInt();
                        Minecraft.mc.getPlayerManager().updatePlayer(uname, x, y, z, yaw, ping);
                        break;
                    }

                    default:
                        System.err.println("Unknown packet: " + packetId);
                        break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final Object writeLock = new Object();

    public static void sendBlock(int packet, int x, int y, int z) throws IOException {
        synchronized (writeLock) {
            out.writeByte(packet);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(z);
            out.flush();
        }
    }

    public static void sendPos(int packet, double x, double y, double z, float yaw, int ping) throws IOException {
        synchronized (writeLock) {
            out.writeByte(packet);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            out.writeFloat(yaw);
            out.writeInt(ping);
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