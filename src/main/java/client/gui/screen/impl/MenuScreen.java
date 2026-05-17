package client.gui.screen.impl;

import static org.lwjgl.opengl.GL11.*;

import client.FontRenderer;
import client.Minecraft;
import client.Textures;
import client.gui.screen.Screen;
import client.gui.screen.components.ButtonComponent;
import client.player.render.SkinModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public class MenuScreen extends Screen {
    public String titleText = "rd-multiplayer";
    public String versionText = "v" + Minecraft.GIT_HASH;

    int bg = -1;
    private int previewSkinId = -1;
    private boolean skinLoaded = false;
    private volatile File pendingSkinFile = null;
    private long lastSkinModified = -1;

    private ButtonComponent[] buttons;

    @Override
    public void init() {
        super.init();
        try {
            new File("skins").mkdirs();
            File help = new File("skins/HELP.txt");
            if (!help.exists())
                try (java.io.FileWriter fw = new java.io.FileWriter(help)) {
                    fw.write("Drop a Minecraft skin named 'skin.png' in this directory.");
                }
        } catch (Exception ignore) {
        }
    }

    @Override
    public void render(FontRenderer font, int width, int height) {
        if (bg == -1) {
            bg = Textures.loadTexture("/client/textures/menu_background.png", GL_NEAREST);
        }

        File skinFile = new File("skins/skin.png");
        if (skinFile.lastModified() != lastSkinModified) {
            lastSkinModified = skinFile.lastModified();
            pendingSkinFile = skinFile;
        }
        if (pendingSkinFile != null) {
            loadSkinFile(pendingSkinFile);
            pendingSkinFile = null;
        }

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glEnable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, 1f);
        Textures.bind(bg);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex2f(0, 0);
        glTexCoord2f(1, 0);
        glVertex2f(width, 0);
        glTexCoord2f(1, 1);
        glVertex2f(width, height);
        glTexCoord2f(0, 1);
        glVertex2f(0, height);
        glEnd();

        int titleW = font.getStringWidth(titleText) * 2;
        int titleH = font.getStringHeight() * 2;
        int titleX = (width - titleW) / 2;
        int titleY = height / 4 - titleH / 2;

        glDisable(GL_TEXTURE_2D);
        glPushMatrix();
        glTranslatef(titleX, titleY, 0);
        glScalef(2f, 2f, 1f);
        glEnable(GL_TEXTURE_2D);
        font.drawString(titleText, 0, 0, Color.WHITE, true);
        glDisable(GL_TEXTURE_2D);
        glPopMatrix();
        glEnable(GL_TEXTURE_2D);

        int verW = font.getStringWidth(versionText);
        int verX = (width - verW) / 2;
        int verY = titleY + titleH + 6;
        font.drawString(versionText, verX, verY, Color.LIGHT_GRAY, true);

        int btnW = 160;
        int btnH = 28;
        int btnGap = 10;
        int totalBtnH = 3 * btnH + 2 * btnGap;
        int startY = height / 2 + height / 8 - totalBtnH / 2;
        int btnX = (width - btnW) / 2;

        buttons = new ButtonComponent[] {
                new ButtonComponent("Play", btnX, startY, btnW, btnH),
                new ButtonComponent("Options", btnX, startY + btnH + btnGap, btnW, btnH),
                new ButtonComponent("Quit", btnX, startY + (btnH + btnGap) * 2, btnW, btnH),
                new ButtonComponent("Edit Skin", width - 130, height - 38, 120, 28),
        };

        int mx = Mouse.getX();
        int myFlipped = height - Mouse.getY() - 1;

        while (Mouse.next()) {
            if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                for (ButtonComponent btn : buttons) {
                    if (btn.contains(mx, myFlipped)) {
                        onButtonClicked(btn.label);
                    }
                }
            }
        }

        int skinBtnX = width - 130;
        int skinBtnY = height - 38;
        int previewH = 120;
        int previewW = 120;
        int previewX = skinBtnX;
        int previewY = skinBtnY - previewH - 8;

        if (skinLoaded && previewSkinId != -1) {
            renderSkinPreview(previewX, previewY, previewW, previewH);
        } else {
            String msg = "No skin!";
            glEnable(GL_TEXTURE_2D);
            font.drawString(msg, skinBtnX + (previewW - font.getStringWidth(msg)) / 2,
                    previewY + (previewH - font.getStringHeight()) / 2, Color.RED, true);
            glDisable(GL_TEXTURE_2D);
        }

        glDisable(GL_TEXTURE_2D);
        for (ButtonComponent btn : buttons) {
            boolean hovered = btn.contains(mx, myFlipped);
            glColor4f(hovered ? 0.6f : 0.2f, hovered ? 0.6f : 0.2f, hovered ? 0.6f : 0.2f, hovered ? 0.85f : 0.75f);
            glBegin(GL_QUADS);
            glVertex2f(btn.x, btn.y);
            glVertex2f(btn.x + btn.w, btn.y);
            glVertex2f(btn.x + btn.w, btn.y + btn.h);
            glVertex2f(btn.x, btn.y + btn.h);
            glEnd();

            glColor4f(0.8f, 0.8f, 0.8f, 0.9f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(btn.x, btn.y);
            glVertex2f(btn.x + btn.w, btn.y);
            glVertex2f(btn.x + btn.w, btn.y + btn.h);
            glVertex2f(btn.x, btn.y + btn.h);
            glEnd();

            glEnable(GL_TEXTURE_2D);
            int lw = font.getStringWidth(btn.label);
            int lh = font.getStringHeight();
            int lx = btn.x + (btn.w - lw) / 2;
            int ly = btn.y + (btn.h - lh) / 2;
            font.drawString(btn.label, lx, ly, hovered ? Color.YELLOW : Color.WHITE, true);
            glDisable(GL_TEXTURE_2D);
        }

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void renderSkinPreview(int previewX, int previewY, int previewW, int previewH) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, previewSkinId);
        glColor4f(1f, 1f, 1f, 1f);
        float scale = previewH / 1.85f;
        float cx = previewX + previewW / 2f;
        float by = previewY + previewH;
        drawSkinFace(SkinModel.BODY[2], cx - 0.25f * scale, by - 1.35f * scale, cx + 0.25f * scale, by - 0.60f * scale);
        drawSkinFace(SkinModel.HEAD[2], cx - 0.25f * scale, by - 1.85f * scale, cx + 0.25f * scale, by - 1.35f * scale);
        drawSkinFace(SkinModel.R_ARM[2], cx - 0.4375f * scale, by - 1.35f * scale, cx - 0.25f * scale, by - 0.60f * scale);
        drawSkinFace(SkinModel.L_ARM[2], cx + 0.25f * scale, by - 1.35f * scale, cx + 0.4375f * scale, by - 0.60f * scale);
        drawSkinFace(SkinModel.R_LEG[2], cx - 0.25f * scale, by - 0.60f * scale, cx, by);
        drawSkinFace(SkinModel.L_LEG[2], cx, by - 0.60f * scale, cx + 0.25f * scale, by);
        glDepthMask(false);
        float g = 1f / scale;
        drawSkinFace(SkinModel.HEAD_HAT[2], cx - 0.25f * scale - g, by - 1.85f * scale - g, cx + 0.25f * scale + g, by - 1.35f * scale + g);
        drawSkinFace(SkinModel.BODY_OUTER[2], cx - 0.25f * scale - g, by - 1.35f * scale - g, cx + 0.25f * scale + g, by - 0.60f * scale + g);
        drawSkinFace(SkinModel.R_ARM_OUTER[2], cx - 0.4375f * scale - g, by - 1.35f * scale - g, cx - 0.25f * scale + g, by - 0.60f * scale + g);
        drawSkinFace(SkinModel.L_ARM_OUTER[2], cx + 0.25f * scale - g, by - 1.35f * scale - g, cx + 0.4375f * scale + g, by - 0.60f * scale + g);
        drawSkinFace(SkinModel.R_LEG_OUTER[2], cx - 0.25f * scale - g, by - 0.60f * scale - g, cx + g, by + g);
        drawSkinFace(SkinModel.L_LEG_OUTER[2], cx - g, by - 0.60f * scale - g, cx + 0.25f * scale + g, by + g);
        glDepthMask(true);
        glDisable(GL_TEXTURE_2D);
    }

    private void drawSkinFace(float[] uv, float x0, float y0, float x1, float y1) {
        glBegin(GL_QUADS);
        glTexCoord2f(uv[0], uv[1]);
        glVertex2f(x0, y0);
        glTexCoord2f(uv[2], uv[1]);
        glVertex2f(x1, y0);
        glTexCoord2f(uv[2], uv[3]);
        glVertex2f(x1, y1);
        glTexCoord2f(uv[0], uv[3]);
        glVertex2f(x0, y1);
        glEnd();
    }

    private void onButtonClicked(String label) {
        if (label.equals("Play")) {
            Minecraft.mc.setScreen(new ServerSelectScreen());
        }
        if (label.equals("Quit")) {
            Display.destroy();
            System.exit(0);
        }
        if (label.equals("Edit Skin")) {
            try {
                File skinsDir = new File("skins");
                skinsDir.mkdirs();
                Desktop.getDesktop().open(skinsDir);
            } catch (Exception e) {
                System.err.println("Failed to open skins folder: " + e.getMessage());
            }
        }
    }

    private void loadSkinFile(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null)
                return;
            int w = img.getWidth(), h = img.getHeight();
            if (!((w == 64 && h == 64) || (w == 64 && h == 32)))
                return;
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);
            for (int i = 0; i < pixels.length; i++) {
                int a = (pixels[i] >> 24) & 0xFF;
                int r = (pixels[i] >> 16) & 0xFF;
                int g = (pixels[i] >> 8) & 0xFF;
                int b = pixels[i] & 0xFF;
                pixels[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }
            ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
            buf.asIntBuffer().put(pixels);
            if (previewSkinId == -1) previewSkinId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, previewSkinId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            skinLoaded = true;
        } catch (Exception ignore) {
            skinLoaded = false;
        }
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}