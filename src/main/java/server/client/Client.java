package server.client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Client {
    private final String username;
    private final Socket socket;
    private final DataOutputStream out;
    private final Object writeLock = new Object();

    private double[] lastPos = null;
    private long lastMoveTime = 0;
    private double placeTokens = 0.0;
    private long lastPlaceTime = 0;

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

    public String getUsername() { return username; }
    public Socket getSocket() { return socket; }
    public DataOutputStream getOut() { return out; }

    public double[] getLastPos() { return lastPos; }
    public long getLastMoveTime() { return lastMoveTime; }

    public void setLastPos(double x, double y, double z, long time) {
        if (lastPos == null) lastPos = new double[3];
        lastPos[0] = x;
        lastPos[1] = y;
        lastPos[2] = z;
        this.lastMoveTime = time;
    }

    public double getPlaceTokens() { return placeTokens; }
    public long getLastPlaceTime() { return lastPlaceTime; }
    public void setPlaceTokens(double tokens, long time) {
        this.placeTokens = tokens;
        this.lastPlaceTime = time;
    }

    @FunctionalInterface
    public interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }
}