package server.client;

import server.net.Broadcaster;
import server.Server;

import java.io.IOException;

public class TimeoutHandler {

    private static final long TIMEOUT_MS = 10_000;

    public static void start() {
        Thread thread = new Thread(() -> {
            while (true) {
                long now = System.currentTimeMillis();

                for (Client client : Server.clients) {
                    Long last = Server.lastKeepAlive.get(client);
                    if (last == null) continue;

                    if (now - last > TIMEOUT_MS) {
                        System.out.println("Client timed out: " + client.getUsername());
                        Broadcaster.broadcastConnection(1, client);
                        try { client.getSocket().close(); } catch (IOException ignored) {}
                        Server.clients.remove(client);
                        Server.lastKeepAlive.remove(client);
                    }
                }

                try { Thread.sleep(1_000); } catch (InterruptedException ignored) {}
            }
        }, "TimeoutHandler");

        thread.setDaemon(true);
        thread.start();
    }
}