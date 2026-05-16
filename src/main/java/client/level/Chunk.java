package client.level;

import client.Textures;
import client.phys.AABB;

import static org.lwjgl.opengl.GL11.*;

public class Chunk {

    private static final int TEXTURE = Textures.loadTexture("/client/textures/terrain.png", GL_NEAREST);
    private static final Tessellator TESSELLATOR = new Tessellator();

    public static int rebuiltThisFrame;
    public static int updates;

    private static final int MAX_FIRST_BUILDS = 4;

    private final Level level;

    public AABB boundingBox;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    private final int lists;
    private boolean dirty = true;

    private final boolean[] built = new boolean[2];

    public Chunk(Level level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.level = level;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.lists = glGenLists(2);
        this.boundingBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void compile(int layer) {
        updates++;
        this.dirty = false;

        glNewList(this.lists + layer, GL_COMPILE);
        glEnable(GL_TEXTURE_2D);
        Textures.bind(TEXTURE);
        TESSELLATOR.init();

        for (int x = this.minX; x < this.maxX; ++x) {
            for (int y = this.minY; y < this.maxY; ++y) {
                for (int z = this.minZ; z < this.maxZ; ++z) {
                    if (this.level.isTile(x, y, z)) {
                        int id = (y != this.level.depth * 2 / 3) ? 1 : 0;
                        if (id == 0) Tile.grass.render(TESSELLATOR, this.level, layer, x, y, z);
                        else Tile.rock.render(TESSELLATOR, this.level, layer, x, y, z);
                    }
                }
            }
        }

        TESSELLATOR.flush();
        glDisable(GL_TEXTURE_2D);
        glEndList();
        built[layer] = true;
    }

    public void render(int layer) {
        if (this.dirty) {
            boolean firstTime = !built[0];
            if (firstTime && rebuiltThisFrame >= MAX_FIRST_BUILDS) {
                return;
            }

            compile(0);
            compile(1);

            if (firstTime) {
                rebuiltThisFrame++;
            }
        }

        if (built[layer]) {
            glCallList(this.lists + layer);
        }
    }

    public void rebuildNow(int layer) {
        compile(layer);
    }

    public void setDirty() { this.dirty = true; }
}