package client.level;

import client.*;
import client.level.block.BlockRegistry;
import client.level.block.Block;
import client.player.remote.PlayerManager;
import client.phys.AABB;
import client.player.local.LocalPlayer;
import client.player.render.PlayerRenderer;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.*;

public class LevelRenderer implements LevelListener {
    private static final int CHUNK_SIZE = Level.CHUNK_SIZE;
    private static final int RENDER_CHUNK_HEIGHT = 16;

    private final Tessellator tessellator;
    private final Level level;
    private final PlayerRenderer playerRenderer;

    private final ConcurrentHashMap<Long, Chunk> renderChunks = new ConcurrentHashMap<>();

    private final Set<Long> pendingLoad = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingUnload = ConcurrentHashMap.newKeySet();

    public LevelRenderer(Level level) {
        this.tessellator = new Tessellator();
        this.level = level;
        this.playerRenderer = new PlayerRenderer(tessellator);
        level.addListener(this);
    }

    private static long rcKey(int cx, int sliceY, int cz) {
        return ((long)(cx & 0xFFFFL) << 32)
                | ((long)(sliceY & 0xFFFFL) << 16)
                | (cz & 0xFFFFL);
    }
    private static int rcCX(long key) { return (short)((key >> 32) & 0xFFFFL); }
    private static int rcSY(long key) { return (int)((key >> 16) & 0xFFFFL); }
    private static int rcCZ(long key) { return (short)(key & 0xFFFFL); }

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
            int cx = colCX(key), cz = colCZ(key);
            for (int sy = 0, slices = sliceCount(); sy < slices; sy++) {
                renderChunks.remove(rcKey(cx, sy, cz));
            }
        }
        pendingUnload.clear();

        for (long key : pendingLoad) {
            createRenderChunks(colCX(key), colCZ(key));
        }
        pendingLoad.clear();
    }

    private void createRenderChunks(int cx, int cz) {
        int slices = sliceCount();
        for (int sy = 0; sy < slices; sy++) {
            long key = rcKey(cx, sy, cz);
            Chunk rc = renderChunks.get(key);
            if (rc == null) {
                int minX = cx * CHUNK_SIZE, minY = sy * RENDER_CHUNK_HEIGHT;
                int minZ = cz * CHUNK_SIZE;
                int maxX = minX + CHUNK_SIZE, maxY = Math.min(level.depth, minY + RENDER_CHUNK_HEIGHT);
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
            long k = e.getKey();
            int cx = rcCX(k), sy = rcSY(k), cz = rcCZ(k);
            if (cx >= minCX && cx <= maxCX
                    && sy >= minSY && sy <= maxSY
                    && cz >= minCZ && cz <= maxCZ) {
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

    public void pick(LocalPlayer localPlayer) {
        float radius = 3.0F;
        AABB bb = localPlayer.boundingBox.grow(radius, radius, radius);

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
                        Block block = BlockRegistry.get(blockId);
                        if (block != null) {
                            glPushName(0);
                            for (int face = 0; face < 6; face++) {
                                glPushName(face);
                                tessellator.init();
                                block.renderFace(tessellator, x, y, z, face);
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
        Block block = BlockRegistry.get(blockId);
        if (block == null) return;

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_CURRENT_BIT);
        glColor4f(1f, 1f, 1f,
                (float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2f + 0.4f);
        tessellator.init();
        block.renderFace(tessellator, hitResult.x, hitResult.y, hitResult.z, hitResult.face);
        tessellator.flush();
        glDisable(GL_BLEND);
    }

    public void renderPlayers(PlayerManager playerManager) {
        playerRenderer.renderPlayers(playerManager);
    }

    public void renderSelf(LocalPlayer p, PlayerManager playerManager) {
        playerRenderer.renderSelf(p, playerManager);
    }

    public void renderNameTags(PlayerManager playerManager, LocalPlayer localPlayer, FontRenderer fontRenderer) {
        playerRenderer.renderNameTags(playerManager, localPlayer, fontRenderer);
    }
}