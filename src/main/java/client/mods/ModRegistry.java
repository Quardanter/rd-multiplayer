package client.mods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ModRegistry {

    private static final ModRegistry INSTANCE = new ModRegistry();
    public static ModRegistry get() { return INSTANCE; }

    private final List<HudRenderer> hud = new CopyOnWriteArrayList<>();
    private final List<Runnable> ticks = new CopyOnWriteArrayList<>();
    private final Map<Integer, List<Runnable>> keybinds = new ConcurrentHashMap<>();
    private final Map<String, ChatCommand> commands = new ConcurrentHashMap<>();
    private final Map<Class<? extends ModEvent>, List<Consumer<? extends ModEvent>>> listeners = new ConcurrentHashMap<>();

    private final List<LoadedMod> mods = new ArrayList<>();

    private ModRegistry() {}

    // mod-facing registration
    void registerHud(HudRenderer r) { hud.add(r); }
    void registerTick(Runnable r) { ticks.add(r); }
    void registerKeybind(int key, Runnable r) { keybinds.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(r); }
    void registerCommand(String name, ChatCommand h) { commands.put(name.toLowerCase(), h); }

    <E extends ModEvent> void addListener(Class<E> type, Consumer<E> l) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(l);
    }

    void track(LoadedMod m) { mods.add(m); }
    public List<LoadedMod> loadedMods() { return mods; }
    public java.util.Set<Integer> keybindKeys() { return keybinds.keySet(); }

    // host facing dispatches

    // dispatch HUD
    public void dispatchHud(int width, int height) {
        for (HudRenderer r : hud) {
            try { r.render(width, height); }
            catch (Throwable t) { logModError("hud", t); }
        }
    }

    // gets called every tick, 60 ticks per second
    public void dispatchTick() {
        for (Runnable r : ticks) {
            try { r.run(); }
            catch (Throwable t) { logModError("tick", t); }
        }
    }

    // dispatching key press
    public void dispatchKeyPress(int key) {
        List<Runnable> list = keybinds.get(key);
        if (list == null) return;
        for (Runnable r : list) {
            try { r.run(); }
            catch (Throwable t) { logModError("keybind", t); }
        }
    }

    // try to use a slash command
    public boolean tryHandleCommand(String line) {
        if (line == null || line.isEmpty()) return false;
        int sp = line.indexOf(' ');
        String name = (sp < 0 ? line : line.substring(0, sp)).toLowerCase();
        String args = (sp < 0 ? "" : line.substring(sp + 1));
        ChatCommand h = commands.get(name);
        if (h == null) return false;
        try {
            return h.handle(args);
        } catch (Throwable t) {
            logModError("command /" + name, t);
            return true;
        }
    }

    // dispatch
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void fire(ModEvent event) {
        if (event == null) return;
        List<Consumer<? extends ModEvent>> list = listeners.get(event.getClass());
        if (list == null) return;
        for (Consumer<? extends ModEvent> raw : list) {
            try { ((Consumer) raw).accept(event); }
            catch (Throwable t) { logModError("event " + event.getClass().getSimpleName(), t); }
        }
    }

    private static void logModError(String what, Throwable t) {
        System.err.println("Mod error in " + what + ": " + t);
        t.printStackTrace();
    }

    
    public static final class LoadedMod {
        public final String id;
        public final String name;
        public final String version;
        public final Mod instance;
        public LoadedMod(String id, String name, String version, Mod instance) {
            this.id = id; this.name = name; this.version = version; this.instance = instance;
        }
    }
}