package client.hud;

import client.*;
import client.level.Tile;
import client.net.PlayerManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL11.*;

public class Info {

    private final FontRenderer fontRenderer;

    private static final int[] HOTBAR_IDS = { 1, 2, 3, 4, 5, 6 };
    private static final int[] HOTBAR_KEYS = {
            Keyboard.KEY_1,
            Keyboard.KEY_2,
            Keyboard.KEY_3,
            Keyboard.KEY_4,
            Keyboard.KEY_5,
            Keyboard.KEY_6
    };

    private int selectedSlot = 0;

    public void tickScroll() {
        int wheel = Mouse.getDWheel();
        if (wheel < 0) selectedSlot = (selectedSlot + 1) % HOTBAR_IDS.length;
        else if (wheel > 0) selectedSlot = (selectedSlot - 1 + HOTBAR_IDS.length) % HOTBAR_IDS.length;
    }


    public void tickKeys() {
        for (int i = 0; i < HOTBAR_KEYS.length; i++) {
            if (Keyboard.isKeyDown(HOTBAR_KEYS[i])) {
                selectedSlot = i;
                break;
            }
        }
    }

    public int getSelectedBlockId() {
        return HOTBAR_IDS[selectedSlot];
    }

    private static final int SLOT_SIZE = 40;
    private static final int SLOT_GAP = 4;
    private static final int HOTBAR_PAD = 6;

    public Info(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    public void render(int w, int h) {


        glMatrixMode(GL_PROJECTION);
        glPushMatrix(); glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix(); glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glEnable(GL_TEXTURE_2D); glColor4f(1, 1, 1, 1);

        Player player = Minecraft.mc.player;

        fontRenderer.drawString(
                String.format("XYZ: %.0f, %.0f, %.0f", player.x, player.y, player.z),
                5, 1, true);
        fontRenderer.drawString("FPS: " + Minecraft.mc.fps, 5, 21, true);

        if (Keyboard.isKeyDown(Keyboard.KEY_TAB)) {
            drawTablist(fontRenderer, w);
        }

        Textures.bind(0);

        drawHotbar(w, h);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION); glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private void drawHotbar(int w, int h) {
        int slots    = HOTBAR_IDS.length;
        int barWidth = slots * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP + HOTBAR_PAD * 2;
        int barH     = SLOT_SIZE + HOTBAR_PAD * 2;
        int barX     = (w - barWidth) / 2;
        int barY     = h - barH - 8;

        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, 0.45f);
        fillRect(barX, barY, barX + barWidth, barY + barH);

        for (int i = 0; i < slots; i++) {
            int sx = barX + HOTBAR_PAD + i * (SLOT_SIZE + SLOT_GAP);
            int sy = barY + HOTBAR_PAD;

            glColor4f(1f, 1f, 1f, i == selectedSlot ? 0.30f : 0.10f);
            fillRect(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE);

            if (i == selectedSlot) {
                glColor4f(1f, 1f, 1f, 0.9f);
                drawRectOutline(sx - 2, sy - 2, sx + SLOT_SIZE + 2, sy + SLOT_SIZE + 2, 2);
            }

            int blockId = HOTBAR_IDS[i];
            float[] col = blockColor(blockId);
            glColor4f(col[0], col[1], col[2], 1f);
            fillRect(sx + 5, sy + 5, sx + SLOT_SIZE - 5, sy + SLOT_SIZE - 5);

            glEnable(GL_TEXTURE_2D); glColor4f(1, 1, 1, 1);
            fontRenderer.drawString(String.valueOf(i + 1), sx + 3, sy + 3, true);

            if (i == selectedSlot) {
                Tile t = Tile.fromId(blockId);
                String label = t != null ? t.name : "?";
                int lw = fontRenderer.getStringWidth(label);
                fontRenderer.drawString(label,
                        sx + (SLOT_SIZE - lw) / 2,
                        barY - fontRenderer.getStringHeight() - 3,
                        Color.WHITE, true);
            }

            glDisable(GL_TEXTURE_2D);
        }
    }

    private static float[] blockColor(int id) {
        switch (id) {
            case 1: return new float[]{0.40f, 0.75f, 0.30f}; // Grass
            case 2: return new float[]{0.55f, 0.55f, 0.55f}; // Cobble
            case 3: return new float[]{0.60f, 0.40f, 0.20f}; // Dirt
            case 4: return new float[]{0.10f, 0.10f, 0.10f}; // Obsidian
            case 5: return new float[]{0.90f, 0.85f, 0.55f}; // Sand
            case 6: return new float[]{0.70f, 0.35f, 0.25f}; // Bricks
            default:return new float[]{1f, 0f, 1f};          // Unknown
        }
    }


    private static void fillRect(int x0, int y0, int x1, int y1) {
        glBegin(GL_QUADS);
        glVertex2f(x0, y0); glVertex2f(x1, y0);
        glVertex2f(x1, y1); glVertex2f(x0, y1);
        glEnd();
    }

    private static void drawRectOutline(int x0, int y0, int x1, int y1, int thickness) {
        fillRect(x0, y0, x1, y0 + thickness);
        fillRect(x0, y1 - thickness, x1, y1);
        fillRect(x0, y0, x0 + thickness, y1);
        fillRect(x1 - thickness, y0, x1, y1);
    }

    private void drawTablist(FontRenderer fontRenderer, int w) {
        int lineHeight = 20;
        Map<String, client.Position> tabPlayers =
                new TreeMap<>(Minecraft.mc.playerManager.getPlayers());
        tabPlayers.put(Minecraft.mc.username,
                new client.Position(0, 0, 0, 0, (int) Minecraft.mc.rtt));

        int widestName = 0;
        for (Map.Entry<String, client.Position> entry : tabPlayers.entrySet()) {
            int tw = fontRenderer.getStringWidth(
                    entry.getKey() + "   " + entry.getValue().ping + "ms");
            if (tw > widestName) widestName = tw;
        }

        int x = (w / 2) - (widestName / 2);
        int y = 10;
        int totalHeight = tabPlayers.size() * lineHeight;
        int yPad = 2, xPad = 10;
        int fontOffset = (lineHeight - fontRenderer.getStringHeight()) / 2;

        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, 0.3f);
        fillRect(x - xPad, y - yPad, x + widestName + xPad, y + totalHeight + yPad);
        glEnable(GL_TEXTURE_2D);

        for (Map.Entry<String, client.Position> entry : tabPlayers.entrySet()) {
            String name = entry.getKey();
            String ping = entry.getValue().ping + "ms";

            fontRenderer.drawString(name, x, y + fontOffset, Color.WHITE, true);

            int pingX = x + widestName - fontRenderer.getStringWidth(ping);
            Color pingColor;
            if (entry.getValue().ping >= 250) pingColor = Color.RED;
            else if (entry.getValue().ping >= 150) pingColor = Color.ORANGE;
            else pingColor = Color.GREEN;

            fontRenderer.drawString(ping, pingX, y + fontOffset, pingColor, true);
            y += lineHeight;
        }
    }
}