package server.level;

import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;

public class Level {

    public static final int CHUNK_SIZE = LevelChunk.CHUNK_SIZE; // 16

    private static final Path CHUNK_DIR = Paths.get("chunks");

    public final int depth;

    private final ConcurrentHashMap<Long, LevelChunk> loadedChunks = new ConcurrentHashMap<>();

    public Level(int depth) {
        this.depth = depth;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public LevelChunk getOrLoadChunk(int cx, int cz) {
        long k = key(cx, cz);
        LevelChunk chunk = loadedChunks.get(k);
        if (chunk != null) return chunk;

        chunk = new LevelChunk(cx, cz, depth);
        if (!chunk.load(CHUNK_DIR)) {
            chunk.generate();
            System.out.println("Generated chunk " + cx + "," + cz);
        } else {
            System.out.println("Loaded chunk " + cx + "," + cz);
        }
        loadedChunks.put(k, chunk);
        return chunk;
    }

    public void unloadChunk(int cx, int cz) {
        LevelChunk chunk = loadedChunks.remove(key(cx, cz));
        if (chunk != null) {
            chunk.save(CHUNK_DIR);
            System.out.println("Unloaded chunk " + cx + "," + cz);
        }
    }

    public void saveAll() {
        for (LevelChunk chunk : loadedChunks.values()) {
            chunk.save(CHUNK_DIR);
        }
        System.out.println("All chunks saved.");
    }

    public void save() { saveAll(); }

    public byte getTile(int x, int y, int z) {
        if (y < 0 || y >= depth) return 0;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        LevelChunk chunk = loadedChunks.get(key(cx, cz));
        if (chunk == null) return 0;   // not loaded = air
        return chunk.getBlock(lx, y, lz);
    }

    public void setTile(int x, int y, int z, int id) {
        if (y < 0 || y >= depth) return;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        LevelChunk chunk = loadedChunks.get(key(cx, cz));
        if (chunk == null) return;
        chunk.setBlock(lx, y, lz, id);
    }

    public byte[] getChunkBlocks(int cx, int cz) {
        return getOrLoadChunk(cx, cz).blocks;
    }

    public int getDepth() { return depth; }
}