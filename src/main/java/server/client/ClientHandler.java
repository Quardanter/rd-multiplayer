package server.client;

import global.Packets;
import server.auth.AuthDatabase;
import server.net.Broadcaster;
import server.Server;
import server.level.Level;
import server.level.LevelChunk;
import server.level.TntManager;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.*;
import java.net.Socket;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class ClientHandler {

    private static final int MAX_USERNAME_LENGTH = 15;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_MESSAGE_LENGTH  = 256;
    private static final int MAX_BLOCK_ID = 7;
    private static final int MAX_SKIN_BYTES = 64 * 1024;
    private static final int TOKEN_LENGTH = 64;

    public static void handle(Socket socket) {
        DataInputStream in = null;
        DataOutputStream out = null;
        Client client = null;

        ChunkTracker chunkTracker = new ChunkTracker();
        boolean spawned = false;

        try {
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 8192));
            byte packetId = in.readByte();
            if (packetId != Packets.AUTH_REQUEST) {
                reject(out, socket, "Invalid authentication flow!");
                return;
            }

            String username = in.readUTF().trim();
            String token    = in.readUTF();

            if (isIPBanned(socket.getInetAddress().getHostAddress())) {
                reject(out, socket, "You're IP-banned from this server!");
                return;
            }

            if (!isValidUsername(username)) {
                reject(out, socket, "Illegal username! You can only use 16 letters, numbers and underscores!");
                return;
            }

            String existing = Server.authDb.getToken(username);
            String newTokenToSend = null;

            if (existing == null) {
                newTokenToSend = Server.authDb.registerNewToken(username);
                System.out.println("Registered new account: " + username);
            } else {
                if (token == null || token.length() != TOKEN_LENGTH || !Server.authDb.verifyToken(username, token)) {
                    System.out.println("Rejected " + username + ": invalid/missing token");
                    reject(out, socket,
                           "That username is already registered on this server. Please pick another name.");
                    return;
                }
            }

            boolean taken = Server.clients.stream()
                    .anyMatch(c -> c.getUsername().equalsIgnoreCase(username));

            String ip = socket.getInetAddress().getHostAddress();

            long connectionsFromIp = Server.clients.stream()
                    .filter(c -> c.getSocket().getInetAddress().getHostAddress().equals(ip))
                    .count();

            if (connectionsFromIp >= Server.MAX_PER_IP) {
                System.out.println("Rejected " + username + ": Too many connections from IP " + ip);
                reject(out, socket, "Too many connections from your IP!");
                return;
            }

            if (taken || Server.clients.size() >= Server.PLAYER_LIMIT) {
                if (taken) {
                    System.out.println("Rejected " + username + ": Username taken.");
                    reject(out, socket, "Username is already taken!");
                } else {
                    System.out.println("Rejected " + username + ": Player limit reached.");
                    reject(out, socket, "This server is full!");
                }
                return;
            }

            out.writeByte(Packets.AUTH_SUCCESS);
            out.flush();

            if (newTokenToSend != null) {
                out.writeByte(Packets.AUTH_TOKEN);
                out.writeUTF(newTokenToSend);
                out.flush();
            }

            client = new Client(username, socket, out);
            Server.clients.add(client);
            Server.lastKeepAlive.put(client, System.currentTimeMillis());

            System.out.println("Client authenticated: " + username);
            Broadcaster.broadcastConnection(0, client);

            for (java.util.Map.Entry<String, byte[]> e : Server.skins.entrySet()) {
                final String uname = e.getKey();
                final byte[] png   = e.getValue();
                client.send(o -> {
                    o.writeByte(Packets.SKIN_DATA);
                    o.writeUTF(uname);
                    o.writeInt(png.length);
                    o.write(png);
                });
            }

            while (true) {
                packetId = in.readByte();

                switch (packetId) {

                    case Packets.REQUEST_LEVEL: {
                        if (spawned) {
                            System.out.println("Ignoring duplicate REQUEST_LEVEL from " + client.getUsername());
                            break;
                        }

                        AuthDatabase.SavedPosition saved =
                                Server.authDb.getPosition(client.getUsername());

                        final double spawnX, spawnY, spawnZ;
                        final float  spawnYaw, spawnPitch;
                        if (saved != null) {
                            spawnX = saved.x;
                            spawnY = saved.y;
                            spawnZ = saved.z;
                            spawnYaw = saved.yaw;
                            spawnPitch = saved.pitch;
                            System.out.printf("Restored %s at (%.1f, %.1f, %.1f)%n",
                                    client.getUsername(), spawnX, spawnY, spawnZ);
                        } else {
                            double[] spawnPos = findSpawnPosition();
                            spawnX = spawnPos[0];
                            spawnY = spawnPos[1];
                            spawnZ = spawnPos[2];
                            spawnYaw = 0f;
                            spawnPitch = 0f;
                        }

                        long now = System.currentTimeMillis();
                        client.setLastPos(spawnX, spawnY, spawnZ, now);
                        client.setMoveTokens(Server.MOVE_BURST, now);
                        client.setLastRotation(spawnYaw, spawnPitch);

                        final Client c = client;
                        c.send(o -> {
                            o.writeByte(Packets.SET_POS);
                            o.writeDouble(spawnX);
                            o.writeDouble(spawnY);
                            o.writeDouble(spawnZ);
                        });
                        c.send(o -> chunkTracker.update(spawnX, spawnZ, o));
                        spawned = true;
                        break;
                    }

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        if (!spawned) break;
                        if (!AntiCheat.checkBlock(client, x, y, z, false, System.currentTimeMillis())) break;
                        Server.level.setTile(x, y, z, 0);
                        Broadcaster.broadcastBlock(Packets.BLOCK_BREAK, x, y, z, 0);
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt(), y = in.readInt(), z = in.readInt();
                        int blockId = in.readByte() & 0xFF;
                        if (!spawned) break;

                        if (blockId <= 0 || blockId > MAX_BLOCK_ID) {
                            System.out.println("Rejected BLOCK_PLACE from " + client.getUsername() + ": invalid blockId " + blockId);
                            break;
                        }

                        if (!AntiCheat.checkBlock(client, x, y, z, true, System.currentTimeMillis())) break;

                        Server.level.setTile(x, y, z, blockId);
                        Broadcaster.broadcastBlock(Packets.BLOCK_PLACE, x, y, z, blockId);

                        if (blockId == TntManager.TNT_BLOCK_ID) {
                            TntManager.schedule(x, y, z);
                        }
                        break;
                    }

                    case Packets.POS: {
                        double x = in.readDouble();
                        double y = in.readDouble();
                        double z = in.readDouble();
                        float yaw = in.readFloat();
                        float pitch = in.readFloat();
                        in.readInt();
                        if (!spawned) break;

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

                        client.setLastRotation(yaw, pitch);

                        // void check
                        if (y < Server.VOID_Y) {
                            double[] respawn = findSpawnPosition();
                            final double rx = respawn[0], ry = respawn[1], rz = respawn[2];
                            client.setLastPos(rx, ry, rz, now);
                            client.setMoveTokens(Server.MOVE_BURST, now);

                            final Client c = client;
                            c.send(o -> {
                                o.writeByte(Packets.SET_POS);
                                o.writeDouble(rx);
                                o.writeDouble(ry);
                                o.writeDouble(rz);
                            });
                            c.send(o -> chunkTracker.update(rx, rz, o));

                            System.out.printf("Respawned %s from void (y=%.1f) to (%.1f, %.1f, %.1f)%n",
                                    client.getUsername(), y, rx, ry, rz);
                            break;
                        }

                        final double fx = x, fz = z;
                        final Client c = client;
                        c.send(o -> chunkTracker.update(fx, fz, o));
                        Broadcaster.broadcastPos(client, x, y, z, yaw, pitch);
                        break;
                    }

                    case Packets.KEEPALIVE: {
                        Server.lastKeepAlive.put(client, System.currentTimeMillis());
                        long stampedTime = in.readLong();
                        boolean isResponse = in.readBoolean();

                        if (isResponse) {
                            long rtt = System.currentTimeMillis() - stampedTime;
                            if (rtt < 0) rtt = 0;
                            if (rtt > 60_000) rtt = 60_000;
                            client.setMeasuredPingMs((int) rtt);
                        } else {
                            final Client c = client;
                            final long t = stampedTime;
                            c.send(o -> {
                                o.writeByte(Packets.KEEPALIVE);
                                o.writeLong(t);
                                o.writeBoolean(true);
                            });
                        }
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

                    case Packets.SKIN_UPLOAD: {
                        int len = in.readInt();
                        if (len <= 0 || len > MAX_SKIN_BYTES) {
                            System.out.println("Disconnecting " + client.getUsername()
                                    + ": invalid SKIN_UPLOAD length " + len);
                            throw new IOException("malformed skin length");
                        }
                        boolean allowed = AntiCheat.checkSkinUpload(client, System.currentTimeMillis());
                        byte[] png = new byte[len];
                        in.readFully(png);
                        if (!allowed) break;
                        if (!isValidSkinPng(png)) {
                            System.out.println("Rejected SKIN_UPLOAD from "
                                    + client.getUsername() + ": not a valid 64x64 or 64x32 PNG");
                            break;
                        }
                        Server.skins.put(client.getUsername(), png);
                        Broadcaster.broadcastSkin(client.getUsername(), png);
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
                double[] pos = client.getLastPos();
                if (pos != null) {
                    Server.authDb.savePosition(client.getUsername(),
                            pos[0], pos[1], pos[2],
                            client.getLastYaw(), client.getLastPitch());
                }

                Server.clients.remove(client);
                Server.lastKeepAlive.remove(client);
                Server.skins.remove(client.getUsername());
                client.close();
            } else {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static final int SPAWN_CENTER = 128;
    private static final int SPAWN_RADIUS = 200;
    private static final java.util.Random SPAWN_RNG = new java.util.Random();

    private static double[] findSpawnPosition() {
        int depth = Server.level.getDepth();

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle  = SPAWN_RNG.nextDouble() * 2.0 * Math.PI;
            double radius = Math.sqrt(SPAWN_RNG.nextDouble()) * SPAWN_RADIUS;
            int x = (int) Math.round(SPAWN_CENTER + radius * Math.cos(angle));
            int z = (int) Math.round(SPAWN_CENTER + radius * Math.sin(angle));

            int surfaceY = findSurfaceY(x, z, depth);
            if (surfaceY >= 0) {
                return new double[]{ x + 0.5, surfaceY + 1.0, z + 0.5 };
            }
        }

        return new double[]{ SPAWN_CENTER + 0.5, depth + 1.0, SPAWN_CENTER + 0.5 };
    }

    private static int findSurfaceY(int x, int z, int depth) {
        int cx = Math.floorDiv(x, Level.CHUNK_SIZE);
        int cz = Math.floorDiv(z, Level.CHUNK_SIZE);
        LevelChunk chunk = Server.level.getOrLoadChunk(cx, cz);

        int lx = Math.floorMod(x, Level.CHUNK_SIZE);
        int lz = Math.floorMod(z, Level.CHUNK_SIZE);

        for (int y = depth - 1; y >= 0; y--) {
            if (chunk.getBlock(lx, y, lz) != 0) {
                return y + 1;
            }
        }
        return -1;
    }

    private static void reject(DataOutputStream out, Socket socket, String reason) throws IOException {
        out.writeByte(Packets.AUTH_FAILED);
        out.writeUTF(reason);
        out.flush();
        socket.close();
    }

    private static boolean isValidUsername(String username) {
        if (username == null) return false;
        int len = username.length();
        if (len < MIN_USERNAME_LENGTH || len > MAX_USERNAME_LENGTH) return false;
        if (username.equalsIgnoreCase("server")) return false;
        return username.matches("[A-Za-z0-9_]+");
    }

    private static boolean isIPBanned(String ip) throws IOException {
        Path path = Server.BANNED_PATH;
        String bannedIPs = new String(Files.readAllBytes(path));
        System.out.printf("Is %s banned: %b%n", ip, bannedIPs.contains(ip));
        return bannedIPs.contains(ip);
    }

    private static boolean isValidSkinPng(byte[] data) {
        if (data == null || data.length == 0) return false;
        if (data.length < 8 || (data[0] & 0xFF) != 0x89 || data[1] != 'P' || data[2] != 'N' || data[3] != 'G' || data[4] != 0x0D || data[5] != 0x0A || data[6] != 0x1A || data[7] != 0x0A) {
            return false;
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) return false;
            int w = img.getWidth();
            int h = img.getHeight();
            return (w == 64 && h == 64) || (w == 64 && h == 32);
        } catch (IOException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}