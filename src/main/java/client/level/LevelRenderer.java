package client.level;

import client.*;
import client.net.PlayerManager;
import client.phys.AABB;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;

public class LevelRenderer implements LevelListener {

    private static final int CHUNK_SIZE = Level.CHUNK_SIZE;
    private static final int RENDER_CHUNK_HEIGHT = 16;

    private final Tessellator tessellator;
    private final Level level;

    private final ConcurrentHashMap<Long, Chunk> renderChunks = new ConcurrentHashMap<>();

    private final Set<Long> pendingLoad   = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingUnload = ConcurrentHashMap.newKeySet();

    public LevelRenderer(Level level) {
        this.tessellator = new Tessellator();
        this.level = level;
        level.addListener(this);
    }

    private static long rcKey(int cx, int sliceY, int cz) {
        return ((long)(cx & 0xFFFFL) << 32) | ((long)(sliceY & 0xFFFFL) << 16) | (cz & 0xFFFFL);
    }

    private static int rcCX(long key) { return (short)((key >> 32) & 0xFFFFL); }
    private static int rcSY(long key) { return (int)  ((key >> 16) & 0xFFFFL); }
    private static int rcCZ(long key) { return (short)( key        & 0xFFFFL); }

    private static long colKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
    private static int colCX(long key) { return (int)(key >> 32); }
    private static int colCZ(long key) { return (int) key; }

    private int sliceCount() {
        return Math.max(1, (level.depth + RENDER_CHUNK_HEIGHT - 1) / RENDER_CHUNK_HEIGHT);
    }

    @Override
    public void chunkLoaded(int cx, int cz) {
        pendingLoad.add(colKey(cx, cz));
    }

    @Override
    public void chunkUnloaded(int cx, int cz) {
        pendingUnload.add(colKey(cx, cz));
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

    private void applyPendingChunks() {
        for (long key : pendingUnload) {
            int cx = colCX(key);
            int cz = colCZ(key);
            int slices = sliceCount();
            for (int sy = 0; sy < slices; sy++) {
                renderChunks.remove(rcKey(cx, sy, cz));
            }
        }
        pendingUnload.clear();

        for (long key : pendingLoad) {
            int cx = colCX(key);
            int cz = colCZ(key);
            createRenderChunks(cx, cz);
        }
        pendingLoad.clear();
    }

    private void createRenderChunks(int cx, int cz) {
        int slices = sliceCount();
        for (int sy = 0; sy < slices; sy++) {
            long key = rcKey(cx, sy, cz);
            Chunk rc = renderChunks.get(key);
            if (rc == null) {
                int minX = cx * CHUNK_SIZE;
                int minY = sy * RENDER_CHUNK_HEIGHT;
                int minZ = cz * CHUNK_SIZE;
                int maxX = minX + CHUNK_SIZE;
                int maxY = Math.min(level.depth, minY + RENDER_CHUNK_HEIGHT);
                int maxZ = minZ + CHUNK_SIZE;
                rc = new Chunk(level, minX, minY, minZ, maxX, maxY, maxZ);
                renderChunks.put(key, rc);
            }
            rc.setDirty();
        }
    }

    public void setDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int minCX = Math.floorDiv(minX, CHUNK_SIZE);
        int minSY = Math.max(0, Math.floorDiv(minY, RENDER_CHUNK_HEIGHT));
        int minCZ = Math.floorDiv(minZ, CHUNK_SIZE);
        int maxCX = Math.floorDiv(maxX, CHUNK_SIZE);
        int maxSY = Math.floorDiv(maxY, RENDER_CHUNK_HEIGHT);
        int maxCZ = Math.floorDiv(maxZ, CHUNK_SIZE);

        for (Map.Entry<Long, Chunk> e : renderChunks.entrySet()) {
            long k  = e.getKey();
            int  cx = rcCX(k);
            int  sy = rcSY(k);
            int  cz = rcCZ(k);
            if (cx >= minCX && cx <= maxCX && sy >= minSY && sy <= maxSY && cz >= minCZ && cz <= maxCZ) {
                e.getValue().setDirty();
            }
        }
    }

    public void render(int layer) {
        if (layer == 0) {
            applyPendingChunks();
            Chunk.rebuiltThisFrame = 0;
        }

        Frustum frustum = Frustum.getFrustum();
        for (Chunk rc : renderChunks.values()) {
            if (frustum.cubeInFrustum(rc.boundingBox)) {
                rc.render(layer);
            }
        }
    }

    public void rebuildAll() {
        applyPendingChunks();
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
                        int blockId = level.getRawBlock(x, y, z) & 0xFF;
                        Tile tile = Blocks.get(blockId);
                        if (tile != null) {
                            glPushName(0);
                            for (int face = 0; face < 6; face++) {
                                glPushName(face);
                                tessellator.init();
                                tile.renderFace(tessellator, x, y, z, face);
                                tessellator.flush();
                                glPopName();
                            }
                            glPopName();
                        }
                    }
                    glPopName();
                }
                glPopName();
            }
            glPopName();
        }
    }

    public void renderHit(HitResult hitResult) {
        int blockId = level.getRawBlock(hitResult.x, hitResult.y, hitResult.z) & 0xFF;
        Tile tile = Blocks.get(blockId);
        if (tile == null) return;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_CURRENT_BIT);
        glColor4f(1f, 1f, 1f,
                (float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2f + 0.4f);
        tessellator.init();
        tile.renderFace(tessellator, hitResult.x, hitResult.y, hitResult.z, hitResult.face);
        tessellator.flush();
        glDisable(GL_BLEND);
    }

    public void renderPlayers(PlayerManager playerManager) {
        glDisable(GL_TEXTURE_2D); glDisable(GL_CULL_FACE); glDisable(GL_FOG);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        long now = System.currentTimeMillis();

        for (Map.Entry<String, client.Position> entry : playerManager.getPlayers().entrySet()) {
            client.Position pos = entry.getValue();
            updateAnimation(pos, now);

            glPushMatrix();
            glTranslatef((float) pos.x, (float) pos.y - 1.62f, (float) pos.z);
            glRotatef(-pos.yaw, 0f, 1f, 0f);
            renderPlayerModel(pos.limbSwing, pos.limbSwingAmount);
            glPopMatrix();
        }

        glDisable(GL_BLEND); glEnable(GL_CULL_FACE); glEnable(GL_FOG); glEnable(GL_TEXTURE_2D);
    }

    /** Animation state for the local player, kept across frames. */
    private final client.Position selfPosition = new client.Position(0, 0, 0, 0f, 0);

    /**
     * Render the local player. Called by {@link client.Minecraft} only when the
     * camera is in 3rd / 2nd person — in 1st person the model would clip into
     * the camera.
     */
    public void renderSelf(Player p) {
        // Mirror live player data into the persistent animation Position.
        selfPosition.x = p.x;
        selfPosition.y = p.y;
        selfPosition.z = p.z;
        selfPosition.yaw = p.yRotation;

        long now = System.currentTimeMillis();
        updateAnimation(selfPosition, now);

        glDisable(GL_TEXTURE_2D); glDisable(GL_CULL_FACE); glDisable(GL_FOG);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glPushMatrix();
        glTranslatef((float) p.x, (float) p.y - 1.62f, (float) p.z);
        glRotatef(-p.yRotation, 0f, 1f, 0f);
        renderPlayerModel(selfPosition.limbSwing, selfPosition.limbSwingAmount);
        glPopMatrix();

        glDisable(GL_BLEND); glEnable(GL_CULL_FACE); glEnable(GL_FOG); glEnable(GL_TEXTURE_2D);
    }

    /** Walk-cycle constants — tune to taste. */
    private static final float SWING_SPEED    = 0.33f;    // phase advance per frame at full motion
    private static final float SWING_AMP      = 0.9f;     // peak swing in radians (~52°)
    private static final float SWING_EASE     = 0.4f;     // how fast amount eases toward target
    private static final float MOVE_THRESHOLD = 0.05f;    // blocks moved per frame to count as "moving"

    private static void updateAnimation(client.Position pos, long now) {
        // First frame for this player — initialise and bail.
        if (pos.lastAnimTime == 0) {
            pos.lastAnimTime = now;
            pos.prevAnimX = pos.x;
            pos.prevAnimZ = pos.z;
            return;
        }

        double dx = pos.x - pos.prevAnimX;
        double dz = pos.z - pos.prevAnimZ;
        double moved = Math.sqrt(dx * dx + dz * dz);

        // Ease limbSwingAmount toward 1.0 if moving, 0.0 if still.
        float target = (moved > MOVE_THRESHOLD) ? 1f : 0f;
        pos.limbSwingAmount += (target - pos.limbSwingAmount) * SWING_EASE;

        // Advance the swing phase proportional to how strongly limbs are swinging.
        pos.limbSwing += SWING_SPEED * pos.limbSwingAmount;

        pos.prevAnimX = pos.x;
        pos.prevAnimZ = pos.z;
        pos.lastAnimTime = now;
    }

    private void renderPlayerModel(float limbSwing, float limbSwingAmount) {
        // Static parts (head + face details + body) share the player's base
        // transform, so they all go in one batched tessellator call.
        tessellator.init();
        renderBox(-0.25f, 1.25f, -0.25f,  0.25f, 1.75f,  0.25f,  0.85f, 0.65f, 0.50f); // head
        renderBox(-0.25f, 0.50f, -0.125f, 0.25f, 1.25f,  0.125f, 0.22f, 0.40f, 0.75f); // body
        renderPlayerFace();
        tessellator.flush();

        float swing = (float) Math.sin(limbSwing) * SWING_AMP * limbSwingAmount;
        float swingDeg = (float) Math.toDegrees(swing);

        // Right leg — pivots at hip (top of leg, y = 0.50).
        renderLimb( 0.00f, 0.00f, -0.125f, 0.25f, 0.50f, 0.125f,
                    0.15f, 0.25f, 0.55f,  0.50f,  swingDeg);

        // Left leg — opposite phase.
        renderLimb(-0.25f, 0.00f, -0.125f, 0.00f, 0.50f, 0.125f,
                    0.15f, 0.25f, 0.55f,  0.50f, -swingDeg);

        // Right arm — pivots at shoulder (top of arm, y = 1.25).
        renderLimb( 0.25f, 0.50f, -0.125f, 0.50f, 1.25f, 0.125f,
                    0.85f, 0.65f, 0.50f,  1.25f, -swingDeg);

        // Left arm — same phase as right leg.
        renderLimb(-0.50f, 0.50f, -0.125f,-0.25f, 1.25f, 0.125f,
                    0.85f, 0.65f, 0.50f,  1.25f,  swingDeg);
    }

    /**
     * Render a box rotated around the X axis at {@code pivotY}.
     * Each limb is its own tessellator batch because the matrix differs per limb.
     */
    private void renderLimb(float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float r,  float g,  float b,
                            float pivotY, float angleDeg) {
        glPushMatrix();
        glTranslatef(0f, pivotY, 0f);
        glRotatef(angleDeg, 1f, 0f, 0f);
        glTranslatef(0f, -pivotY, 0f);

        tessellator.init();
        renderBox(x0, y0, z0, x1, y1, z1, r, g, b);
        tessellator.flush();

        glPopMatrix();
    }

    private void renderPlayerFace() {
        float z = -0.251f;

        renderFaceQuad(-0.15f, 1.62f, -0.03f, 1.55f, z,  1.0f, 1.0f, 1.0f);
        renderFaceQuad( 0.03f, 1.62f,  0.15f, 1.55f, z,  1.0f, 1.0f, 1.0f);
        renderFaceQuad(-0.13f, 1.60f, -0.07f, 1.56f, z,  0.08f, 0.08f, 0.08f);
        renderFaceQuad( 0.07f, 1.60f,  0.13f, 1.56f, z,  0.08f, 0.08f, 0.08f);
        renderFaceQuad(-0.10f, 1.37f,  0.10f, 1.34f, z,  0.25f, 0.08f, 0.08f);
    }

    private void renderFaceQuad(float x0, float y0, float x1, float y1, float z,
                                float r, float g, float b) {
        tessellator.color(r, g, b);
        tessellator.vertex(x0, y0, z);
        tessellator.vertex(x1, y0, z);
        tessellator.vertex(x1, y1, z);
        tessellator.vertex(x0, y1, z);
    }

    private void renderBox(float x0, float y0, float z0, float x1, float y1, float z1,
                           float r, float g, float b) {
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
        glEnable(GL_DEPTH_TEST); glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D); glDisable(GL_FOG);
        glDepthMask(false); glDisable(GL_CULL_FACE);

        for (Map.Entry<String, client.Position> entry : playerManager.getPlayers().entrySet()) {
            String name = entry.getKey();
            client.Position pos = entry.getValue();
            glPushMatrix();
            glTranslated(pos.x, pos.y + 0.7D, pos.z);
            glRotatef(-localPlayer.yRotation, 0f, 1f, 0f);
            glRotatef(localPlayer.xRotation,  1f, 0f, 0f);
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

        glDepthMask(true); glEnable(GL_CULL_FACE); glEnable(GL_FOG); glDisable(GL_BLEND);
    }
}