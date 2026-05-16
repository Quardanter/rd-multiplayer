package server.level;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Level {
    public static final int CHUNK_SIZE = LevelChunk.CHUNK_SIZE;
    private static final Path CHUNK_DIR = Paths.get("chunks");
    public final int width;
    public final int height;
    public final int depth;

    // chunk storage
    private final ConcurrentHashMap<Long, LevelChunk> loadedChunks = new ConcurrentHashMap<>();
    public Level(int width, int height, int depth) {
        this.width  = width;
        this.height = height;
        this.depth  = depth;

        Files.exists(CHUNK_DIR);
    }

    private static long key(int cx, int cz) {
        return (long) cx * 100_000L + cz;
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
        long k = key(cx, cz);
        LevelChunk chunk = loadedChunks.remove(k);
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

    public void save() {
        saveAll();
    }

    // block access
    private LevelChunk chunkFor(int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= height) return null;
        return getOrLoadChunk(x / CHUNK_SIZE, z / CHUNK_SIZE);
    }

    public byte getTile(int x, int y, int z) {
        if (y < 0 || y >= depth) return 0;
        LevelChunk chunk = chunkFor(x, z);
        if (chunk == null) return 0;
        return chunk.getBlock(x % CHUNK_SIZE, y, z % CHUNK_SIZE);
    }

    public void setTile(int x, int y, int z, int id) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return;
        LevelChunk chunk = chunkFor(x, z);
        if (chunk == null) return;
        chunk.setBlock(x % CHUNK_SIZE, y, z % CHUNK_SIZE, id);
    }

    public byte[] getChunkBlocks(int cx, int cz) {
        return getOrLoadChunk(cx, cz).blocks;
    }

    public int getChunkCountX() { return width  / CHUNK_SIZE; }
    public int getChunkCountZ() { return height / CHUNK_SIZE; }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }
}