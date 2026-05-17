package client.gui.screen.impl;

import client.FontRenderer;
import client.Minecraft;
import client.Textures;
import client.gui.screen.Screen;
import client.gui.screen.components.ButtonComponent;
import org.lwjgl.input.Mouse;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public class LoadingScreen extends Screen {
    public String loadingText = "";
    public Color loadingColor = Color.WHITE;
    int loadingBackground = -1;

    @Override
    public void render(FontRenderer font, int width, int height) {
        if (loadingBackground == -1) {
            loadingBackground = Textures.loadTexture("/client/textures/background.png", GL_NEAREST);
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
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(1f, 1f, 1f, 1f);
        Textures.bind(loadingBackground);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(0, 0);
        glTexCoord2f(1, 0); glVertex2f(width, 0);
        glTexCoord2f(1, 1); glVertex2f(width, height);
        glTexCoord2f(0, 1); glVertex2f(0, height);
        glEnd();

        int textWidth = font.getStringWidth(loadingText);
        int textHeight = font.getStringHeight();
        int tx = (width / 2) - (textWidth / 2);
        int ty = (height / 2) - (textHeight / 2);

        glColor4f(1f, 1f, 1f, 1f);
        font.drawString(loadingText, tx, ty, loadingColor, true);

        int btnW = 160;
        int btnH = 28;
        int btnX = (width - btnW) / 2;
        int btnY = ty + textHeight + 16;
        ButtonComponent backBtn = new ButtonComponent("Back", btnX, btnY, btnW, btnH);

        int mx = Mouse.getX();
        int myFlipped = height - Mouse.getY() - 1;
        boolean hovered = backBtn.contains(mx, myFlipped);

        while (Mouse.next()) {
            if (Mouse.getEventButton() == 0 && Mouse.getEventButtonState()) {
                if (backBtn.contains(mx, myFlipped)) {
                    Minecraft.mc.disconnect();
                    return;
                }
            }
        }

        glDisable(GL_TEXTURE_2D);
        glColor4f(hovered ? 0.6f : 0.2f, hovered ? 0.6f : 0.2f, hovered ? 0.6f : 0.2f, hovered ? 0.85f : 0.75f);
        glBegin(GL_QUADS);
        glVertex2f(backBtn.x, backBtn.y);
        glVertex2f(backBtn.x + backBtn.w, backBtn.y);
        glVertex2f(backBtn.x + backBtn.w, backBtn.y + backBtn.h);
        glVertex2f(backBtn.x, backBtn.y + backBtn.h);
        glEnd();

        glColor4f(0.8f, 0.8f, 0.8f, 0.9f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(backBtn.x, backBtn.y);
        glVertex2f(backBtn.x + backBtn.w, backBtn.y);
        glVertex2f(backBtn.x + backBtn.w, backBtn.y + backBtn.h);
        glVertex2f(backBtn.x, backBtn.y + backBtn.h);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        int lw = font.getStringWidth(backBtn.label);
        int lh = font.getStringHeight();
        int lx = backBtn.x + (backBtn.w - lw) / 2;
        int ly = backBtn.y + (backBtn.h - lh) / 2;
        font.drawString(backBtn.label, lx, ly, hovered ? Color.YELLOW : Color.WHITE, true);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    public void setLoadingText(String loadingText) {
        this.loadingText = loadingText;
    }

    public void setLoadingColor(Color loadingColor) {
        this.loadingColor = loadingColor;
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}