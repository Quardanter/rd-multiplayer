package server.net;

import server.Server;
import server.client.Client;

public final class PingBroadcaster {
    private static final long BROADCAST_INTERVAL_MS = 2000L;

    private PingBroadcaster() {}

    public static void start() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(BROADCAST_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                for (Client sender : Server.clients) {
                    Broadcaster.broadcastPing(sender, sender.getMeasuredPingMs());
                }
            }
        }, "PingBroadcaster");
        t.setDaemon(true);
        t.start();
    }
}