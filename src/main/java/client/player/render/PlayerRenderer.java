package client.player.render;

import client.FontRenderer;
import client.Minecraft;
import client.level.Tessellator;
import client.Textures;
import client.player.local.LocalPlayer;
import client.player.remote.PlayerManager;
import client.player.remote.RemotePlayer;
import client.world.WorldTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

public class PlayerRenderer {
    private static final float SWING_SPEED = 0.33f;
    private static final float SWING_AMP = 0.9f;
    private static final float SWING_EASE = 0.4f;
    private static final float MOVE_THRESHOLD = 0.05f;

    private final Tessellator tessellator;

    private final RemotePlayer selfRemotePlayer = new RemotePlayer(Minecraft.mc.username, 0, 0, 0f, 0,0);

    public PlayerRenderer(Tessellator tessellator) {
        this.tessellator = tessellator;
    }

    private static void tintColor(Tessellator t, float r, float g, float b) {
        float[] tint = WorldTime.ambientLight();
        final float floor = 0.12f;
        float tr = Math.max(tint[0], floor);
        float tg = Math.max(tint[1], floor);
        float tb = Math.max(tint[2], floor);
        t.color(r * tr, g * tg, b * tb);
    }


    public void renderPlayers(PlayerManager playerManager) {
        long now = System.currentTimeMillis();

        List<Map.Entry<String, RemotePlayer>> snapshot;
        synchronized (playerManager) {
            snapshot = new ArrayList<>(playerManager.getPlayers().entrySet());
        }

        beginPlayerRender();

        for (Map.Entry<String, RemotePlayer> entry : snapshot) {
            RemotePlayer pos = entry.getValue();
            uploadPendingSkin(pos);
            updateAnimation(pos, now);
            renderOnePlayer(pos.x, pos.y, pos.z, pos.yaw, pos.limbSwing, pos.limbSwingAmount,
                    pos.pitch, pos.skinTextureId);
        }

        endPlayerRender();
    }

    public void renderSelf(LocalPlayer p, PlayerManager playerManager) {
        selfRemotePlayer.x = p.x;
        selfRemotePlayer.y = p.y;
        selfRemotePlayer.z = p.z;
        selfRemotePlayer.yaw = p.yRotation;
        selfRemotePlayer.pitch = p.xRotation;

        RemotePlayer mine;
        synchronized (playerManager) {
            mine = playerManager.getPlayers().get(client.Minecraft.mc.username);
        }
        if (mine != null) {
            if (mine.pendingSkinPng != null) {
                selfRemotePlayer.pendingSkinPng = mine.pendingSkinPng;
                mine.pendingSkinPng = null;
            }
            if (mine.skinTextureId != -1) {
                selfRemotePlayer.skinTextureId = mine.skinTextureId;
            }
        }
        uploadPendingSkin(selfRemotePlayer);

        long now = System.currentTimeMillis();
        updateAnimation(selfRemotePlayer, now);

        beginPlayerRender();
        renderOnePlayer(p.x, p.y, p.z, p.yRotation, selfRemotePlayer.limbSwing, selfRemotePlayer.limbSwingAmount, selfRemotePlayer.pitch, selfRemotePlayer.skinTextureId);
        endPlayerRender();
    }

    public void renderNameTags(PlayerManager playerManager, LocalPlayer localPlayer,
                               FontRenderer fontRenderer) {
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);

        for (Map.Entry<String, RemotePlayer> entry : playerManager.getPlayers().entrySet()) {
            String name = entry.getKey();
            RemotePlayer pos = entry.getValue();

            glPushMatrix();
            glTranslated(pos.x, pos.y + 0.7D, pos.z);
            glRotatef(-localPlayer.yRotation, 0f, 1f, 0f);
            glRotatef( localPlayer.xRotation, 1f, 0f, 0f);

            float scale = 0.015F;
            if (Minecraft.mc.camera.mode == 2) {
                glScalef(-scale, -scale, scale);
            } else {
                glScalef(scale, -scale, scale);
            }

            int tw = fontRenderer.getStringWidth(name);
            int th = fontRenderer.getStringHeight();
            int xo = -tw / 2;

            glDisable(GL_TEXTURE_2D);
            glColor4f(0f, 0f, 0f, 0.25f);
            glBegin(GL_QUADS);
            glVertex3f(xo - 2,      -1,    0);
            glVertex3f(xo + tw + 2, -1,    0);
            glVertex3f(xo + tw + 2,  th + 1, 0);
            glVertex3f(xo - 2,       th + 1, 0);
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


    private void beginPlayerRender() {
        glDisable(GL_CULL_FACE);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void endPlayerRender() {
        Textures.bind(0);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glEnable(GL_TEXTURE_2D);
    }

    private void renderOnePlayer(double x, double y, double z, float yaw,
                                 float limbSwing, float limbSwingAmount,
                                 float pitch, int skinTextureId) {
        bindPlayerSkin(skinTextureId);
        glPushMatrix();
        glTranslatef((float) x, (float) y - 1.62f, (float) z);
        glRotatef(-yaw, 0f, 1f, 0f);
        renderPlayerModel(limbSwing, limbSwingAmount, pitch, skinTextureId);
        glPopMatrix();
    }


    private void uploadPendingSkin(RemotePlayer pos) {
        byte[] png = pos.pendingSkinPng;
        if (png == null) return;
        pos.pendingSkinPng = null;

        try {
            java.awt.image.BufferedImage img =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null) {
                System.err.println("Skin upload failed: ImageIO returned null");
                return;
            }

            int w = img.getWidth(), h = img.getHeight();
            if (!((w == 64 && h == 64) || (w == 64 && h == 32))) {
                System.err.println("Skin upload rejected: bad size " + w + "x" + h);
                return;
            }

            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);
            for (int i = 0; i < pixels.length; i++) {
                int a = (pixels[i] >> 24) & 0xFF;
                int r = (pixels[i] >> 16) & 0xFF;
                int g = (pixels[i] >>  8) & 0xFF;
                int b =  pixels[i]        & 0xFF;
                pixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }

            java.nio.ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
            buf.asIntBuffer().put(pixels);

            int id = (pos.skinTextureId != -1) ? pos.skinTextureId : glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            pos.skinTextureId = id;

        } catch (Exception e) {
            System.err.println("Skin decode/upload failed: " + e.getMessage());
        }
    }


    private static void updateAnimation(RemotePlayer pos, long now) {
        if (pos.lastAnimTime == 0) {
            pos.lastAnimTime = now;
            pos.prevAnimX = pos.x;
            pos.prevAnimZ = pos.z;
            return;
        }

        double dx    = pos.x - pos.prevAnimX;
        double dz    = pos.z - pos.prevAnimZ;
        double moved = Math.sqrt(dx * dx + dz * dz);

        float target = (moved > MOVE_THRESHOLD) ? 1f : 0f;
        pos.limbSwingAmount += (target - pos.limbSwingAmount) * SWING_EASE;
        pos.limbSwing       += SWING_SPEED * pos.limbSwingAmount;

        pos.prevAnimX    = pos.x;
        pos.prevAnimZ    = pos.z;
        pos.lastAnimTime = now;
    }


    private void bindPlayerSkin(int skinTextureId) {
        if (skinTextureId != -1) {
            glEnable(GL_TEXTURE_2D);
            Textures.bind(skinTextureId);
        } else {
            glDisable(GL_TEXTURE_2D);
        }
    }

    private void renderPlayerModel(float limbSwing, float limbSwingAmount,
                                   float pitch, int skin) {
        float swing    = (float) Math.sin(limbSwing) * SWING_AMP * limbSwingAmount;
        float swingDeg = (float) Math.toDegrees(swing);
        boolean textured = skin != -1;

        tessellator.init();
        if (textured) {
            renderSkinBox(-0.25f, 0.60f, -0.125f,  0.25f, 1.35f, 0.125f, SkinModel.BODY);
        } else {
            renderBox   (-0.25f, 0.60f, -0.125f,  0.25f, 1.35f, 0.125f, 0.22f, 0.40f, 0.75f);
        }
        tessellator.flush();

        if (textured) {
            glDepthMask(false);
            tessellator.init();
            renderSkinBoxScaled(-0.25f, 0.60f, -0.125f, 0.25f, 1.35f, 0.125f,
                   SkinModel.BODY_OUTER, 0.03125f);
            tessellator.flush();
            glDepthMask(true);
        }

        renderHead(textured ? skin : -1, pitch);

        renderLimbBox( 0.00f, 0.00f, -0.125f,  0.25f, 0.60f, 0.125f,
                textured ? SkinModel.R_LEG : null,
                textured ? SkinModel.R_LEG_OUTER : null,
                0.15f, 0.25f, 0.55f, 0.60f,  swingDeg);

        renderLimbBox(-0.25f, 0.00f, -0.125f,  0.00f, 0.60f, 0.125f,
                textured ? SkinModel.L_LEG : null,
                textured ? SkinModel.L_LEG_OUTER : null,
                0.15f, 0.25f, 0.55f, 0.60f, -swingDeg);

        renderLimbBox( 0.25f, 0.60f, -0.125f,  0.4375f, 1.35f, 0.125f,
                textured ? SkinModel.R_ARM       : null,
                textured ? SkinModel.R_ARM_OUTER : null,
                0.85f, 0.65f, 0.50f, 1.35f, -swingDeg);

        renderLimbBox(-0.4375f, 0.60f, -0.125f, -0.25f, 1.35f, 0.125f,
                textured ? SkinModel.L_ARM       : null,
                textured ? SkinModel.L_ARM_OUTER : null,
                0.85f, 0.65f, 0.50f, 1.35f,  swingDeg);
    }

    private void renderHead(int skin, float pitch) {
        float pivotY = 1.35f;
        float p = Math.max(-90f, Math.min(90f, pitch));

        glPushMatrix();
        glTranslatef(0f, pivotY, 0f);
        glRotatef(-p, 1f, 0f, 0f);
        glTranslatef(0f, -pivotY, 0f);

        tessellator.init();
        if (skin != -1) {
            renderSkinBox(-0.25f, 1.35f, -0.25f, 0.25f, 1.85f, 0.25f, SkinModel.HEAD);
        } else {
            renderBox   (-0.25f, 1.35f, -0.25f, 0.25f, 1.85f, 0.25f, 0.85f, 0.65f, 0.50f);
            renderPlayerFace();
        }
        tessellator.flush();

        if (skin != -1) {
            glDepthMask(false);
            tessellator.init();
            renderSkinBoxScaled(-0.25f, 1.35f, -0.25f, 0.25f, 1.85f, 0.25f,
                    SkinModel.HEAD_HAT, 0.0625f);
            tessellator.flush();
            glDepthMask(true);
        }

        glPopMatrix();
    }

    private void renderLimbBox(float x0, float y0, float z0,
                               float x1, float y1, float z1,
                               float[][] uvBase, float[][] uvOuter,
                               float r, float g, float b,
                               float pivotY, float angleDeg) {
        glPushMatrix();
        glTranslatef(0f, pivotY, 0f);
        glRotatef(angleDeg, 1f, 0f, 0f);
        glTranslatef(0f, -pivotY, 0f);

        tessellator.init();
        if (uvBase != null) {
            renderSkinBox(x0, y0, z0, x1, y1, z1, uvBase);
        } else {
            renderBox(x0, y0, z0, x1, y1, z1, r, g, b);
        }
        tessellator.flush();

        if (uvOuter != null) {
            glDepthMask(false);
            tessellator.init();
            renderSkinBoxScaled(x0, y0, z0, x1, y1, z1, uvOuter, 0.03125f);
            tessellator.flush();
            glDepthMask(true);
        }

        glPopMatrix();
    }

    private void renderPlayerFace() {
        float z = -0.251f;
        renderFaceQuad(-0.15f, 1.72f, -0.03f, 1.65f, z, 1.0f,  1.0f,  1.0f);
        renderFaceQuad( 0.03f, 1.72f,  0.15f, 1.65f, z, 1.0f,  1.0f,  1.0f);
        renderFaceQuad(-0.13f, 1.70f, -0.07f, 1.66f, z, 0.08f, 0.08f, 0.08f);
        renderFaceQuad( 0.07f, 1.70f,  0.13f, 1.66f, z, 0.08f, 0.08f, 0.08f);
        renderFaceQuad(-0.10f, 1.47f,  0.10f, 1.44f, z, 0.25f, 0.08f, 0.08f);
    }

    private void renderFaceQuad(float x0, float y0, float x1, float y1, float z, float r, float g, float b) {
        tintColor(tessellator, r, g, b);
        tessellator.vertex(x0, y0, z);
        tessellator.vertex(x1, y0, z);
        tessellator.vertex(x1, y1, z);
        tessellator.vertex(x0, y1, z);
    }

    private void renderBox(float x0, float y0, float z0,
                           float x1, float y1, float z1,
                           float r, float g, float b) {
        tintColor(tessellator, r, g, b);
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

    private void renderSkinBox(float x0, float y0, float z0, float x1, float y1, float z1, float[][] uv) {
        tintColor(tessellator, 1f, 1f, 1f);

        float u0 = uv[0][0], v0 = uv[0][1], u1 = uv[0][2], v1 = uv[0][3];
        tessellator.texture(u0, v0); tessellator.vertex(x0, y0, z0);
        tessellator.texture(u1, v0); tessellator.vertex(x1, y0, z0);
        tessellator.texture(u1, v1); tessellator.vertex(x1, y0, z1);
        tessellator.texture(u0, v1); tessellator.vertex(x0, y0, z1);

        u0 = uv[1][0]; v0 = uv[1][1]; u1 = uv[1][2]; v1 = uv[1][3];
        tessellator.texture(u0, v1); tessellator.vertex(x0, y1, z0);
        tessellator.texture(u1, v1); tessellator.vertex(x1, y1, z0);
        tessellator.texture(u1, v0); tessellator.vertex(x1, y1, z1);
        tessellator.texture(u0, v0); tessellator.vertex(x0, y1, z1);

        u0 = uv[2][0]; v0 = uv[2][1]; u1 = uv[2][2]; v1 = uv[2][3];
        tessellator.texture(u1, v1); tessellator.vertex(x0, y0, z0);
        tessellator.texture(u0, v1); tessellator.vertex(x1, y0, z0);
        tessellator.texture(u0, v0); tessellator.vertex(x1, y1, z0);
        tessellator.texture(u1, v0); tessellator.vertex(x0, y1, z0);

        u0 = uv[3][0]; v0 = uv[3][1]; u1 = uv[3][2]; v1 = uv[3][3];
        tessellator.texture(u0, v1); tessellator.vertex(x0, y0, z1);
        tessellator.texture(u1, v1); tessellator.vertex(x1, y0, z1);
        tessellator.texture(u1, v0); tessellator.vertex(x1, y1, z1);
        tessellator.texture(u0, v0); tessellator.vertex(x0, y1, z1);

        u0 = uv[4][0]; v0 = uv[4][1]; u1 = uv[4][2]; v1 = uv[4][3];
        tessellator.texture(u0, v1); tessellator.vertex(x0, y0, z0);
        tessellator.texture(u0, v0); tessellator.vertex(x0, y1, z0);
        tessellator.texture(u1, v0); tessellator.vertex(x0, y1, z1);
        tessellator.texture(u1, v1); tessellator.vertex(x0, y0, z1);

        u0 = uv[5][0]; v0 = uv[5][1]; u1 = uv[5][2]; v1 = uv[5][3];
        tessellator.texture(u1, v1); tessellator.vertex(x1, y0, z0);
        tessellator.texture(u1, v0); tessellator.vertex(x1, y1, z0);
        tessellator.texture(u0, v0); tessellator.vertex(x1, y1, z1);
        tessellator.texture(u0, v1); tessellator.vertex(x1, y0, z1);
    }

    private void renderSkinBoxScaled(float x0, float y0, float z0, float x1, float y1, float z1, float[][] uv, float grow) {
        renderSkinBox(x0 - grow, y0 - grow, z0 - grow, x1 + grow, y1 + grow, z1 + grow, uv);
    }
}