package client.hud;

import client.*;
import client.net.PlayerManager;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_MODELVIEW;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_PROJECTION;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glLoadIdentity;
import static org.lwjgl.opengl.GL11.glMatrixMode;
import static org.lwjgl.opengl.GL11.glOrtho;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;

public class Info {

    private FontRenderer fontRenderer;

    public Info(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    public void render(int w, int h) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1f, 1f, 1f, 1f);

        Player player = Minecraft.mc.player;
        fontRenderer.drawString(String.format("XYZ: %.0f, %.0f, %.0f", player.x, player.y, player.z),5, 1, true);
        fontRenderer.drawString("FPS: " + Minecraft.mc.fps, 5, 21, true);

        if(Keyboard.isKeyDown(Keyboard.KEY_TAB)) {
            drawTablist(fontRenderer);
        }

        Textures.bind(0); //reset the bind shi otherwise it breaks

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void drawTablist(FontRenderer fontRenderer) {
        int lineHeight = 20;
        Map<String, Position> tabPlayers = new TreeMap<>(Minecraft.mc.playerManager.getPlayers());
        tabPlayers.put(Minecraft.mc.username, new Position(0, 0, 0, 0, (int) Minecraft.mc.rtt));

        int widestName = 0;
        for (Map.Entry<String, Position> player : tabPlayers.entrySet()) {
            int w = fontRenderer.getStringWidth(player.getKey() + "   " + player.getValue().ping + "ms");
            if (w > widestName) widestName = w;
        }

        int x = (Minecraft.mc.width / 2) - (widestName / 2);
        int y = 10;
        int totalHeight = tabPlayers.size() * lineHeight;
        int yPadding = 2;
        int xPadding = 10;
        int fontOffset = (lineHeight - fontRenderer.getStringHeight()) / 2;

        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, 0.3f);
        glBegin(GL_QUADS);
        glVertex2f(x - xPadding, y - yPadding);
        glVertex2f(x + widestName + xPadding, y - yPadding);
        glVertex2f(x + widestName + xPadding, y + totalHeight + yPadding);
        glVertex2f(x - xPadding, y + totalHeight + yPadding);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        for (Map.Entry<String, Position> player : tabPlayers.entrySet()) {
            String name = player.getKey();
            String ping = player.getValue().ping + "ms";

            fontRenderer.drawString(name, x, y + fontOffset, Color.WHITE, true);

            int pingX = x + widestName - fontRenderer.getStringWidth(ping);

            Color pingColor;
            if (player.getValue().ping >= 250) {
                pingColor = Color.RED;
            } else if (player.getValue().ping >= 150) {
                pingColor = Color.ORANGE;
            } else {
                pingColor = Color.GREEN;
            }

            fontRenderer.drawString(ping, pingX, y + fontOffset, pingColor, true);

            y += lineHeight;
        }
    }
}
