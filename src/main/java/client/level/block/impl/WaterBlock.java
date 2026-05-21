package client.level.block.impl;

import client.level.FaceTextures;
import client.level.Level;
import client.level.VertexSink;
import client.level.block.Block;

public final class WaterBlock extends Block {

    private static final float MIN_V = 0f;
    private static final float MAX_V = 16f / 256f;

    public WaterBlock(int id, String name, int atlasCol) {
        super(id, name, FaceTextures.uniform(atlasCol));
    }

    public WaterBlock(int id, String name, FaceTextures faces) {
        super(id, name, faces);
    }

    @Override public void onPlace(int x, int y, int z) {}
    @Override public void onBreak(int x, int y, int z) {}

    private static float minU(int col) { return col / 16f; }
    private static float maxU(int col) { return col / 16f + 16f / 256f; }

    private static boolean hidesWaterFace(Level level, int nx, int ny, int nz) {
        int id = level.getRawBlock(nx, ny, nz) & 0xFF;
        if (id == 0) return false; // air
        if (id == Level.WATER_ID) return true; // water
        return true; // opaque
    }

    @Override
    public void render(VertexSink t, Level level, int layer, int x, int y, int z) {
        if (layer != 0) return;

        float x0 = x, x1 = x + 1;
        float y0 = y, y1 = y + 1;
        float z0 = z, z1 = z + 1;

        // BOTTOM (-Y)
        if (!hidesWaterFace(level, x, y - 1, z)) {
            emitFace(t, level, x, y - 1, z, 1.0f, 0f, -1f, 0f, faces.col(FaceTextures.BOTTOM),
                    x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1,
                    /*u order*/ true);
        }
        // TOP (+Y)
        if (!hidesWaterFace(level, x, y + 1, z)) {
            emitFace(t, level, x, y + 1, z, 1.0f, 0f, 1f, 0f, faces.col(FaceTextures.TOP),
                    x1, y1, z1, x1, y1, z0, x0, y1, z0, x0, y1, z1,
                    /*u order*/ false);
        }
        // NORTH (-Z)
        if (!hidesWaterFace(level, x, y, z - 1)) {
            emitFace(t, level, x, y, z - 1, 0.8f, 0f, 0f, -1f, faces.col(FaceTextures.NORTH),
                    x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0,
                    /*u order*/ false);
        }
        // SOUTH (+Z)
        if (!hidesWaterFace(level, x, y, z + 1)) {
            emitFace(t, level, x, y, z + 1, 0.8f, 0f, 0f, 1f, faces.col(FaceTextures.SOUTH),
                    x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1,
                    /*u order*/ true);
        }
        // WEST (-X)
        if (!hidesWaterFace(level, x - 1, y, z)) {
            emitFace(t, level, x - 1, y, z, 0.6f, -1f, 0f, 0f, faces.col(FaceTextures.WEST),
                    x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1,
                    /*u order*/ false);
        }
        // EAST (+X)
        if (!hidesWaterFace(level, x + 1, y, z)) {
            emitFace(t, level, x + 1, y, z, 0.6f, 1f, 0f, 0f, faces.col(FaceTextures.EAST),
                    x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                    /*u order*/ true);
        }
    }

    private void emitFace(VertexSink t, Level level,
                          int nx, int ny, int nz,
                          float shade, float normX, float normY, float normZ,
                          int col,
                          float ax, float ay, float az,
                          float bx, float by, float bz,
                          float cx, float cy, float cz,
                          float dx, float dy, float dz,
                          boolean uOrder) {
        float u0 = minU(col), u1 = maxU(col);
        float b = level.getBrightness(nx, ny, nz) * shade;

        t.normal(normX, normY, normZ);
        t.color(b, b, b);
        if (uOrder) {
            t.texture(u0, MAX_V); t.vertex(ax, ay, az);
            t.texture(u0, MIN_V); t.vertex(bx, by, bz);
            t.texture(u1, MIN_V); t.vertex(cx, cy, cz);
            t.texture(u1, MAX_V); t.vertex(dx, dy, dz);
        } else {
            t.texture(u1, MIN_V); t.vertex(ax, ay, az);
            t.texture(u0, MIN_V); t.vertex(bx, by, bz);
            t.texture(u0, MAX_V); t.vertex(cx, cy, cz);
            t.texture(u1, MAX_V); t.vertex(dx, dy, dz);
        }
    }
}