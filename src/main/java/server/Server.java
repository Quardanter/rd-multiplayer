package server;

import global.Packets;
import server.level.Level;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class Server {
    public static Level level;

    private static final Set<DataOutputStream> clients = ConcurrentHashMap.newKeySet();

    public static void main(String args[]) throws IOException {
        level = new Level(256, 256, 64);
        level.save();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Saving level...");
            level.save();
        }));

        ServerSocket serverSocket = new ServerSocket(9090);
        System.out.println("server started...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println(String.format("client connected from: %s", clientSocket.getInetAddress().getHostAddress()));

            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        DataInputStream in = null;
        DataOutputStream out = null;

        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            clients.add(out);

            while (true) {
                byte packetId = in.readByte();

                switch (packetId) {

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();

                        level.setTile(x, y, z, 0);
                        broadcastBlock(Packets.BLOCK_BREAK, x, y, z);
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();

                        level.setTile(x, y, z, 1);
                        broadcastBlock(Packets.BLOCK_PLACE, x, y, z);
                        break;
                    }

                    case Packets.REQUEST_LEVEL: {
                        sendLevel(out);
                        break;
                    }

                    default:
                        System.err.println("Unknown packet id: " + packetId);
                        break;
                }
            }

        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {
            if (out != null) {
                clients.remove(out);
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private static void broadcastBlock(byte type, int x, int y, int z) {
        for (DataOutputStream out : clients) {
            try {
                out.writeByte(type);
                out.writeInt(x);
                out.writeInt(y);
                out.writeInt(z);
                out.flush();
            } catch (IOException ignored) {}
        }
    }

    private static void sendLevel(DataOutputStream out) throws IOException {
        byte[] blocks = level.getBlocks(); 
        out.writeByte(Packets.LEVEL_DATA);
        out.writeInt(level.getWidth());
        out.writeInt(level.getHeight());
        out.writeInt(level.getDepth());
        out.writeInt(blocks.length);
        out.write(blocks);
        out.flush();
    }
}
