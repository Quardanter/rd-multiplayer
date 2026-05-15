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

    private static final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<Client, Long> lastKeepAlive = new ConcurrentHashMap<>();

    public static void main(String args[]) throws IOException {
        level = new Level(256, 256, 64);
        level.save();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Saving level...");
            level.save();
        }));

        ServerSocket serverSocket = new ServerSocket(9090);
        System.out.println("Server started on port 9090...");

        handleKeepalive();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println(String.format("client connected from: %s", clientSocket.getInetAddress().getHostAddress()));

            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        DataInputStream in = null;
        DataOutputStream out = null;
        Client client = null;

        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(new java.io.BufferedOutputStream(socket.getOutputStream(), 8192));

            boolean authenticated = false;
            while (!authenticated) {
                byte packetId = in.readByte();
                if (packetId == Packets.AUTH_REQUEST) {
                    String username = in.readUTF().trim();

                    if (username.length() < 3 || username.length() > 15) {
                        out.writeByte(Packets.AUTH_FAILED);
                        out.flush();
                        socket.close();
                        return;
                    }

                    boolean exists = clients.stream()
                            .anyMatch(c -> c.getUsername().equalsIgnoreCase(username));
                    if (exists) {
                        out.writeByte(Packets.AUTH_FAILED);
                        out.flush();
                        socket.close();
                        return;
                    }

                    out.writeByte(Packets.AUTH_SUCCESS);
                    out.flush();

                    client = new Client(username, socket, out);
                    clients.add(client);

                    System.out.println("client authenticated: " + username);
                    broadcastConnection(0, client);

                    authenticated = true;
                } else {
                    out.writeByte(Packets.AUTH_FAILED);
                    out.flush();
                    socket.close();
                    return;
                }
            }

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

                    case Packets.KEEPALIVE: {
                        lastKeepAlive.put(client, System.currentTimeMillis());
                        long clientTime = in.readLong();
                        client.send(o -> {
                            o.writeByte(Packets.KEEPALIVE);
                            o.writeLong(clientTime);
                        });
                        break;
                    }

                    case Packets.REQUEST_LEVEL: {
                        client.send(o -> sendLevel(o));
                        break;
                    }

                    case Packets.CHAT: {
                        String author = in.readUTF();
                        String message = in.readUTF();
                        broadcastChat(author, message);
                        break;
                    }

                    case Packets.POS: {
                        double x = in.readDouble();
                        double y = in.readDouble();
                        double z = in.readDouble();
                        float yaw = in.readFloat();
                        broadcastPos(client, x, y, z, yaw);
                        break;
                    }

                    default:
                        System.err.println("unknown packet id: " + packetId);
                        break;
                }
            }

        } catch (IOException e) {
            if (client != null) {
                broadcastConnection(1, client);
            }
            System.out.println("client disconnected: " + (client != null ? client.getUsername() : "unknown"));
        } finally {
            if (client != null) {
                clients.remove(client);
                lastKeepAlive.remove(client);
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private static void handleKeepalive() {
        Thread timeoutThread = new Thread(() -> {
            final long TIMEOUT_MS = 10000;
            while (true) {
                long now = System.currentTimeMillis();
                for (Client client : clients) {
                    Long last = lastKeepAlive.get(client);
                    if (last == null) continue;
                    if (now - last > TIMEOUT_MS) {
                        System.out.println("Client timed out: " + client.getUsername());
                        broadcastConnection(1, client);
                        try {
                            client.getSocket().close();
                        } catch (IOException ignored) {}
                        clients.remove(client);
                        lastKeepAlive.remove(client);
                    }
                }
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException ignored) {}
            }
        }, "ClientTimeoutChecker");

        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    private static void broadcastBlock(byte type, int x, int y, int z) {
        for (Client client : clients) {
            client.send(o -> {
                o.writeByte(type);
                o.writeInt(x);
                o.writeInt(y);
                o.writeInt(z);
            });
        }
    }

    private static void broadcastConnection(int type, Client _client) {
        for (Client client : clients) {
            if (client == _client) continue;
            client.send(o -> {
                o.writeByte(Packets.CONNECTION);
                o.writeInt(type);
                o.writeUTF(_client.getUsername());
            });
        }
    }

    private static void broadcastChat(String author, String message) {
        for (Client client : clients) {
            client.send(o -> {
                o.writeByte(Packets.CHAT);
                o.writeUTF(author);
                o.writeUTF(message);
            });
        }
    }

    private static void broadcastPos(Client _client, double x, double y, double z, float yaw) {
        for (Client client : clients) {
            if (client == _client) continue;
            client.send(o -> {
                o.writeByte(Packets.POS);
                o.writeUTF(_client.getUsername());
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
            });
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
    }

    @FunctionalInterface
    interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }

    public static class Client {
        private final String username;
        private final Socket socket;
        private final DataOutputStream out;
        private final Object writeLock = new Object();

        public Client(String username, Socket socket, DataOutputStream out) {
            this.username = username;
            this.socket = socket;
            this.out = out;
        }

        public void send(PacketWriter writer) {
            synchronized (writeLock) {
                try {
                    writer.write(out);
                    out.flush();
                } catch (IOException ignored) {}
            }
        }

        public String getUsername() {
            return username;
        }

        public Socket getSocket() {
            return socket;
        }

        public DataOutputStream getOut() {
            return out;
        }
    }
}