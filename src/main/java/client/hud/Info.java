package client.hud;

import client.*;
import client.level.block.BlockRegistry;
import client.level.FaceTextures;
import client.level.block.Block;
import client.player.local.LocalPlayer;
import client.player.remote.RemotePlayer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL11.*;

public class Info {
    private static final int[] BLOCKS = {1,2,3,4,5,6,7};
    private static final int[] KEYS = {Keyboard.KEY_1,Keyboard.KEY_2,Keyboard.KEY_3,Keyboard.KEY_4,Keyboard.KEY_5,Keyboard.KEY_6,Keyboard.KEY_7};

    private static final int SLOT = 40, GAP = 4, PAD = 6;

    private final FontRenderer font;
    private int selected;

    private int terrainTexture = -1;

    public Info(FontRenderer font) {
        this.font = font;
    }

    public void tickScroll() {
        int wheel = Mouse.getDWheel();
        if (wheel < 0) selected = (selected + 1) % BLOCKS.length;
        else if (wheel > 0) selected = (selected - 1 + BLOCKS.length) % BLOCKS.length;
    }

    public void tickKeys() {
        if (Minecraft.mc.chat.toggled) return;

        for (int i = 0; i < KEYS.length; i++) {
            if (Keyboard.isKeyDown(KEYS[i])) {
                selected = i;
                return;
            }
        }
    }

    public int getSelectedBlockId() {
        return BLOCKS[selected];
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

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        LocalPlayer p = Minecraft.mc.localPlayer;

        font.drawString(String.format("XYZ: %.0f %.0f %.0f", p.x, p.y, p.z), 5, 2, true);
        font.drawString("FPS: " + Minecraft.mc.fps, 5, 22, true);

        if (Keyboard.isKeyDown(Keyboard.KEY_TAB)) drawTablist(w);

        drawHotbar(w, h);

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glPopMatrix();

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glMatrixMode(GL_MODELVIEW);
    }

    private void drawHotbar(int w, int h) {
        int width = BLOCKS.length * (SLOT + GAP) - GAP + PAD * 2;
        int x = (w - width) / 2;
        int y = h - SLOT - PAD * 2 - 8;

        glDisable(GL_TEXTURE_2D);

        glColor4f(0,0,0,0.45f);
        rect(x, y, x + width, y + SLOT + PAD * 2);

        for (int i = 0; i < BLOCKS.length; i++) {

            int sx = x + PAD + i * (SLOT + GAP), sy = y + PAD;

            glColor4f(1,1,1,i == selected ? 0.3f : 0.1f);
            rect(sx, sy, sx + SLOT, sy + SLOT);

            if (i == selected) {
                glColor4f(1,1,1,0.9f);
                outline(sx - 2, sy - 2, sx + SLOT + 2, sy + SLOT + 2, 2);
            }

            int iconX0 = sx + 5, iconY0 = sy + 5;
            int iconX1 = sx + SLOT - 5, iconY1 = sy + SLOT - 5;

            assert BlockRegistry.get(BLOCKS[i]) != null;
            drawBlockSideIcon(BlockRegistry.get(BlockRegistry.get(BLOCKS[i]).id), iconX0, iconY0, iconX1, iconY1);
            glDisable(GL_TEXTURE_2D);

            glEnable(GL_TEXTURE_2D);

            font.drawString(String.valueOf(i + 1), sx + 3, sy + 3, true);

            if (i == selected) {
                Block t = BlockRegistry.get(BLOCKS[i]);

                if (t != null)
                    font.drawString(t.name, sx + (SLOT - font.getStringWidth(t.name)) / 2, y - font.getStringHeight() - 3, Color.WHITE, true);
            }

            glDisable(GL_TEXTURE_2D);
        }
    }

    private void drawTablist(int w) {
        Map<String, RemotePlayer> players = new TreeMap<>(Minecraft.mc.playerManager.getPlayers());
        players.put(Minecraft.mc.username, new RemotePlayer(Minecraft.mc.username, 0,0,0,0,(int) Minecraft.mc.rtt));

        int widest = 0;

        for (Map.Entry<String, RemotePlayer> e : players.entrySet())
            widest = Math.max(widest, font.getStringWidth(e.getKey() + "   " + e.getValue().ping + "ms"));

        int x = w / 2 - widest / 2, y = 10;

        glDisable(GL_TEXTURE_2D);

        glColor4f(0,0,0,0.3f);
        rect(x - 10, y - 2, x + widest + 10, y + players.size() * 20 + 2);

        glEnable(GL_TEXTURE_2D);

        for (Map.Entry<String, RemotePlayer> e : players.entrySet()) {

            int ping = e.getValue().ping;
            Color pingColor = ping >= 250 ? Color.RED : ping >= 150 ? Color.ORANGE : Color.GREEN;

            font.drawString(e.getKey(), x, y, Color.WHITE, true);
            font.drawString(ping + "ms", x + widest - font.getStringWidth(ping + "ms"), y, pingColor, true);

            y += 20;
        }
    }

    private void rect(int x0, int y0, int x1, int y1) {
        glBegin(GL_QUADS);
        glVertex2f(x0, y0);
        glVertex2f(x1, y0);
        glVertex2f(x1, y1);
        glVertex2f(x0, y1);
        glEnd();
    }

    private void outline(int x0, int y0, int x1, int y1, int t) {
        rect(x0, y0, x1, y0 + t);
        rect(x0, y1 - t, x1, y1);
        rect(x0, y0, x0 + t, y1);
        rect(x1 - t, y0, x1, y1);
    }

    private void drawBlockSideIcon(Block block, int x0, int y0, int x1, int y1) {
        if (block == null) return;

        if (terrainTexture == -1) {
            terrainTexture = Textures.loadTexture("/client/textures/terrain.png", GL_NEAREST);
        }

        int col = block.faces.col(FaceTextures.NORTH);
        float u0 = col / 16f;
        float u1 = u0 + 16 / 256f;
        float v0 = 0f;
        float v1 = 16 / 256f;

        glEnable(GL_TEXTURE_2D);
        Textures.bind(terrainTexture);
        glColor4f(1f, 1f, 1f, 1f);

        glBegin(GL_QUADS);
        glTexCoord2f(u0, v0); glVertex2f(x0, y0);
        glTexCoord2f(u1, v0); glVertex2f(x1, y0);
        glTexCoord2f(u1, v1); glVertex2f(x1, y1);
        glTexCoord2f(u0, v1); glVertex2f(x0, y1);
        glEnd();

        Textures.bind(0);
    }
}