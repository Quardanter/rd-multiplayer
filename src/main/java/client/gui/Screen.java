package client.gui;

import client.FontRenderer;

public abstract class Screen {
    public abstract void render(FontRenderer font, int width, int height);
    public void init() {}
    public void destroy() {}
}