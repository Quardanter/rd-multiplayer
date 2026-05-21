package server.level;

import global.Packets;
import server.Server;
import server.net.Broadcaster;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class TntManager {
    public static final int TNT_BLOCK_ID = 7;
    public static final int OBSIDIAN_BLOCK_ID = 4;
    public static final int WATER_BLOCK_ID = 8;

    public static final int FUSE_SECONDS = 5;
    public static final int BLAST_RADIUS = 3;

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TntFuseScheduler");
                t.setDaemon(true);
                return t;
            });

    private static final Set<Long> fusing = ConcurrentHashMap.newKeySet();

    private TntManager() {}

    public static void schedule(int x, int y, int z) {
        long key = pack(x, y, z);
        if (!fusing.add(key)) return;

        EXECUTOR.schedule(() -> {
            try {
                explode(x, y, z);
            } catch (Throwable t) {
                if (Server.LOGS) System.err.println("TNT explosion failed at " + x + "," + y + "," + z);
                t.printStackTrace();
            } finally {
                fusing.remove(key);
            }
        }, FUSE_SECONDS, TimeUnit.SECONDS);
    }

    private static void explode(int x, int y, int z) {
        Level level = Server.level;
        if (level == null) return;

        if ((level.getTile(x, y, z) & 0xFF) != TNT_BLOCK_ID) return;

        level.setTile(x, y, z, 0);
        Broadcaster.broadcastBlock(Packets.BLOCK_BREAK, x, y, z, 0);

        int r = BLAST_RADIUS;
        int rSq = r * r;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (dx*dx + dy*dy + dz*dz > rSq) continue;

                    int bx = x + dx, by = y + dy, bz = z + dz;
                    int id = level.getTile(bx, by, bz) & 0xFF;

                    if (id == 0) continue;
                    if (id == OBSIDIAN_BLOCK_ID) continue;
                    if (id == WATER_BLOCK_ID) continue;

                    if (id == TNT_BLOCK_ID) {
                        schedule(bx, by, bz);
                        continue;
                    }

                    level.setTile(bx, by, bz, 0);
                    Broadcaster.broadcastBlock(Packets.BLOCK_BREAK, bx, by, bz, 0);
                }
            }
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long)(x & 0x1FFFFF) << 42) | ((long)(y & 0x1FFFFF) << 21) | (long)(z & 0x1FFFFF);
    }
}