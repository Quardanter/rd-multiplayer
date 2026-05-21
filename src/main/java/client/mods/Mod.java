package client.mods;

public interface Mod {
    void onEnable(ModContext ctx);
    default void onDisable() {}
}