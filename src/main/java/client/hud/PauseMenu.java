package client.hud;

import client.FontRenderer;
import client.Minecraft;
import client.gui.screen.components.ButtonComponent;
import client.gui.screen.impl.OptionsScreen;
import org.lwjgl.input.Mouse;

import java.awt.*;

import static org.lwjgl.opengl.GL11.*;

public class PauseMenu {
    private static final int BTN_W = 200;
    private static final int BTN_H = 32;
    private static final int BTN_GAP = 12;

    public boolean visible = false;

    private boolean wasMouseDown = false;

    public void render(FontRenderer font, int width, int height) {
        if (!visible) return;

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor4f(0f, 0f, 0f, 0.75f);

        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(width, 0);
        glVertex2f(width, height);
        glVertex2f(0, height);
        glEnd();

        int totalH = BTN_H * 3 + BTN_GAP * 2;
        int startX = (width - BTN_W) / 2;
        int startY = (height - totalH) / 2;

        ButtonComponent resumeBtn =
                new ButtonComponent("Resume", startX, startY, BTN_W, BTN_H);

        ButtonComponent optionsBtn =
                new ButtonComponent("Options",
                        startX,
                        startY + (BTN_H + BTN_GAP),
                        BTN_W,
                        BTN_H);

        ButtonComponent disconnectBtn =
                new ButtonComponent("Disconnect",
                        startX,
                        startY + (BTN_H + BTN_GAP) * 2,
                        BTN_W,
                        BTN_H);

        int mx = Mouse.getX();
        int my = height - Mouse.getY() - 1;

        boolean isMouseDown = Mouse.isButtonDown(0);

        if (isMouseDown && !wasMouseDown) {
            if (resumeBtn.contains(mx, my)) {
                visible = false;
                Mouse.setGrabbed(true);
                wasMouseDown = isMouseDown;
                return;
            }

            if (optionsBtn.contains(mx, my)) {
                visible = false;
                Mouse.setGrabbed(false);
                Minecraft.mc.setScreen(OptionsScreen.forInGame());
                wasMouseDown = isMouseDown;
                return;
            }

            if (disconnectBtn.contains(mx, my)) {
                Minecraft.mc.disconnectPending = true;
                wasMouseDown = isMouseDown;
                return;
            }
        }

        wasMouseDown = isMouseDown;

        drawButton(font, resumeBtn, mx, my);
        drawButton(font, optionsBtn, mx, my);
        drawButton(font, disconnectBtn, mx, my);

        glDisable(GL_BLEND);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glMatrixMode(GL_MODELVIEW);
    }

    private void drawButton(FontRenderer font, ButtonComponent btn, int mx, int my) {
        boolean hovered = btn.contains(mx, my);

        glDisable(GL_TEXTURE_2D);

        glColor4f(
                hovered ? 0.6f : 0.2f,
                hovered ? 0.6f : 0.2f,
                hovered ? 0.6f : 0.2f,
                hovered ? 0.85f : 0.75f
        );

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

        font.drawString(
                btn.label,
                lx,
                ly,
                hovered ? Color.YELLOW : Color.WHITE,
                true
        );
    }
}