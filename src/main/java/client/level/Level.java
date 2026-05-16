package client.level;

import client.phys.AABB;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Level {
    public static final int CHUNK_SIZE = 16;

    public int width;
    public int height;
    public int depth;

    private final ConcurrentHashMap<Long, byte[]> chunks = new ConcurrentHashMap<>();
    private int[] lightDepths = new int[0];
    private final ArrayList<LevelListener> levelListeners = new ArrayList<>();


    public Level(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.lightDepths = new int[width * height];
    }

    public void loadLevel(int w, int h, int d, byte[] flatBlocks) {
        this.width = w;
        this.height = h;
        this.depth = d;
        this.lightDepths = new int[w * h];
        chunks.clear();

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
                chunks.put(chunkKey(cx, cz), chunkData);
            }
        }

        calcLightDepths(0, 0, w, h);

        for (LevelListener l : levelListeners) l.allChanged();
    }


    public void loadChunk(int cx, int cz, int chunkDepth, byte[] data) {
        if (this.depth == 0) this.depth = chunkDepth;

        chunks.put(chunkKey(cx, cz), data);
        int baseX = cx * CHUNK_SIZE;
        int baseZ = cz * CHUNK_SIZE;
        calcLightDepths(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE);

        for (LevelListener l : levelListeners) {
            l.lightColumnChanged(baseX, baseZ, 0, depth);
        }

        for (LevelListener l : levelListeners) {
            l.chunkLoaded(cx, cz);
        }
    }

    public void unloadChunk(int cx, int cz) {
        chunks.remove(chunkKey(cx, cz));

        for (LevelListener l : levelListeners) {
            l.chunkUnloaded(cx, cz);
        }
    }

    private void calcLightDepths(int startX, int startZ, int rangeX, int rangeZ) {
        if (lightDepths.length < width * height) {
            lightDepths = new int[width * height];
        }
        for (int x = startX; x < startX + rangeX && x < width; x++) {
            for (int z = startZ; z < startZ + rangeZ && z < height; z++) {
                int prevDepth = lightDepths[x + z * width];

                int d = this.depth - 1;
                while (d > 0 && !isLightBlocker(x, d, z)) d--;

                lightDepths[x + z * width] = d;

                if (prevDepth != d) {
                    int lo = Math.min(prevDepth, d);
                    int hi = Math.max(prevDepth, d);
                    for (LevelListener l : levelListeners) {
                        l.lightColumnChanged(x, z, lo, hi);
                    }
                }
            }
        }
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public byte getRawBlock(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return 0;
        int cx = x / CHUNK_SIZE;
        int cz = z / CHUNK_SIZE;
        byte[] data = chunks.get(chunkKey(cx, cz));
        if (data == null) return 0;
        int lx = x % CHUNK_SIZE;
        int lz = z % CHUNK_SIZE;
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
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return light;
        if (lightDepths.length > x + z * width && y < lightDepths[x + z * width]) return dark;
        return light;
    }

    public void setTile(int x, int y, int z, int id) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= depth || z >= height) return;
        int cx = x / CHUNK_SIZE;
        int cz = z / CHUNK_SIZE;
        byte[] data = chunks.get(chunkKey(cx, cz));
        if (data == null) return;
        int lx = x % CHUNK_SIZE;
        int lz = z % CHUNK_SIZE;
        data[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = (byte) id;

        calcLightDepths(x, z, 1, 1);
        for (LevelListener l : levelListeners) l.tileChanged(x, y, z);
    }


    // physics
    public ArrayList<AABB> getCubes(AABB bb) {
        ArrayList<AABB> list = new ArrayList<>();

        int x0 = Math.max(0, (int)(Math.floor(bb.minX) - 1));
        int x1 = Math.min(width, (int)(Math.ceil(bb.maxX) + 1));
        int y0 = Math.max(0, (int)(Math.floor(bb.minY) - 1));
        int y1 = Math.min(depth, (int)(Math.ceil(bb.maxY) + 1));
        int z0 = Math.max(0, (int)(Math.floor(bb.minZ) - 1));
        int z1 = Math.min(height,(int)(Math.ceil(bb.maxZ) + 1));

        for (int x = x0; x < x1; x++)
            for (int y = y0; y < y1; y++)
                for (int z = z0; z < z1; z++)
                    if (isSolidTile(x, y, z))
                        list.add(new AABB(x, y, z, x+1, y+1, z+1));

        return list;
    }

    public void addListener(LevelListener l) { levelListeners.add(l); }
    public byte[] getBlocks() {
        return new byte[0];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }

    public boolean hasAnyChunk() { return !chunks.isEmpty(); }
}