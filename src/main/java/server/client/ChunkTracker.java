package server.client;

import global.Packets;
import server.Server;
import server.level.Level;
import server.level.LevelChunk;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ChunkTracker {
    public static final int RENDER_DISTANCE = 8; //hard coded probably should go in server.properties
    private static final int CHUNK_SIZE = LevelChunk.CHUNK_SIZE;

    private final Set<Long> sentChunks = new HashSet<>();


    // public:
    public void update(double worldX, double worldZ, DataOutputStream out) throws IOException {
        Level level = Server.level;

        int playerCX = Math.max(0, Math.min((int)(worldX / CHUNK_SIZE), level.getChunkCountX() - 1));
        int playerCZ = Math.max(0, Math.min((int)(worldZ / CHUNK_SIZE), level.getChunkCountZ() - 1));

        int minCX = Math.max(0, playerCX - RENDER_DISTANCE);
        int maxCX = Math.min(level.getChunkCountX() - 1, playerCX + RENDER_DISTANCE);
        int minCZ = Math.max(0, playerCZ - RENDER_DISTANCE);
        int maxCZ = Math.min(level.getChunkCountZ() - 1, playerCZ + RENDER_DISTANCE);

        Set<Long> toRemove = new HashSet<>();
        for (long key : sentChunks) {
            int cx = (int)(key >> 32);
            int cz = (int)(key & 0xFFFFFFFFL);
            if (cx < minCX || cx > maxCX || cz < minCZ || cz > maxCZ) {
                sendUnload(out, cx, cz);
                toRemove.add(key);
            }
        }
        sentChunks.removeAll(toRemove);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                long key = chunkKey(cx, cz);
                if (!sentChunks.contains(key)) {
                    sendChunk(out, level, cx, cz);
                    sentChunks.add(key);
                }
            }
        }
    }

    public boolean hasChunk(int cx, int cz) {
        return sentChunks.contains(chunkKey(cx, cz));
    }

    public void clear() {
        sentChunks.clear();
    }

    private void sendChunk(DataOutputStream out, Level level, int cx, int cz) throws IOException {
        byte[] data = level.getChunkBlocks(cx, cz);

        out.writeByte(Packets.CHUNK_DATA);
        out.writeInt(cx);
        out.writeInt(cz);
        out.writeInt(level.getDepth());
        out.writeInt(data.length);
        out.write(data);
    }

    private void sendUnload(DataOutputStream out, int cx, int cz) throws IOException {
        out.writeByte(Packets.CHUNK_UNLOAD);
        out.writeInt(cx);
        out.writeInt(cz);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}