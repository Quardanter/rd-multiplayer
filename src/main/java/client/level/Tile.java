package client.level;

public class Tile {
    public final int id;
    public final int atlasCol;
    public final String name;

    private static final float MIN_V = 0f;
    private static final float MAX_V = 16 / 256f;

    public Tile(int id, String name) {
        this.id = id;
        this.atlasCol = id-1;
        this.name = name;
    }

    public float minU() {return atlasCol / 16f;}

    public float maxU() {return minU() + 16 / 256f;}

    public void render(Tessellator t, Level level, int layer, int x, int y, int z) {

        float minU = minU(), maxU = maxU();

        float x0 = x,     x1 = x + 1;
        float y0 = y,     y1 = y + 1;
        float z0 = z,     z1 = z + 1;

        face(t, level, layer, x, y - 1, z, 1f, 0,
                x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1,
                minU,MAX_V, minU,MIN_V, maxU,MIN_V, maxU,MAX_V);

        face(t, level, layer, x, y + 1, z, 1f, 1,
                x1,y1,z1, x1,y1,z0, x0,y1,z0, x0,y1,z1,
                maxU,MAX_V, maxU,MIN_V, minU,MIN_V, minU,MAX_V);

        face(t, level, layer, x, y, z - 1, .8f, 2,
                x0,y1,z0, x1,y1,z0, x1,y0,z0, x0,y0,z0,
                maxU,MIN_V, minU,MIN_V, minU,MAX_V, maxU,MAX_V);

        face(t, level, layer, x, y, z + 1, .8f, 3,
                x0,y1,z1, x0,y0,z1, x1,y0,z1, x1,y1,z1,
                minU,MIN_V, minU,MAX_V, maxU,MAX_V, maxU,MIN_V);

        face(t, level, layer, x - 1, y, z, .6f, 4,
                x0,y1,z1, x0,y1,z0, x0,y0,z0, x0,y0,z1,
                maxU,MIN_V, minU,MIN_V, minU,MAX_V, maxU,MAX_V);

        face(t, level, layer, x + 1, y, z, .6f, 5,
                x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1,
                minU,MAX_V, maxU,MAX_V, maxU,MIN_V, minU,MIN_V);
    }

    private void face(
            Tessellator t, Level level, int layer,
            int x, int y, int z,
            float shade, int face,

            float x0,float y0,float z0,
            float x1,float y1,float z1,
            float x2,float y2,float z2,
            float x3,float y3,float z3,

            float u0,float v0,
            float u1,float v1,
            float u2,float v2,
            float u3,float v3
    ) {

        if (level.isSolidTile(x, y, z)) return;

        float b = level.getBrightness(x, y, z) * shade;

        if (!(layer == 1 ^ b == shade)) return;

        t.color(b, b, b);

        t.texture(u0, v0); t.vertex(x0, y0, z0);
        t.texture(u1, v1); t.vertex(x1, y1, z1);
        t.texture(u2, v2); t.vertex(x2, y2, z2);
        t.texture(u3, v3); t.vertex(x3, y3, z3);
    }

    public void renderFace(Tessellator t, int x, int y, int z, int face) {

        float x0 = x,     x1 = x + 1;
        float y0 = y,     y1 = y + 1;
        float z0 = z,     z1 = z + 1;

        switch (face) {
            case 0: t.vertex(x0,y0,z1); t.vertex(x0,y0,z0); t.vertex(x1,y0,z0); t.vertex(x1,y0,z1); break;
            case 1: t.vertex(x1,y1,z1); t.vertex(x1,y1,z0); t.vertex(x0,y1,z0); t.vertex(x0,y1,z1); break;
            case 2: t.vertex(x0,y1,z0); t.vertex(x1,y1,z0); t.vertex(x1,y0,z0); t.vertex(x0,y0,z0); break;
            case 3: t.vertex(x0,y1,z1); t.vertex(x0,y0,z1); t.vertex(x1,y0,z1); t.vertex(x1,y1,z1); break;
            case 4: t.vertex(x0,y1,z1); t.vertex(x0,y1,z0); t.vertex(x0,y0,z0); t.vertex(x0,y0,z1); break;
            case 5: t.vertex(x1,y0,z1); t.vertex(x1,y0,z0); t.vertex(x1,y1,z0); t.vertex(x1,y1,z1); break;
        }
    }
}