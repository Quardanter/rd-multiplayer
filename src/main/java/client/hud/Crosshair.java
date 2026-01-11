package client.hud;

import client.Textures;

import static org.lwjgl.opengl.GL11.*;

public class Crosshair {
    public int size, texture;

    public Crosshair(int size, String texturePath) {
        this.size = size;

        this.texture = Textures.loadTexture(texturePath, GL_NEAREST);
    }

    public void render(int w, int h) {
        int x = w / 2 - size / 2;
        int y = h / 2 - size / 2;

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
        glColor4f(1f, 1f, 1f, 0.6f);

        Textures.bind(texture);

        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(x, y);
        glTexCoord2f(1, 0); glVertex2f(x + size, y);
        glTexCoord2f(1, 1); glVertex2f(x + size, y + size);
        glTexCoord2f(0, 1); glVertex2f(x, y + size);
        glEnd();

        Textures.bind(0); //reset the bind shi otherwise it breaks

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
}
