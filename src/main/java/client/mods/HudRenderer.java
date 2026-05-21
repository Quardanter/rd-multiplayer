package client.mods;

@FunctionalInterface
public interface HudRenderer {
    void render(int width, int height);
}