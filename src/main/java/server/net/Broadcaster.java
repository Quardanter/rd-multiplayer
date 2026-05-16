package server.net;

import global.Packets;
import server.Server;
import server.client.Client;

import java.io.DataOutputStream;
import java.io.IOException;

public class Broadcaster {

    public static void broadcastBlock(byte type, int x, int y, int z) {
        for (Client client : Server.clients) {
            client.send(o -> {
                o.writeByte(type);
                o.writeInt(x);
                o.writeInt(y);
                o.writeInt(z);
            });
        }
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
        for (Client client : Server.clients) {
            client.send(o -> {
                o.writeByte(Packets.CHAT);
                o.writeUTF(author);
                o.writeUTF(message);
            });
        }
    }

    public static void broadcastPos(Client sender, double x, double y, double z, float yaw, int ping) {
        for (Client client : Server.clients) {
            if (client == sender) continue;
            client.send(o -> {
                o.writeByte(Packets.POS);
                o.writeUTF(sender.getUsername());
                o.writeDouble(x);
                o.writeDouble(y);
                o.writeDouble(z);
                o.writeFloat(yaw);
                o.writeInt(ping);
            });
        }
    }

    public static void sendLevel(DataOutputStream out) throws IOException {
        byte[] blocks = Server.level.getBlocks();
        out.writeByte(Packets.LEVEL_DATA);
        out.writeInt(Server.level.getWidth());
        out.writeInt(Server.level.getHeight());
        out.writeInt(Server.level.getDepth());
        out.writeInt(blocks.length);
        out.write(blocks);
    }
}