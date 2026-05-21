package client.mods;

import java.util.function.Consumer;

public interface ModContext {
    String modId();

    // chat
    // registers a / (slash) command
    void registerCommand(String name, ChatCommand handler);
    // prints to client-side chat only
    void chatLocal(String message);

    // HUD
    void registerHud(HudRenderer renderer);

    // ticks
    void registerTick(Runnable onTick);

    // keybinds
    void registerKeybind(int key, Runnable onPress);

    // events
    <E extends ModEvent> void addListener(Class<E> type, Consumer<E> listener);
}