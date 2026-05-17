package server.net;

import global.Packets;
import server.Server;
import server.client.Client;

public final class ServerPinger {

    private static final long PROBE_INTERVAL_MS = 2000L;

    private ServerPinger() {}

    public static void start() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(PROBE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                for (Client c : Server.clients) {
                    c.send(o -> {
                        o.writeByte(Packets.KEEPALIVE);
                        o.writeLong(now);
                        o.writeBoolean(false);
                    });
                }
            }
        }, "ServerPinger");
        t.setDaemon(true);
        t.start();
    }
}