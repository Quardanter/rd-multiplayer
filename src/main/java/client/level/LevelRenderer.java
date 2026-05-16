package client.level;

import client.*;
import client.net.PlayerManager;
import client.phys.AABB;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.lwjgl.opengl.GL11.*;

public class LevelRenderer implements LevelListener {
    private static final int CHUNK_SIZE = Level.CHUNK_SIZE;
    private static final int RENDER_CHUNK_HEIGHT = 16;
    private final Tessellator tessellator;
    private final Level level;
    private final ConcurrentHashMap<Long, Chunk> renderChunks = new ConcurrentHashMap<>();

    public LevelRenderer(Level level) {
        this.tessellator = new Tessellator();
        this.level = level;
        level.addListener(this);
    }
    private static long rcKey(int cx, int sliceY, int cz) {
        return ((long) cx << 40) | ((long) sliceY << 20) | (cz & 0xFFFFF);
    }

    private int sliceCount() {
        return Math.max(1, (level.depth + RENDER_CHUNK_HEIGHT - 1) / RENDER_CHUNK_HEIGHT);
    }

    private Chunk getOrCreateRenderChunk(int cx, int sliceY, int cz) {
        long key = rcKey(cx, sliceY, cz);
        return renderChunks.computeIfAbsent(key, k -> {
            int minX = cx * CHUNK_SIZE;
            int minY = sliceY * RENDER_CHUNK_HEIGHT;
            int minZ = cz * CHUNK_SIZE;
            int maxX = minX + CHUNK_SIZE;
            int maxY = Math.min(level.depth, minY + RENDER_CHUNK_HEIGHT);
            int maxZ = minZ + CHUNK_SIZE;
            return new Chunk(level, minX, minY, minZ, maxX, maxY, maxZ);
        });
    }

    @Override
    public void chunkLoaded(int cx, int cz) {
        int slices = sliceCount();
        for (int sy = 0; sy < slices; sy++) {
            Chunk rc = getOrCreateRenderChunk(cx, sy, cz);
            rc.setDirty();
        }
    }

    @Override
    public void chunkUnloaded(int cx, int cz) {
        int slices = sliceCount();
        for (int sy = 0; sy < slices; sy++) {
            renderChunks.remove(rcKey(cx, sy, cz));
        }
    }

    @Override
    public void lightColumnChanged(int x, int z, int minY, int maxY) {
        setDirty(x - 1, minY - 1, z - 1, x + 1, maxY + 1, z + 1);
    }

    @Override
    public void tileChanged(int x, int y, int z) {
        setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
    }

    @Override
    public void allChanged() {
        for (Chunk rc : renderChunks.values()) rc.setDirty();
    }

    public void setDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int minCX = Math.max(0, minX / CHUNK_SIZE);
        int minSY = Math.max(0, minY / RENDER_CHUNK_HEIGHT);
        int minCZ = Math.max(0, minZ / CHUNK_SIZE);
        int maxCX = maxX / CHUNK_SIZE;
        int maxSY = maxY / RENDER_CHUNK_HEIGHT;
        int maxCZ = maxZ / CHUNK_SIZE;

        for (long key : renderChunks.keySet()) {
            int cx = (int)(key >> 40);
            int sy = (int)((key >> 20) & 0xFFFFF);
            int cz = (int)(key & 0xFFFFF);
            if (cx >= minCX && cx <= maxCX && sy >= minSY && sy <= maxSY && cz >= minCZ && cz <= maxCZ) {
                Chunk rc = renderChunks.get(key);
                if (rc != null) rc.setDirty();
            }
        }
    }

    public void render(int layer) {
        Frustum frustum = Frustum.getFrustum();
        Chunk.rebuiltThisFrame = 0;

        for (Chunk rc : renderChunks.values()) {
            if (frustum.cubeInFrustum(rc.boundingBox)) {
                rc.render(layer);
            }
        }
    }

    public void rebuildAll() {
        Chunk.rebuiltThisFrame = 0;
        for (Chunk rc : renderChunks.values()) {
            rc.rebuildNow(0);
            rc.rebuildNow(1);
        }
    }

    public void pick(Player player) {
        float radius = 3.0F;
        AABB bb = player.boundingBox.grow(radius, radius, radius);

        int x0 = (int) bb.minX, x1 = (int)(bb.maxX + 1);
        int y0 = (int) bb.minY, y1 = (int)(bb.maxY + 1);
        int z0 = (int) bb.minZ, z1 = (int)(bb.maxZ + 1);

        glInitNames();
        for (int x = x0; x < x1; x++) {
            glPushName(x);
            for (int y = y0; y < y1; y++) {
                glPushName(y);
                for (int z = z0; z < z1; z++) {
                    glPushName(z);
                    if (level.isSolidTile(x, y, z)) {
                        glPushName(0);
                        for (int face = 0; face < 6; face++) {
                            glPushName(face);
                            tessellator.init();
                            Tile.rock.renderFace(tessellator, x, y, z, face);
                            tessellator.flush();
                            glPopName();
                        }
                        glPopName();
                    }
                    glPopName();
                }
                glPopName();
            }
            glPopName();
        }
    }

    public void renderHit(HitResult hitResult) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_CURRENT_BIT);
        glColor4f(1f, 1f, 1f, (float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2f + 0.4f);

        tessellator.init();
        Tile.rock.renderFace(tessellator, hitResult.x, hitResult.y, hitResult.z, hitResult.face);
        tessellator.flush();

        glDisable(GL_BLEND);
    }

    public void renderPlayers(PlayerManager playerManager) {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        for (Map.Entry<String, client.Position> entry : playerManager.getPlayers().entrySet()) {
            client.Position pos = entry.getValue();
            glPushMatrix();
            glTranslatef((float) pos.x, (float) pos.y - 1.62f, (float) pos.z);
            glRotatef(-pos.yaw, 0f, 1f, 0f);
            tessellator.init();
            renderPlayerModel();
            tessellator.flush();
            glPopMatrix();
        }

        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glEnable(GL_TEXTURE_2D);
    }

    private void renderPlayerModel() {
        renderBox(-0.25f, 1.25f, -0.25f,  0.25f, 1.75f,  0.25f,  0.85f, 0.65f, 0.50f);
        renderBox(-0.25f, 0.50f, -0.125f, 0.25f, 1.25f,  0.125f, 0.22f, 0.40f, 0.75f);
        renderBox( 0.00f, 0.00f, -0.125f, 0.25f, 0.50f,  0.125f, 0.15f, 0.25f, 0.55f);
        renderBox(-0.25f, 0.00f, -0.125f, 0.00f, 0.50f,  0.125f, 0.15f, 0.25f, 0.55f);
        renderBox( 0.25f, 0.50f, -0.125f, 0.50f, 1.25f,  0.125f, 0.85f, 0.65f, 0.50f);
        renderBox(-0.50f, 0.50f, -0.125f,-0.25f, 1.25f,  0.125f, 0.85f, 0.65f, 0.50f);
    }

    private void renderBox(float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b) {
        tessellator.color(r, g, b);
        tessellator.vertex(x0, y0, z0); tessellator.vertex(x1, y0, z0);
        tessellator.vertex(x1, y0, z1); tessellator.vertex(x0, y0, z1);
        tessellator.vertex(x0, y1, z0); tessellator.vertex(x1, y1, z0);
        tessellator.vertex(x1, y1, z1); tessellator.vertex(x0, y1, z1);
        tessellator.vertex(x0, y0, z0); tessellator.vertex(x1, y0, z0);
        tessellator.vertex(x1, y1, z0); tessellator.vertex(x0, y1, z0);
        tessellator.vertex(x0, y0, z1); tessellator.vertex(x1, y0, z1);
        tessellator.vertex(x1, y1, z1); tessellator.vertex(x0, y1, z1);
        tessellator.vertex(x0, y0, z0); tessellator.vertex(x0, y1, z0);
        tessellator.vertex(x0, y1, z1); tessellator.vertex(x0, y0, z1);
        tessellator.vertex(x1, y0, z0); tessellator.vertex(x1, y1, z0);
        tessellator.vertex(x1, y1, z1); tessellator.vertex(x1, y0, z1);
    }

    public void renderNameTags(PlayerManager playerManager, Player localPlayer, FontRenderer fontRenderer) {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        for (Map.Entry<String, client.Position> entry : playerManager.getPlayers().entrySet()) {
            String name = entry.getKey();
            client.Position pos = entry.getValue();

            glPushMatrix();
            glTranslated(pos.x, pos.y + 0.7D, pos.z);
            glRotatef(-localPlayer.yRotation, 0f, 1f, 0f);
            glRotatef(localPlayer.xRotation, 1f, 0f, 0f);

            float scale = 0.015F;
            glScalef(scale, -scale, scale);

            int tw = fontRenderer.getStringWidth(name);
            int th = fontRenderer.getStringHeight();
            int xo = -tw / 2;

            glDisable(GL_TEXTURE_2D);
            glColor4f(0f, 0f, 0f, 0.25f);
            glBegin(GL_QUADS);
            glVertex3f(xo - 2, -1, 0);  glVertex3f(xo + tw + 2, -1, 0);
            glVertex3f(xo + tw + 2, th + 1, 0); glVertex3f(xo - 2, th + 1, 0);
            glEnd();
            glEnable(GL_TEXTURE_2D);

            glColor4f(1f, 1f, 1f, 1f);
            fontRenderer.drawString(name, xo, 0, true);
            Textures.bind(0);
            glPopMatrix();
        }

        glDepthMask(true);
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glDisable(GL_BLEND);
    }
}