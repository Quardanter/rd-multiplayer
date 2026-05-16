package client.level;

import client.phys.AABB;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Level {

    public static final int CHUNK_SIZE = 16;
    public int depth;

    private final ConcurrentHashMap<Long, byte[]> chunks = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, int[]> chunkLightDepths = new ConcurrentHashMap<>();

    private final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    public Level(int depth) {
        this.depth = depth;
    }

    public void loadChunk(int cx, int cz, int chunkDepth, byte[] data) {
        if (this.depth == 0) this.depth = chunkDepth;

        chunks.put(chunkKey(cx, cz), data);

        calcLightDepthsForChunk(cx, cz);

        for (LevelListener l : levelListeners) {
            l.lightColumnChanged(cx * CHUNK_SIZE, cz * CHUNK_SIZE, 0, depth);
        }
        for (LevelListener l : levelListeners) {
            l.chunkLoaded(cx, cz);
        }
    }

    public void unloadChunk(int cx, int cz) {
        long key = chunkKey(cx, cz);
        chunks.remove(key);
        chunkLightDepths.remove(key);

        for (LevelListener l : levelListeners) {
            l.chunkUnloaded(cx, cz);
        }
    }

    public void loadLevel(int w, int h, int d, byte[] flatBlocks) {
        this.depth = d;
        chunks.clear();
        chunkLightDepths.clear();

        int cntX = w / CHUNK_SIZE;
        int cntZ = h / CHUNK_SIZE;

        for (int cx = 0; cx < cntX; cx++) {
            for (int cz = 0; cz < cntZ; cz++) {
                byte[] chunkData = new byte[CHUNK_SIZE * CHUNK_SIZE * d];
                for (int y = 0; y < d; y++) {
                    for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                            int wx = cx * CHUNK_SIZE + lx;
                            int wz = cz * CHUNK_SIZE + lz;
                            int flatIdx  = (y * h + wz) * w + wx;
                            int chunkIdx = (y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx;
                            chunkData[chunkIdx] = flatBlocks[flatIdx];
                        }
                    }
                }
                long key = chunkKey(cx, cz);
                chunks.put(key, chunkData);
                calcLightDepthsForChunk(cx, cz);
            }
        }

        for (LevelListener l : levelListeners) l.allChanged();
    }

    private void calcLightDepthsForChunk(int cx, int cz) {
        long key = chunkKey(cx, cz);
        byte[] data = chunks.get(key);
        if (data == null) return;

        int[] ld = new int[CHUNK_SIZE * CHUNK_SIZE];
        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                int d = this.depth - 1;
                while (d > 0 && data[(d * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] == 0) {
                    d--;
                }
                ld[lx + lz * CHUNK_SIZE] = d;
            }
        }
        chunkLightDepths.put(key, ld);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public byte getRawBlock(int x, int y, int z) {
        if (y < 0 || y >= depth) return 0;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        byte[] data = chunks.get(chunkKey(cx, cz));
        if (data == null) return 0;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        return data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx];
    }

    public boolean isTile(int x, int y, int z) {
        return getRawBlock(x, y, z) != 0;
    }

    public boolean isSolidTile(int x, int y, int z) {
        return isTile(x, y, z);
    }

    public boolean isLightBlocker(int x, int y, int z) {
        return isSolidTile(x, y, z);
    }

    public float getBrightness(int x, int y, int z) {
        float dark  = 0.8F;
        float light = 1.0F;
        if (y < 0 || y >= depth) return light;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        int[] ld = chunkLightDepths.get(chunkKey(cx, cz));
        if (ld == null) return light;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        if (y < ld[lx + lz * CHUNK_SIZE]) return dark;
        return light;
    }

    public void setTile(int x, int y, int z, int id) {
        if (y < 0 || y >= depth) return;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        long key = chunkKey(cx, cz);
        byte[] data = chunks.get(key);
        if (data == null) return;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = (byte) id;

        calcLightDepthsForChunk(cx, cz);
        for (LevelListener l : levelListeners) l.tileChanged(x, y, z);
    }

    public ArrayList<AABB> getCubes(AABB bb) {
        ArrayList<AABB> list = new ArrayList<>();

        int x0 = (int) Math.floor(bb.minX) - 1;
        int x1 = (int) Math.ceil(bb.maxX) + 1;
        int y0 = Math.max(0, (int) Math.floor(bb.minY) - 1);
        int y1 = Math.min(depth, (int) Math.ceil(bb.maxY) + 1);
        int z0 = (int) Math.floor(bb.minZ) - 1;
        int z1 = (int) Math.ceil(bb.maxZ) + 1;

        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                for (int z = z0; z < z1; z++)
                    if (isSolidTile(x, y, z))
                        list.add(new AABB(x, y, z, x+1, y+1, z+1));

        return list;
    }

    public void addListener(LevelListener l) { levelListeners.add(l); }

    public byte[] getBlocks() { return new byte[0]; }

    public int getWidth() { return Integer.MAX_VALUE; }
    public int getHeight() { return Integer.MAX_VALUE; }
    public int getDepth() { return depth; }

    public boolean hasAnyChunk() { return !chunks.isEmpty(); }

    public void forEachLoadedChunk(java.util.function.BiConsumer<Integer, Integer> action) {
        for (long key : chunks.keySet()) {
            int cx = (int)(key >> 32);
            int cz = (int)(key & 0xFFFFFFFFL);
            action.accept(cx, cz);
        }
    }
}