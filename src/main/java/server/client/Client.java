package server.client;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Client {
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

    public String getUsername() { return username; }
    public Socket getSocket()   { return socket; }
    public DataOutputStream getOut() { return out; }

    @FunctionalInterface
    public interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }
}