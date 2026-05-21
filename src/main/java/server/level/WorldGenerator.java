package server.level;

/**
 * Stateless world generator. Given a cubic chunk's (cx, cy, cz), fills its
 * block array.
 *
 * The surface lives roughly in the range [5, 60] world-Y. Below that, solid
 * cobblestone (with a one-block bedrock-like floor far below). Above, air.
 * Chunks that lie entirely above the maximum possible surface are left as
 * pure air and the generator skips block-by-block work.
 */
public final class WorldGenerator {

    /** Hard floor of the world. Below this Y we put one layer of "bedrock". */
    public static final int WORLD_FLOOR = 0;
    /** Minimum surface Y. */
    public static final int MIN_SURFACE = 5;
    /** Maximum surface Y. */
    public static final int MAX_SURFACE = 60;
    /** Water fills air blocks between (surfaceY, SEA_LEVEL] inclusive of SEA_LEVEL. */
    public static final int SEA_LEVEL = 12;
    /** Block id for water — must match BlockRegistry.WATER / Level.WATER_ID on the client. */
    private static final byte WATER_ID = 8;

    private WorldGenerator() {}

    public static void generate(LevelChunk chunk) {
        int chunkMinY = chunk.chunkY * LevelChunk.CHUNK_SIZE;
        int chunkMaxY = chunkMinY + LevelChunk.CHUNK_SIZE - 1;

        // Trivial-reject: chunk entirely above max surface → leave as air.
        if (chunkMinY > MAX_SURFACE) {
            chunk.onGenerated();
            return;
        }

        if (chunkMaxY < MIN_SURFACE) {
            fillSolid(chunk, chunkMinY);
            chunk.onGenerated();
            return;
        }

        int worldOffsetX = chunk.chunkX * LevelChunk.CHUNK_SIZE;
        int worldOffsetZ = chunk.chunkZ * LevelChunk.CHUNK_SIZE;

        for (int lz = 0; lz < LevelChunk.CHUNK_SIZE; lz++) {
            for (int lx = 0; lx < LevelChunk.CHUNK_SIZE; lx++) {
                double wx = worldOffsetX + lx;
                double wz = worldOffsetZ + lz;

                boolean isDesert = biomeIsDesert(wx, wz);
                int surfaceY = surfaceY(wx, wz, isDesert);

                for (int ly = 0; ly < LevelChunk.CHUNK_SIZE; ly++) {
                    int wy = chunkMinY + ly;
                    chunk.blocks[blockIndex(lx, ly, lz)] = blockAt(wy, surfaceY, isDesert);
                }
            }
        }
        chunk.onGenerated();
    }

    private static int blockIndex(int lx, int ly, int lz) {
        return (ly * LevelChunk.CHUNK_SIZE + lz) * LevelChunk.CHUNK_SIZE + lx;
    }

    private static void fillSolid(LevelChunk chunk, int chunkMinY) {
        for (int ly = 0; ly < LevelChunk.CHUNK_SIZE; ly++) {
            int wy = chunkMinY + ly;
            byte id = (wy == WORLD_FLOOR) ? (byte) 2 : (byte) 2; // cobble; could be obsidian
            for (int lz = 0; lz < LevelChunk.CHUNK_SIZE; lz++) {
                for (int lx = 0; lx < LevelChunk.CHUNK_SIZE; lx++) {
                    chunk.blocks[blockIndex(lx, ly, lz)] = id;
                }
            }
        }
    }

    public static int surfaceY(double wx, double wz, boolean isDesert) {
        double continent = noise(wx * 0.003, wz * 0.003);
        double ridge = Math.abs(noise(wx * 0.015, wz * 0.015));
        double hills = noise(wx * 0.01,  wz * 0.01)
                     + noise(wx * 0.02,  wz * 0.02)  * 0.5
                     + noise(wx * 0.04,  wz * 0.04)  * 0.25
                     + noise(wx * 0.08,  wz * 0.08)  * 0.125;
        hills /= 1.875;

        double combined = isDesert
                ? continent * 0.55 + ridge * 0.1 + hills * 0.35
                : continent * 0.45 + ridge * 0.3 + hills * 0.25;

        int y = MIN_SURFACE + (int) ((combined + 0.6) / 1.4 * (MAX_SURFACE - MIN_SURFACE));
        if (y < MIN_SURFACE) y = MIN_SURFACE;
        if (y > MAX_SURFACE) y = MAX_SURFACE;
        return y;
    }

    public static boolean biomeIsDesert(double wx, double wz) {
        double biome = noise(wx * 0.0005 + 1000, wz * 0.0005 + 1000) + noise(wx * 0.001  + 1000, wz * 0.001  + 1000) * 0.5;
        biome /= 1.5;
        return biome > 0.05;
    }

    private static byte blockAt(int wy, int surfaceY, boolean isDesert) {
        if (wy == WORLD_FLOOR) return 2;
        if (wy < WORLD_FLOOR) return 0;
        if (wy > surfaceY) {
            // Above terrain but at or below sea level → fill with water.
            // Above sea level → air.
            return (wy <= SEA_LEVEL) ? WATER_ID : 0;
        }

        if (isDesert) {
            if (wy >= surfaceY - 5) return 5; // sand
            return 2; // cobble
        } else {
            // Submerged terrain (top is below sea level): dirt instead of
            // grass on the surface, since grass underwater looks wrong.
            if (wy == surfaceY) return (surfaceY < SEA_LEVEL) ? (byte) 3 : (byte) 1;
            if (wy >= surfaceY - 3) return 3; // dirt
            return 2; // cobble
        }
    }

    private static final int[] PERM = new int[512];
    static {
        int[] p = {151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,
                140,36,103,30,69,142,8,99,37,240,21,10,23,190,6,148,
                247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,
                57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,
                74,165,71,134,139,48,27,166,77,146,158,231,83,111,229,122,
                60,211,133,230,220,105,92,41,55,46,245,40,244,102,143,54,
                65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,
                200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,
                52,217,226,250,124,123,5,202,38,147,118,126,255,82,85,212,
                207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,
                119,248,152,2,44,154,163,70,221,153,101,155,167,43,172,9,
                129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,
                218,246,97,228,251,34,242,193,238,210,144,12,191,179,162,241,
                81,51,145,235,249,14,239,107,49,192,214,31,181,199,106,157,
                184,84,204,176,115,121,50,45,127,4,150,254,138,236,205,93,
                222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180};
        for (int i = 0; i < 256; i++) { PERM[i] = p[i]; PERM[i + 256] = p[i]; }
    }

    private static double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);
        int aa = PERM[PERM[xi]     + yi];
        int ab = PERM[PERM[xi]     + yi + 1];
        int ba = PERM[PERM[xi + 1] + yi];
        int bb = PERM[PERM[xi + 1] + yi + 1];
        return lerp(v, lerp(u, grad(aa, xf,     yf),
                               grad(ba, xf - 1, yf)),
                       lerp(u, grad(ab, xf,     yf - 1),
                               grad(bb, xf - 1, yf - 1)));
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
    private static double grad(int hash, double x, double y) {
        int h = hash & 3;
        double u = h < 2 ? x : y;
        double v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}