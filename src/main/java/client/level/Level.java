package client.level;

import client.phys.AABB;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Level {

    public static final int CHUNK_SIZE = 16;

    private final ConcurrentHashMap<Long, byte[]> chunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, int[]> heightMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> columnLoadCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, java.util.concurrent.ConcurrentSkipListSet<Integer>> columnChunkYs = new ConcurrentHashMap<>();

    private final ArrayList<LevelListener> levelListeners = new ArrayList<>();

    public Level() {}
    private static long chunkKey(int cx, int cy, int cz) {
        return ((long)(cx & 0x1FFFFF) << 42) | ((long)(cy & 0x1FFFFF) << 21) | (long)(cz & 0x1FFFFF);
    }

    private static long columnKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public void loadChunk(int cx, int cy, int cz, byte[] data) {
        long ck = chunkKey(cx, cy, cz);
        byte[] prior = chunks.put(ck, data);
        if (prior == null) {
            long colK = columnKey(cx, cz);
            columnLoadCount.merge(colK, 1, Integer::sum);
            columnChunkYs
                .computeIfAbsent(colK, k -> new java.util.concurrent.ConcurrentSkipListSet<>())
                .add(cy);
        }
        recomputeColumnHeight(cx, cz);

        for (LevelListener l : levelListeners) {
            l.chunkLoaded(cx, cy, cz);
        }
        for (LevelListener l : levelListeners) {
            l.lightColumnChanged(cx * CHUNK_SIZE, cz * CHUNK_SIZE, cy * CHUNK_SIZE, cy * CHUNK_SIZE + CHUNK_SIZE);
        }
    }

    public void unloadChunk(int cx, int cy, int cz) {
        byte[] removed = chunks.remove(chunkKey(cx, cy, cz));
        if (removed != null) {
            long colK = columnKey(cx, cz);
            columnLoadCount.computeIfPresent(colK, (k, v) -> v <= 1 ? null : v - 1);
            java.util.concurrent.ConcurrentSkipListSet<Integer> ys = columnChunkYs.get(colK);
            if (ys != null) {
                ys.remove(cy);
                if (ys.isEmpty()) columnChunkYs.remove(colK);
            }
        }
        recomputeColumnHeight(cx, cz);
        for (LevelListener l : levelListeners) {
            l.chunkUnloaded(cx, cy, cz);
        }
    }

    private void recomputeColumnHeight(int cx, int cz) {
        long colK = columnKey(cx, cz);
        java.util.concurrent.ConcurrentSkipListSet<Integer> ys = columnChunkYs.get(colK);
        if (ys == null || ys.isEmpty()) {
            heightMap.remove(colK);
            return;
        }

        int[] tops = new int[CHUNK_SIZE * CHUNK_SIZE];
        java.util.Arrays.fill(tops, Integer.MIN_VALUE);
        int remaining = tops.length;

        for (java.util.Iterator<Integer> it = ys.descendingIterator(); it.hasNext() && remaining > 0; ) {
            int cy = it.next();
            byte[] data = chunks.get(chunkKey(cx, cy, cz));
            if (data == null) continue;

            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                    int idx = lx + lz * CHUNK_SIZE;
                    if (tops[idx] != Integer.MIN_VALUE) continue;
                    for (int ly = CHUNK_SIZE - 1; ly >= 0; ly--) {
                        if (data[(ly * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] != 0) {
                            tops[idx] = cy * CHUNK_SIZE + ly;
                            remaining--;
                            break;
                        }
                    }
                }
            }
        }

        heightMap.put(colK, tops);
    }

    private static int signExtend21(long v) {
        long m = v & 0x1FFFFF;
        return (int)((m & 0x100000L) != 0 ? m | ~0x1FFFFFL : m);
    }
    private static int unpackCX(long k) { return signExtend21((k >> 42) & 0x1FFFFF); }
    private static int unpackCY(long k) { return signExtend21((k >> 21) & 0x1FFFFF); }
    private static int unpackCZ(long k) { return signExtend21( k & 0x1FFFFF); }

    public byte getRawBlock(int x, int y, int z) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        byte[] data = chunks.get(chunkKey(cx, cy, cz));
        if (data == null) return 0;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int ly = Math.floorMod(y, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        return data[(ly * CHUNK_SIZE + lz) * CHUNK_SIZE + lx];
    }

    public byte[] getChunkData(int cx, int cy, int cz) {
        return chunks.get(chunkKey(cx, cy, cz));
    }

    public boolean isTile(int x, int y, int z) {
        return getRawBlock(x, y, z) != 0;
    }

    public boolean isSolidTile(int x, int y, int z) {
        int id = getRawBlock(x, y, z) & 0xFF;
        if (id == 0) return false;
        if (id == WATER_ID) return false;
        return true;
    }

    public boolean isSolidForCulling(int x, int y, int z) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        byte[] data = chunks.get(chunkKey(cx, cy, cz));
        if (data == null) return true; // unloaded
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int ly = Math.floorMod(y, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        return data[(ly * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] != 0;
    }

    public boolean isSolidForCullingY(int x, int y, int z) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        byte[] data = chunks.get(chunkKey(cx, cy, cz));
        if (data == null) return false; // unloaded
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int ly = Math.floorMod(y, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        return data[(ly * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] != 0;
    }

    public boolean isOpaqueForCulling(int x, int y, int z) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        byte[] data = chunks.get(chunkKey(cx, cy, cz));
        if (data == null) return true; // unloaded
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int ly = Math.floorMod(y, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        return isOpaqueId(data[(ly * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] & 0xFF);
    }

    public boolean isOpaqueForCullingY(int x, int y, int z) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        byte[] data = chunks.get(chunkKey(cx, cy, cz));
        if (data == null) return false; // unloaded
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int ly = Math.floorMod(y, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        return isOpaqueId(data[(ly * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] & 0xFF);
    }

    public boolean isWater(int x, int y, int z) {
        return (getRawBlock(x, y, z) & 0xFF) == WATER_ID;
    }

    public static final int WATER_ID = 8;

    private static boolean isOpaqueId(int id) {
        if (id == 0) return false;
        if (id == WATER_ID) return false;
        return true;
    }

    public boolean isLightBlocker(int x, int y, int z) {
        return isSolidTile(x, y, z);
    }

    public float getBrightness(int x, int y, int z) {
        float dark  = 0.8F;
        float light = 1.0F;
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        int[] tops = heightMap.get(columnKey(cx, cz));
        if (tops == null) return light;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        int top = tops[lx + lz * CHUNK_SIZE];
        if (top == Integer.MIN_VALUE) return light;
        return (y < top) ? dark : light;
    }

    public void setTile(int x, int y, int z, int id) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        int cz = Math.floorDiv(z, CHUNK_SIZE);
        byte[] data = chunks.get(chunkKey(cx, cy, cz));
        if (data == null) return;
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int ly = Math.floorMod(y, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        data[(ly * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = (byte) id;

        int[] tops = heightMap.get(columnKey(cx, cz));
        int currentTop = (tops == null) ? Integer.MIN_VALUE : tops[lx + lz * CHUNK_SIZE];
        if (currentTop == Integer.MIN_VALUE || y >= currentTop) {
            recomputeColumnHeight(cx, cz);
        }

        for (LevelListener l : levelListeners) l.tileChanged(x, y, z);
    }

    public ArrayList<AABB> getCubes(AABB bb) {
        ArrayList<AABB> list = new ArrayList<>();

        int x0 = (int) Math.floor(bb.minX) - 1;
        int x1 = (int) Math.ceil(bb.maxX) + 1;
        int y0 = (int) Math.floor(bb.minY) - 1;
        int y1 = (int) Math.ceil(bb.maxY) + 1;
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

    public boolean hasAnyChunk() { return !chunks.isEmpty(); }

    public boolean hasChunk(int cx, int cy, int cz) {
        return chunks.containsKey(chunkKey(cx, cy, cz));
    }

    public boolean hasColumn(int cx, int cz) {
        Integer n = columnLoadCount.get(columnKey(cx, cz));
        return n != null && n > 0;
    }

    public boolean hasChunksInArea(double minX, double minZ, double maxX, double maxZ) {
        int cx0 = Math.floorDiv((int) Math.floor(minX), CHUNK_SIZE);
        int cx1 = Math.floorDiv((int) Math.floor(maxX), CHUNK_SIZE);
        int cz0 = Math.floorDiv((int) Math.floor(minZ), CHUNK_SIZE);
        int cz1 = Math.floorDiv((int) Math.floor(maxZ), CHUNK_SIZE);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!hasColumn(cx, cz)) return false;
            }
        }
        return true;
    }

    public void forEachLoadedChunk(ChunkConsumer action) {
        for (long key : chunks.keySet()) {
            action.accept(unpackCX(key), unpackCY(key), unpackCZ(key));
        }
    }

    public interface ChunkConsumer {
        void accept(int cx, int cy, int cz);
    }
}