package client.level;

import client.*;
import client.gfx.ChunkShader;
import client.gfx.GL;
import client.level.block.BlockRegistry;
import client.level.block.Block;
import client.player.remote.PlayerManager;
import client.player.local.LocalPlayer;
import client.player.render.PlayerRenderer;
import client.world.WorldTime;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LevelRenderer implements LevelListener {
    private static final int CHUNK_SIZE = Level.CHUNK_SIZE;
    private final Set<Long> tntPositions = ConcurrentHashMap.newKeySet();

    private final Tessellator tessellator;
    private final Level level;
    private final PlayerRenderer playerRenderer;

    private final ConcurrentHashMap<Long, Chunk> renderChunks = new ConcurrentHashMap<>();

    private final Set<Long> pendingLoad   = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingUnload = ConcurrentHashMap.newKeySet();
 
    private final java.util.ArrayList<Chunk> sortedRender = new java.util.ArrayList<>();

    public LevelRenderer(Level level) {
        this.tessellator = new Tessellator();
        this.level = level;
        this.playerRenderer = new PlayerRenderer(tessellator);
        level.addListener(this);
    }

    private static long rcKey(int cx, int cy, int cz) {
        return ((long)(cx & 0x1FFFFF) << 42)
             | ((long)(cy & 0x1FFFFF) << 21)
             |  (long)(cz & 0x1FFFFF);
    }
    private static int signExtend21(long v) {
        long m = v & 0x1FFFFF;
        return (int)((m & 0x100000L) != 0 ? m | ~0x1FFFFFL : m);
    }
    private static int rcCX(long k) { return signExtend21((k >> 42) & 0x1FFFFF); }
    private static int rcCY(long k) { return signExtend21((k >> 21) & 0x1FFFFF); }
    private static int rcCZ(long k) { return signExtend21( k & 0x1FFFFF); }

    @Override
    public void chunkLoaded(int cx, int cy, int cz) {
        pendingLoad.add(rcKey(cx, cy, cz));
    }

    @Override
    public void chunkUnloaded(int cx, int cy, int cz) {
        pendingUnload.add(rcKey(cx, cy, cz));
    }

    @Override
    public void lightColumnChanged(int x, int z, int minY, int maxY) {
        setDirty(x - 1, minY - 1, z - 1, x + 1, maxY + 1, z + 1);
    }

    @Override
    public void tileChanged(int x, int y, int z) {
        setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);

        long tntKey = packTnt(x, y, z);
        if ((level.getRawBlock(x, y, z) & 0xFF) == BlockRegistry.TNT.id) {
            tntPositions.add(tntKey);
        } else {
            tntPositions.remove(tntKey);
        }
    }

    @Override
    public void allChanged() {
        for (Chunk rc : renderChunks.values()) rc.setDirty();
    }

    private int[] playerChunkCoords() {
        LocalPlayer p = (Minecraft.mc == null) ? null : Minecraft.mc.localPlayer;
        if (p == null) return null;
        int pcx = Math.floorDiv((int) Math.floor(p.x), CHUNK_SIZE);
        int pcy = Math.floorDiv((int) Math.floor(p.y), CHUNK_SIZE);
        int pcz = Math.floorDiv((int) Math.floor(p.z), CHUNK_SIZE);
        return new int[]{ pcx, pcy, pcz };
    }

    private static long chunkDistSq(int cx, int cy, int cz, int[] pc) {
        long dx = cx - pc[0], dy = cy - pc[1], dz = cz - pc[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private void applyPendingChunks() {
        for (java.util.Iterator<Long> it = pendingUnload.iterator(); it.hasNext(); ) {
            long key = it.next();
            it.remove();
            Chunk rc = renderChunks.remove(key);
            if (rc != null) rc.dispose();
            int cx = rcCX(key), cy = rcCY(key), cz = rcCZ(key);
            markNeighborsDirty(cx, cy, cz);
        }

        if (pendingLoad.isEmpty()) return;
        int[] pc = playerChunkCoords();
        java.util.ArrayList<Long> keys = new java.util.ArrayList<>(pendingLoad);
        pendingLoad.clear();
        if (pc != null) {
            final int[] pcf = pc;
            keys.sort((a, b) -> Long.compare(
                chunkDistSq(rcCX(a), rcCY(a), rcCZ(a), pcf),
                chunkDistSq(rcCX(b), rcCY(b), rcCZ(b), pcf)));
        }
        for (long key : keys) {
            int cx = rcCX(key), cy = rcCY(key), cz = rcCZ(key);
            createRenderChunk(cx, cy, cz);
            markNeighborsDirty(cx, cy, cz);
        }
    }

    private void markNeighborsDirty(int cx, int cy, int cz) {
        markIfPresent(cx - 1, cy, cz);
        markIfPresent(cx + 1, cy, cz);
        markIfPresent(cx, cy - 1, cz);
        markIfPresent(cx, cy + 1, cz);
        markIfPresent(cx, cy, cz - 1);
        markIfPresent(cx, cy, cz + 1);
    }

    private void markIfPresent(int cx, int cy, int cz) {
        Chunk rc = renderChunks.get(rcKey(cx, cy, cz));
        if (rc != null) rc.setDirty();
    }

    private void createRenderChunk(int cx, int cy, int cz) {
        long key = rcKey(cx, cy, cz);
        Chunk rc = renderChunks.get(key);
        if (rc == null) {
            rc = new Chunk(level, cx, cy, cz);
            renderChunks.put(key, rc);

            byte[] data = level.getChunkData(cx, cy, cz);
            if (data != null) {
                int tntId = BlockRegistry.TNT.id;
                int baseX = cx * CHUNK_SIZE;
                int baseY = cy * CHUNK_SIZE;
                int baseZ = cz * CHUNK_SIZE;
                int idx = 0;
                for (int ly = 0; ly < CHUNK_SIZE; ly++) {
                    for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
                            if ((data[idx] & 0xFF) == tntId) {
                                tntPositions.add(packTnt(baseX + lx, baseY + ly, baseZ + lz));
                            }
                            idx++;
                        }
                    }
                }
            }
        }
        rc.setDirty();
    }

    public void setDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int minCX = Math.floorDiv(minX, CHUNK_SIZE);
        int minCY = Math.floorDiv(minY, CHUNK_SIZE);
        int minCZ = Math.floorDiv(minZ, CHUNK_SIZE);
        int maxCX = Math.floorDiv(maxX, CHUNK_SIZE);
        int maxCY = Math.floorDiv(maxY, CHUNK_SIZE);
        int maxCZ = Math.floorDiv(maxZ, CHUNK_SIZE);

        for (Map.Entry<Long, Chunk> e : renderChunks.entrySet()) {
            long k = e.getKey();
            int cx = rcCX(k), cy = rcCY(k), cz = rcCZ(k);
            if (cx >= minCX && cx <= maxCX
                    && cy >= minCY && cy <= maxCY
                    && cz >= minCZ && cz <= maxCZ) {
                e.getValue().setDirty();
            }
        }
    }

    public void render(int layer) {
        if (layer == 0) {
            applyPendingChunks();
            Chunk.rebuiltThisFrame = 0;

            sortedRender.clear();
            sortedRender.addAll(renderChunks.values());
            int[] pc = playerChunkCoords();
            if (pc != null) {
                final int[] pcf = pc;
                sortedRender.sort((a, b) -> Long.compare(
                    chunkDistSq(a.chunkX, a.chunkY, a.chunkZ, pcf),
                    chunkDistSq(b.chunkX, b.chunkY, b.chunkZ, pcf)));
            }
        }

        beginChunkPass();
        try {
            Frustum frustum = Frustum.getFrustum();
            if (layer == 1) {
                for (int i = sortedRender.size() - 1; i >= 0; i--) {
                    Chunk rc = sortedRender.get(i);
                    if (frustum.cubeInFrustum(rc.boundingBox)) rc.render(layer);
                }
            } else {
                for (Chunk rc : sortedRender) {
                    if (frustum.cubeInFrustum(rc.boundingBox)) rc.render(layer);
                }
            }
        } finally {
            endChunkPass();
        }
    }

    private void beginChunkPass() {
        ChunkShader shader = ChunkShader.get();
        shader.use();

        float[] sun = WorldTime.sunDirection();
        float[] ambient = WorldTime.ambientLight();
        float[] diffuse = WorldTime.diffuseLight();
        float gamma = Settings.gammaMultiplier();

        shader.setSunDir(sun[0], sun[1], sun[2]);
        shader.setSunColor(diffuse[0], diffuse[1], diffuse[2]);
        shader.setAmbient(ambient[0], ambient[1], ambient[2]);
        shader.setGamma(gamma);

        GL.activeTexture(GL.TEXTURE0);
        GL.enable(GL.TEXTURE_2D);
        Textures.bind(Chunk.TEXTURE);

        GL.enableVertexAttribArray(ChunkShader.ATTR_POS);
        GL.enableVertexAttribArray(ChunkShader.ATTR_NORMAL);
        GL.enableVertexAttribArray(ChunkShader.ATTR_COLOR);
        GL.enableVertexAttribArray(ChunkShader.ATTR_UV);
    }

    private void endChunkPass() {
        GL.disableVertexAttribArray(ChunkShader.ATTR_UV);
        GL.disableVertexAttribArray(ChunkShader.ATTR_COLOR);
        GL.disableVertexAttribArray(ChunkShader.ATTR_NORMAL);
        GL.disableVertexAttribArray(ChunkShader.ATTR_POS);
        GL.bindBuffer(GL.ARRAY_BUFFER, 0);
        GL.disable(GL.TEXTURE_2D);
        ChunkShader.get().unuse();
    }

    public void rebuildAll() {
        applyPendingChunks();
        for (Chunk rc : renderChunks.values()) {
            rc.rebuildNow(0);
            rc.rebuildNow(1);
        }
    }

    public HitResult pick(LocalPlayer localPlayer) {
        float reach = 3.0F;
        float step = 0.05F;

        double eyeX = localPlayer.x, eyeY = localPlayer.y, eyeZ = localPlayer.z;

        double yawRad = Math.toRadians(localPlayer.yRotation);
        double pitchRad = Math.toRadians(localPlayer.xRotation);
        double dx = Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = -Math.cos(yawRad) * Math.cos(pitchRad);

        int prevBX = Integer.MIN_VALUE, prevBY = Integer.MIN_VALUE, prevBZ = Integer.MIN_VALUE;

        int steps = (int) Math.ceil(reach / step);
        for (int i = 1; i <= steps; i++) {
            double t = i * step;
            int bx = (int) Math.floor(eyeX + dx * t);
            int by = (int) Math.floor(eyeY + dy * t);
            int bz = (int) Math.floor(eyeZ + dz * t);

            if (bx == prevBX && by == prevBY && bz == prevBZ) continue;

            if (level.isSolidTile(bx, by, bz)) {
                int face = entryFace(prevBX, prevBY, prevBZ, bx, by, bz, dx, dy, dz);
                int type = level.getRawBlock(bx, by, bz) & 0xFF;
                return new HitResult(bx, by, bz, type, face);
            }

            prevBX = bx; prevBY = by; prevBZ = bz;
        }
        return null;
    }

    private static int entryFace(int prevBX, int prevBY, int prevBZ,int bx, int by, int bz,double dx, double dy, double dz) {
        if (prevBX != Integer.MIN_VALUE) {
            if (bx != prevBX) return bx > prevBX ? 4 : 5;
            if (by != prevBY) return by > prevBY ? 0 : 1;
            if (bz != prevBZ) return bz > prevBZ ? 2 : 3;
        }
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx > 0 ? 4 : 5;
        if (ay >= ax && ay >= az) return dy > 0 ? 0 : 1;
        return dz > 0 ? 2 : 3;
    }

    public void renderHit(HitResult hitResult) {
        int blockId = level.getRawBlock(hitResult.x, hitResult.y, hitResult.z) & 0xFF;
        Block block = BlockRegistry.get(blockId);
        if (block == null) return;

        GL.enable(GL.BLEND);
        GL.blendFunc(GL.SRC_ALPHA, GL.CURRENT_BIT);
        GL.color4f(1f, 1f, 1f,(float) Math.sin(System.currentTimeMillis() / 100.0) * 0.2f + 0.4f);
        tessellator.init();
        block.renderFace(tessellator, hitResult.x, hitResult.y, hitResult.z, hitResult.face);
        tessellator.flush();
        GL.disable(GL.BLEND);
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

    private static long packTnt(int x, int y, int z) {
        return ((long)(x & 0x1FFFFF) << 42)
             | ((long)(y & 0x1FFFFF) << 21)
             |  (long)(z & 0x1FFFFF);
    }
    private static int unpackTntX(long k) { return signExtend21((k >> 42) & 0x1FFFFF); }
    private static int unpackTntY(long k) { return signExtend21((k >> 21) & 0x1FFFFF); }
    private static int unpackTntZ(long k) { return signExtend21(k & 0x1FFFFF); }

    public void renderTntOverlay() {
        if (tntPositions.isEmpty()) return;
        float alpha = (float)(Math.sin(System.currentTimeMillis() / 150.0) * 0.5 + 0.5) * 0.8f;

        GL.enable(GL.BLEND);
        GL.blendFunc(GL.SRC_ALPHA, GL.ONE_MINUS_SRC_ALPHA);
        GL.disable(GL.TEXTURE_2D);
        GL.disable(GL.LIGHTING);
        GL.color4f(1f, 1f, 1f, alpha);

        Frustum frustum = Frustum.getFrustum();

        for (long key : tntPositions) {
            int x = unpackTntX(key);
            int y = unpackTntY(key);
            int z = unpackTntZ(key);

            if (!frustum.cubeInFrustum(x, y, z, x + 1, y + 1, z + 1)) continue;

            float x0 = x, x1 = x + 1;
            float y0 = y, y1 = y + 1;
            float z0 = z, z1 = z + 1;

            GL.begin(GL.QUADS);
            if (!level.isSolidTile(x, y - 1, z)) {
                GL.vertex3f(x0,y0,z1); GL.vertex3f(x0,y0,z0); GL.vertex3f(x1,y0,z0); GL.vertex3f(x1,y0,z1);
            }
            if (!level.isSolidTile(x, y + 1, z)) {
                GL.vertex3f(x1,y1,z1); GL.vertex3f(x1,y1,z0); GL.vertex3f(x0,y1,z0); GL.vertex3f(x0,y1,z1);
            }
            if (!level.isSolidTile(x, y, z - 1)) {
                GL.vertex3f(x0,y1,z0); GL.vertex3f(x1,y1,z0); GL.vertex3f(x1,y0,z0); GL.vertex3f(x0,y0,z0);
            }
            if (!level.isSolidTile(x, y, z + 1)) {
                GL.vertex3f(x0,y1,z1); GL.vertex3f(x0,y0,z1); GL.vertex3f(x1,y0,z1); GL.vertex3f(x1,y1,z1);
            }
            if (!level.isSolidTile(x - 1, y, z)) {
                GL.vertex3f(x0,y1,z1); GL.vertex3f(x0,y1,z0); GL.vertex3f(x0,y0,z0); GL.vertex3f(x0,y0,z1);
            }
            if (!level.isSolidTile(x + 1, y, z)) {
                GL.vertex3f(x1,y0,z1); GL.vertex3f(x1,y0,z0); GL.vertex3f(x1,y1,z0); GL.vertex3f(x1,y1,z1);
            }
            GL.end();
        }

        GL.enable(GL.TEXTURE_2D);
        GL.enable(GL.LIGHTING);
        GL.disable(GL.BLEND);
        GL.color4f(1f, 1f, 1f, 1f);
    }
}
