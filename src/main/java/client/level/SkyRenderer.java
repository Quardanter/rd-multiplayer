package client.level;

import client.Minecraft;
import client.Textures;
import client.world.WorldTime;
import org.lwjgl.BufferUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public final class SkyRenderer {
    private static final float SKY_RADIUS = 100f;
    private static final float SUN_SIZE  = 15f;
    private static final float MOON_SIZE = 10f;
    private static final int MOON_COLS = 4;
    private static final int MOON_ROWS = 2;
    private static final int MOON_PHASES = MOON_COLS * MOON_ROWS;

    private int sunTexture  = -1;
    private int moonTexture = -1;

    private long cyclesElapsedSinceStart = 0;
    private float lastFraction = -1f;

    public void init() {
        sunTexture = loadTexture("/client/textures/sun.png");
        moonTexture = loadTexture("/client/textures/moon_phases.png");
    }

    public void render() {
        if (sunTexture == -1 && moonTexture == -1) return;

        float f = WorldTime.fraction();

        if (lastFraction >= 0f && f < lastFraction - 0.5f) {
            cyclesElapsedSinceStart++;
        }
        lastFraction = f;
        float sunAlpha = WorldTime.sunStrength();
        float moonAlpha = 1f - sunAlpha;

        glPushMatrix();

        if (Minecraft.mc != null && Minecraft.mc.localPlayer != null) {
            glTranslated(Minecraft.mc.localPlayer.x, Minecraft.mc.localPlayer.y, Minecraft.mc.localPlayer.z);
        }

        boolean lightingWasOn = glIsEnabled(GL_LIGHTING);
        boolean colorMaterialOn = glIsEnabled(GL_COLOR_MATERIAL);
        if (lightingWasOn) glDisable(GL_LIGHTING);
        if (colorMaterialOn) glDisable(GL_COLOR_MATERIAL);

        glDisable(GL_FOG);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE);
        glDepthMask(false);
        glEnable(GL_TEXTURE_2D);
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        if (sunTexture != -1 && sunAlpha > 0.001f) {
            glPushMatrix();
            glRotatef(360f * (f - 0.5f), 0f, 0f, 1f);
            Textures.bind(sunTexture);
            glColor4f(1f, 1f, 1f, sunAlpha);
            drawQuad(SUN_SIZE, 0f, 0f, 1f, 1f);
            glPopMatrix();
        }

        if (moonTexture != -1 && moonAlpha > 0.001f) {
            glPushMatrix();
            glRotatef(360f * (f - 0.5f) + 180f, 0f, 0f, 1f);
            Textures.bind(moonTexture);
            int phaseIdx = (int) Math.floorMod(cyclesElapsedSinceStart, (long) MOON_PHASES);
            int col = phaseIdx % MOON_COLS;
            int row = phaseIdx / MOON_COLS;
            float u0 = col       / (float) MOON_COLS;
            float u1 = (col + 1) / (float) MOON_COLS;
            float v0 = row       / (float) MOON_ROWS;
            float v1 = (row + 1) / (float) MOON_ROWS;
            glColor4f(1f, 1f, 1f, moonAlpha);
            drawQuad(MOON_SIZE, u0, v0, u1, v1);
            glPopMatrix();
        }

        glColor4f(1f, 1f, 1f, 1f);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        if (colorMaterialOn) glEnable(GL_COLOR_MATERIAL);
        if (lightingWasOn)   glEnable(GL_LIGHTING);

        glPopMatrix();
    }

    private static void drawQuad(float size, float u0, float v0, float u1, float v1) {
        float y = SKY_RADIUS;
        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0); glVertex3f(-size, y, -size);
        glTexCoord2f(u1, v0); glVertex3f( size, y, -size);
        glTexCoord2f(u1, v1); glVertex3f( size, y,  size);
        glTexCoord2f(u0, v1); glVertex3f(-size, y,  size);
        glEnd();
    }

    private static int loadTexture(String resourcePath) {
        try (InputStream in = SkyRenderer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.err.println("Sky texture not found: " + resourcePath);
                return -1;
            }
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) {
                System.err.println("Failed to decode " + resourcePath);
                return -1;
            }
            int w = raw.getWidth(), h = raw.getHeight();

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2 = img.createGraphics();
            g2.setComposite(java.awt.AlphaComposite.Src);
            g2.drawImage(raw, 0, 0, null);
            g2.dispose();

            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);
            for (int i = 0; i < pixels.length; i++) {
                int a = (pixels[i] >> 24) & 0xFF;
                int r = (pixels[i] >> 16) & 0xFF;
                int g = (pixels[i] >>  8) & 0xFF;
                int b =  pixels[i]        & 0xFF;
                pixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }
            ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
            buf.asIntBuffer().put(pixels);

            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            return id;
        } catch (Exception e) {
            System.err.println("Failed to load " + resourcePath + ": " + e.getMessage());
            return -1;
        }
    }
}