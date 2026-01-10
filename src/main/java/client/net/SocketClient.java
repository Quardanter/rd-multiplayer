package client.net;

import client.Minecraft;
import global.Packets;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class SocketClient implements Runnable {
    private final String host;
    private final int port;
    public static DataOutputStream out;

    public SocketClient(String host, int port){
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        try {
            Socket socket = new Socket(host, port);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            SocketClient.out = out;

            while (true) {
                byte packetId = in.readByte();

                switch (packetId) {

                    case Packets.BLOCK_BREAK: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();
                        Minecraft.mc.getLevel().setTile(x, y, z, 0);
                        break;
                    }

                    case Packets.BLOCK_PLACE: {
                        int x = in.readInt();
                        int y = in.readInt();
                        int z = in.readInt();
                        Minecraft.mc.getLevel().setTile(x, y, z, 1);
                        break;
                    }

                    case Packets.LEVEL_DATA: {
                        /*int w = in.readInt();
                        int h = in.readInt();
                        int d = in.readInt();

                        int len = in.readInt();
                        byte[] blocks = new byte[len];
                        in.readFully(blocks);

                        //Minecraft.mc.loadLevel(w, h, d, blocks);*/
                        break;
                    }

                    default:
                        System.err.println("Unknown packet: " + packetId);
                        break;
                }
            }

        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static void sendBlock(int packet, int x, int y, int z) throws IOException {
        out.writeByte(packet);
        out.writeInt(x);
        out.writeInt(y);
        out.writeInt(z);
        out.flush();
    }

}
