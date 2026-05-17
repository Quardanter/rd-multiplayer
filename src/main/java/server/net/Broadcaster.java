package server.net;

import global.Packets;
import server.Server;
import server.client.Client;
import server.client.ChunkTracker;


public class Broadcaster {

    private static final double VISIBILITY_RANGE = ChunkTracker.RENDER_DISTANCE * 16;

    private static boolean inRange(Client a, Client b) {
        double[] posA = a.getLastPos();
        double[] posB = b.getLastPos();
        if (posA == null || posB == null) return false;
        double dx = posA[0] - posB[0];
        double dz = posA[2] - posB[2];
        return (dx * dx + dz * dz) <= VISIBILITY_RANGE * VISIBILITY_RANGE;
    }

    public static void broadcastBlock(byte packet, int x, int y, int z, int blockId) {
        for (Client c : Server.clients) {
            c.send(o -> {
                o.writeByte(packet);
                o.writeInt(x); o.writeInt(y); o.writeInt(z);
                if (packet == Packets.BLOCK_PLACE) o.writeByte(blockId);
            });
        }
    }

    public static void broadcastBlock(byte packet, int x, int y, int z) {
        broadcastBlock(packet, x, y, z, 0);
    }

    public static void broadcastConnection(int type, Client sender) {
        for (Client client : Server.clients) {
            if (client == sender) continue;
            client.send(o -> {
                o.writeByte(Packets.CONNECTION);
                o.writeInt(type);
                o.writeUTF(sender.getUsername());
            });
        }
    }

    public static void broadcastChat(String author, String message) {
        System.out.printf("<%s> %s%n", author, message);
        for (Client client : Server.clients) {
            client.send(o -> {
                o.writeByte(Packets.CHAT);
                o.writeUTF(author);
                o.writeUTF(message);
            });
        }
    }

    public static void broadcastSkin(String username, byte[] png) {
        for (Client client : Server.clients) {
            client.send(o -> {
                o.writeByte(Packets.SKIN_DATA);
                o.writeUTF(username);
                o.writeInt(png.length);
                o.write(png);
            });
        }
    }

    public static void broadcastPos(Client sender, double x, double y, double z, float yaw, float pitch) {
        for (Client client : Server.clients) {
            if (client == sender) continue;
            if (!inRange(client, sender)) continue;

            client.send(o -> {
                o.writeByte(Packets.POS);
                o.writeUTF(sender.getUsername());
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeFloat(pitch);
            });
        }
    }

    public static void broadcastPing(Client sender, int pingMs) {
        for (Client client : Server.clients) {
            client.send(o -> {
                o.writeByte(Packets.PING_INFO);
                o.writeUTF(sender.getUsername());
                o.writeInt(pingMs);
            });
        }
    }
}