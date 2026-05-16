package server.client;

import global.Packets;
import server.Server;
import server.level.Level;
import server.level.LevelChunk;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class ChunkTracker {

    public static final int RENDER_DISTANCE = 8;
    private static final int CHUNK_SIZE = LevelChunk.CHUNK_SIZE;


    private static final ConcurrentHashMap<Long, AtomicInteger> refCounts =
            new ConcurrentHashMap<>();

    private static void addRef(int cx, int cz) {
        refCounts.computeIfAbsent(pack(cx, cz), k -> new AtomicInteger(0))
                 .incrementAndGet();
    }

    private static void releaseRef(int cx, int cz) {
        long key = pack(cx, cz);
        AtomicInteger count = refCounts.get(key);
        if (count != null && count.decrementAndGet() <= 0) {
            refCounts.remove(key);
            Server.level.unloadChunk(cx, cz);
        }
    }


    private final Set<Long> sentChunks = new HashSet<>();

    public void update(double worldX, double worldZ, DataOutputStream out) throws IOException {
        Level level = Server.level;

        int playerCX = (int) Math.floor(worldX / CHUNK_SIZE);
        int playerCZ = (int) Math.floor(worldZ / CHUNK_SIZE);

        int minCX = playerCX - RENDER_DISTANCE;
        int maxCX = playerCX + RENDER_DISTANCE;
        int minCZ = playerCZ - RENDER_DISTANCE;
        int maxCZ = playerCZ + RENDER_DISTANCE;

        Set<Long> toRemove = new HashSet<>();
        for (long key : sentChunks) {
            int cx = unpackX(key);
            int cz = unpackZ(key);
            if (cx < minCX || cx > maxCX || cz < minCZ || cz > maxCZ) {
                out.writeByte(Packets.CHUNK_UNLOAD);
                out.writeInt(cx);
                out.writeInt(cz);
                toRemove.add(key);
                releaseRef(cx, cz);
            }
        }
        sentChunks.removeAll(toRemove);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                long key = pack(cx, cz);
                if (!sentChunks.contains(key)) {
                    writeChunk(out, level, cx, cz);
                    sentChunks.add(key);
                    addRef(cx, cz);
                }
            }
        }
    }

    public boolean hasChunk(int cx, int cz) {
        return sentChunks.contains(pack(cx, cz));
    }

    public void clear() {
        for (long key : sentChunks) {
            releaseRef(unpackX(key), unpackZ(key));
        }
        sentChunks.clear();
    }

    private static void writeChunk(DataOutputStream out, Level level, int cx, int cz) throws IOException {
        byte[] data = level.getChunkBlocks(cx, cz);
        out.writeByte(Packets.CHUNK_DATA);
        out.writeInt(cx);
        out.writeInt(cz);
        out.writeInt(level.getDepth());
        out.writeInt(data.length);
        out.write(data);
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
    private static int unpackX(long key) { return (int)(key >> 32); }
    private static int unpackZ(long key) { return (int)(key & 0xFFFFFFFFL); }
}