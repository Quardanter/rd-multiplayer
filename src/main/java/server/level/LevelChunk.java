package server.level;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class LevelChunk {
    public static final int CHUNK_SIZE = 16;

    public final int chunkX;
    public final int chunkZ;
    public final int depth;

    public final byte[] blocks;

    private boolean dirty = false;

    public LevelChunk(int chunkX, int chunkZ, int depth) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.depth = depth;
        this.blocks = new byte[CHUNK_SIZE * CHUNK_SIZE * depth];
    }

    // block access
    private int index(int lx, int y, int lz) {
        return (y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx;
    }

    public byte getBlock(int lx, int y, int lz) {
        return blocks[index(lx, y, lz)];
    }

    public void setBlock(int lx, int y, int lz, int id) {
        blocks[index(lx, y, lz)] = (byte) id;
        dirty = true;
    }

    public boolean isDirty() {return dirty;}
    public void cleardirty() {dirty = false;}
    public void markDirty() {dirty = true;}

    // chunk generation
    public void generate() {
        int surfaceY = depth * 2 / 3;
        for (int y = 0; y < depth; y++) {
            byte id = (y <= surfaceY) ? (byte) 1 : (byte) 0;
            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                    blocks[index(lx, y, lz)] = id;
                }
            }
        }
        dirty = true;
    }

    // persistence (syncing to disk)
    private static Path chunkPath(Path dir, int cx, int cz) {
        return dir.resolve("c_" + cx + "_" + cz + ".dat");
    }

    // load from disk
    public boolean load(Path chunkDir) {
        Path p = chunkPath(chunkDir, chunkX, chunkZ);
        if (!Files.exists(p)) return false;
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(Files.newInputStream(p)))) {
            dis.readFully(blocks);
            dirty = false;
            return true;
        } catch (IOException e) {
            System.err.println("Failed to load " + p + ": " + e.getMessage());
            return false;
        }
    }

    // save to disk
    public void save(Path chunkDir) {
        if (!dirty) return;
        Path p = chunkPath(chunkDir, chunkX, chunkZ);
        try {
            Files.createDirectories(chunkDir);
            try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(p)))) {
                dos.write(blocks);
            }
            dirty = false;
        } catch (IOException e) {
            System.err.println("Failed to save " + p + ": " + e.getMessage());
        }
    }

    public void forceSave(Path chunkDir) {
        markDirty();
        save(chunkDir);
    }
    
}
