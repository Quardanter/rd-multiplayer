package server.client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Client {
    private final String username;
    private final Socket socket;
    private final DataOutputStream out;
    private final Object writeLock = new Object();

    private final BlockingQueue<PacketWriter> sendQueue = new LinkedBlockingQueue<>();
    private final Thread sendThread;

    private double[] lastPos = null;
    private long lastMoveTime = 0;
    private double moveTokens = 10.0;

    private double placeTokens = 0.0;
    private long lastPlaceTime = 0;

    private double breakTokens = 0.0;
    private long lastBreakTime = 0;

    public Client(String username, Socket socket, DataOutputStream out) {
        this.username = username;
        this.socket = socket;
        this.out = out;

        this.sendThread = new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    PacketWriter writer = sendQueue.take();
                    synchronized (writeLock) {
                        try {
                            writer.write(out);
                            out.flush();
                        } catch (IOException ignored) {}
                    }
                }
            } catch (InterruptedException e) {
            }
        }, "SendThread-" + username);
        this.sendThread.setDaemon(true);
        this.sendThread.start();
    }

    public void send(PacketWriter writer) {
        sendQueue.add(writer);
    }

    public void close() {
        try { socket.close(); } catch (IOException ignored) {}
        if (sendThread != null) {
            sendThread.interrupt();
        }
    }

    public String getUsername() { return username; }
    public Socket getSocket() { return socket; }
    public DataOutputStream getOut() { return out; }

    public double[] getLastPos() { return lastPos; }
    public long getLastMoveTime() { return lastMoveTime; }
    public double getMoveTokens() { return moveTokens; }

    public void setLastPos(double x, double y, double z, long time) {
        if (lastPos == null) lastPos = new double[3];
        lastPos[0] = x;
        lastPos[1] = y;
        lastPos[2] = z;
        this.lastMoveTime = time;
    }

    public void setMoveTokens(double tokens, long time) {
        this.moveTokens = tokens;
        this.lastMoveTime = time;
    }

    public double getPlaceTokens() { return placeTokens; }
    public long getLastPlaceTime() { return lastPlaceTime; }
    public void setPlaceTokens(double tokens, long time) {
        this.placeTokens = tokens;
        this.lastPlaceTime = time;
    }

    public double getBreakTokens() { return breakTokens; }
    public long getLastBreakTime() { return lastBreakTime; }
    public void setBreakTokens(double tokens, long time) {
        this.breakTokens = tokens;
        this.lastBreakTime = time;
    }

    @FunctionalInterface
    public interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }
}