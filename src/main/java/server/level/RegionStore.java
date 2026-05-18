package server.level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RegionStore {
    private static final int CACHE_LIMIT = 64;
    private final Path chunkDir;
    private final LinkedHashMap<Long, RegionFile> regions =
            new LinkedHashMap<Long, RegionFile>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, RegionFile> eldest) {
                    if (size() <= CACHE_LIMIT) return false;
                    eldest.getValue().close();
                    return true;
                }
            };

    public RegionStore(Path chunkDir) {
        this.chunkDir = chunkDir;
    }

    private static long regionKey(int rx, int ry, int rz) {
        return ((long)(rx & 0x1FFFFF) << 42) | ((long)(ry & 0x1FFFFF) << 21) |  (long)(rz & 0x1FFFFF);
    }

    private Path regionPath(int rx, int ry, int rz) {
        return chunkDir.resolve("r." + rx + "." + ry + "." + rz + ".mcr");
    }

    private Path legacyPath(int cx, int cy, int cz) {
        return chunkDir.resolve("c_" + cx + "_" + cy + "_" + cz + ".dat");
    }

    private Path legacy2dPath(int cx, int cz) {
        return chunkDir.resolve("c_" + cx + "_" + cz + ".dat");
    }

    private static final int LEGACY_2D_DEPTH = 64;
    private static final int LEGACY_2D_CHUNKS_TALL = LEGACY_2D_DEPTH / LevelChunk.CHUNK_SIZE;

    private synchronized RegionFile region(int rx, int ry, int rz) throws IOException {
        long key = regionKey(rx, ry, rz);
        RegionFile rf = regions.get(key);
        if (rf == null) {
            rf = new RegionFile(regionPath(rx, ry, rz));
            regions.put(key, rf);
        }
        return rf;
    }

    public byte[] read(int cx, int cy, int cz) {
        int rx = cx >> RegionFile.REGION_BITS;
        int ry = cy >> RegionFile.REGION_BITS;
        int rz = cz >> RegionFile.REGION_BITS;
        int lx = cx & (RegionFile.REGION_SIZE - 1);
        int ly = cy & (RegionFile.REGION_SIZE - 1);
        int lz = cz & (RegionFile.REGION_SIZE - 1);

        try {
            RegionFile rf;
            synchronized (this) {
                rf = regions.get(regionKey(rx, ry, rz));
            }
            if (rf == null && !Files.exists(regionPath(rx, ry, rz))) {
                byte[] migrated = tryMigrate(cx, cy, cz);
                return migrated;
            }

            rf = region(rx, ry, rz);
            byte[] data = rf.read(lx, ly, lz);
            if (data != null) return data;

            return tryMigrate(cx, cy, cz);
        } catch (IOException e) {
            System.err.println("RegionStore.read failed for (" + cx + ", " + cy + ", " + cz + "): " + e.getMessage());
            return null;
        }
    }

    private byte[] tryMigrate(int cx, int cy, int cz) throws IOException {
        Path cubic = legacyPath(cx, cy, cz);
        if (Files.exists(cubic)) {
            byte[] migrated = readLegacy(cubic);
            if (migrated != null) {
                write(cx, cy, cz, migrated);
                try { Files.delete(cubic); } catch (IOException ignored) {}
                return migrated;
            }
        }

        Path twoD = legacy2dPath(cx, cz);
        if (Files.exists(twoD)) {
            byte[] column = readLegacy2D(twoD);
            if (column != null) {
                byte[] requested = null;
                for (int icy = 0; icy < LEGACY_2D_CHUNKS_TALL; icy++) {
                    byte[] slice = new byte[LevelChunk.VOLUME];
                    System.arraycopy(column, icy * LevelChunk.VOLUME, slice, 0, LevelChunk.VOLUME);
                    write(cx, icy, cz, slice);
                    if (icy == cy) requested = slice;
                }
                try { Files.delete(twoD); } catch (IOException ignored) {}
                return requested;
            }
        }

        return null;
    }

    private byte[] readLegacy(Path legacy) {
        try (java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.util.zip.GZIPInputStream(Files.newInputStream(legacy)))) {
            byte[] data = new byte[LevelChunk.VOLUME];
            dis.readFully(data);
            return data;
        } catch (IOException e) {
            System.err.println("Failed to migrate legacy chunk " + legacy + ": " + e.getMessage());
            return null;
        }
    }

    private byte[] readLegacy2D(Path legacy) {
        int expected = LEGACY_2D_DEPTH * LevelChunk.CHUNK_SIZE * LevelChunk.CHUNK_SIZE;
        try (java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.util.zip.GZIPInputStream(Files.newInputStream(legacy)))) {
            byte[] data = new byte[expected];
            dis.readFully(data);
            return data;
        } catch (IOException e) {
            System.err.println("Failed to migrate legacy 2D chunk " + legacy + ": " + e.getMessage());
            return null;
        }
    }

    /** Write a chunk's bytes to disk */
    public void write(int cx, int cy, int cz, byte[] data) {
        int rx = cx >> RegionFile.REGION_BITS;
        int ry = cy >> RegionFile.REGION_BITS;
        int rz = cz >> RegionFile.REGION_BITS;
        int lx = cx & (RegionFile.REGION_SIZE - 1);
        int ly = cy & (RegionFile.REGION_SIZE - 1);
        int lz = cz & (RegionFile.REGION_SIZE - 1);
        try {
            region(rx, ry, rz).write(lx, ly, lz, data);
        } catch (IOException e) {
            System.err.println("RegionStore.write failed for (" + cx + ", " + cy + ", " + cz + "): " + e.getMessage());
        }
    }

    // close all the regions
    public synchronized void closeAll() {
        for (RegionFile rf : regions.values()) rf.close();
        regions.clear();
    }
}