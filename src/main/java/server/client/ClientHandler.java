package server.client;

import global.Packets;
import server.net.Broadcaster;
import server.Server;

import java.io.*;
import java.net.Socket;

public class ClientHandler {

    private static final int MAX_USERNAME_LENGTH = 15;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_MESSAGE_LENGTH  = 256;

    public static void handle(Socket socket) {
        DataInputStream in = null;
        DataOutputStream out = null;
        Client client = null;

        ChunkTracker chunkTracker = new ChunkTracker();

        try {
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 8192));
            byte packetId = in.readByte();
            if (packetId != Packets.AUTH_REQUEST) {
                reject(out, socket);
                return;
            }

            String username = in.readUTF().trim();

            if (!isValidUsername(username)) {
                reject(out, socket);
                return;
            }

            boolean taken = Server.clients.stream()
                    .anyMatch(c -> c.getUsername().equalsIgnoreCase(username));

            String ip = socket.getInetAddress().getHostAddress();

            long connectionsFromIp = Server.clients.stream()
                    .filter(c -> c.getSocket().getInetAddress().getHostAddress().equals(ip))
                    .count();

            if (connectionsFromIp >= Server.MAX_PER_IP) {
                System.out.println("Rejected " + username + ": Too many connections from IP " + ip);
                reject(out, socket);
                return;
            }

            if (taken || Server.clients.size() >= Server.PLAYER_LIMIT) {
                if (taken) System.out.println("Rejected " + username + ": Username taken.");
                else       System.out.println("Rejected " + username + ": Player limit reached.");
                reject(out, socket);
                return;
            }

            out.writeByte(Packets.AUTH_SUCCESS);
            out.flush();

            client = new Client(username, socket, out);
            Server.clients.add(client);
            Server.lastKeepAlive.put(client, System.currentTimeMillis());

            System.out.println("Client authenticated: " + username);
            Broadcaster.broadcastConnection(0, client);


            while (true) {
                packetId = in.readByte();

                switch (packetId) {
                    case Packets.REQUEST_LEVEL: {
                        double spawnX = 128.0;
                        double spawnY = Server.level.getDepth() + 3.0;
                        double spawnZ = 128.0;

                        final Client c = client;

                        c.send(o -> {
                            o.writeByte(Packets.SET_POS);
                            o.writeDouble(spawnX);
                            o.writeDouble(spawnY);
                            o.writeDouble(spawnZ);
                        });
                        c.send(o -> chunkTracker.update(spawnX, spawnZ, o));
                        break;
                    }

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        if (!AntiCheat.checkBlock(client, x, y, z, false, System.currentTimeMillis())) break;
                        Server.level.setTile(x, y, z, 0);
                        Broadcaster.broadcastBlock(Packets.BLOCK_BREAK, x, y, z);
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        if (!AntiCheat.checkBlock(client, x, y, z, true, System.currentTimeMillis())) break;
                        Server.level.setTile(x, y, z, 1);
                        Broadcaster.broadcastBlock(Packets.BLOCK_PLACE, x, y, z);
                        break;
                    }

                    case Packets.POS: {
                        double x = in.readDouble();
                        double y = in.readDouble();
                        double z = in.readDouble();
                        float yaw = in.readFloat();
                        int ping = in.readInt();

                        long now = System.currentTimeMillis();
                        if (!AntiCheat.checkMovement(client, x, y, z, now)) {
                            double[] validPos = client.getLastPos();
                            if (validPos != null) {
                                final Client c = client;
                                c.send(o -> {
                                    o.writeByte(Packets.SET_POS);
                                    o.writeDouble(validPos[0]);
                                    o.writeDouble(validPos[1]);
                                    o.writeDouble(validPos[2]);
                                });
                            }
                            break;
                        }

                        final double fx = x, fz = z;
                        final Client c = client;
                        c.send(o -> chunkTracker.update(fx, fz, o));

                        Broadcaster.broadcastPos(client, x, y, z, yaw, ping);
                        break;
                    }

                    case Packets.KEEPALIVE: {
                        Server.lastKeepAlive.put(client, System.currentTimeMillis());
                        long clientTime = in.readLong();
                        final Client c = client;
                        c.send(o -> {
                            o.writeByte(Packets.KEEPALIVE);
                            o.writeLong(clientTime);
                        });
                        break;
                    }

                    case Packets.CHAT: {
                        in.readUTF();
                        String message = in.readUTF().trim();

                        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) break;
                        message = message.replaceAll("[^\\x20-\\x7E]", "");
                        if (message.isEmpty()) break;

                        final String author  = client.getUsername();
                        final String payload = message;
                        Broadcaster.broadcastChat(author, payload);
                        break;
                    }

                    default:
                        System.err.println("Unknown packet id: " + packetId);
                        break;
                }
            }

        } catch (IOException e) {
            if (client != null) Broadcaster.broadcastConnection(1, client);
            System.out.println("Client disconnected: "
                    + (client != null ? client.getUsername() : "unknown"));
        } finally {
            chunkTracker.clear();
            if (client != null) {
                Server.clients.remove(client);
                Server.lastKeepAlive.remove(client);
                client.close();
            } else {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void reject(DataOutputStream out, Socket socket) throws IOException {
        out.writeByte(Packets.AUTH_FAILED);
        out.flush();
        socket.close();
    }

    private static boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < MIN_USERNAME_LENGTH || len > MAX_USERNAME_LENGTH) return false;
        return username.matches("[A-Za-z0-9_]+");
    }
}