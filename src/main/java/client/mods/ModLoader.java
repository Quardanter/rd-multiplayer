package client.mods;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

public final class ModLoader {

    private static final Path MODS_DIR = Paths.get("mods");
    private static final String MANIFEST = "mod.properties";

    private ModLoader() {}

    public static void loadAll() {
        if (!Files.exists(MODS_DIR)) {
            try { Files.createDirectories(MODS_DIR); }
            catch (IOException e) {
                System.err.println("Could not create mods/ directory: " + e.getMessage());
                return;
            }
        }
        if (!Files.isDirectory(MODS_DIR)) {
            System.err.println("mods/ exists but is not a directory");
            return;
        }

        Set<String> seenIds = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(MODS_DIR, "*.jar")) {
            for (Path jar : stream) {
                loadOne(jar, seenIds);
            }
        } catch (IOException e) {
            System.err.println("Failed to scan mods/: " + e.getMessage());
        }

        int n = ModRegistry.get().loadedMods().size();
        if (n > 0) System.out.println("Loaded " + n + " mod" + (n == 1 ? "" : "s") + " from mods/");
    }

    private static void loadOne(Path jar, Set<String> seenIds) {
        Manifest m = readManifest(jar);
        if (m == null) return; // already logged

        if (!seenIds.add(m.id)) {
            System.err.println("Mod id '" + m.id + "' is duplicated; skipping " + jar.getFileName());
            return;
        }

        URLClassLoader cl;
        try {
            URL[] urls = { jar.toUri().toURL() };
            cl = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        } catch (Exception e) {
            System.err.println("Failed to create classloader for " + jar.getFileName() + ": " + e.getMessage());
            return;
        }

        Mod instance;
        try {
            Class<?> klass = Class.forName(m.entry, true, cl);
            if (!Mod.class.isAssignableFrom(klass)) {
                System.err.println("Mod " + m.id + " entry class " + m.entry + " does not implement client.mods.Mod");
                return;
            }
            instance = (Mod) klass.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            System.err.println("Failed to instantiate mod " + m.id + ": " + t);
            t.printStackTrace();
            return;
        }

        ModContextImpl ctx = new ModContextImpl(m.id);
        try {
            instance.onEnable(ctx);
        } catch (Throwable t) {
            System.err.println("Mod " + m.id + " onEnable threw, unloading: " + t);
            t.printStackTrace();
            return;
        }

        ModRegistry.get().track(new ModRegistry.LoadedMod(m.id, m.name, m.version, instance));
        System.out.println("Mod loaded: " + m.name + " (" + m.id + " v" + m.version + ")");
    }

    private static Manifest readManifest(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            JarEntry entry = jf.getJarEntry(MANIFEST);
            if (entry == null) {
                System.err.println("Skipping " + jar.getFileName() + ": no " + MANIFEST + " at JAR root");
                return null;
            }
            Properties p = new Properties();
            try (InputStream in = jf.getInputStream(entry)) { p.load(in); }
            String id = trim(p.getProperty("id"));
            String name = trim(p.getProperty("name"));
            String version = trim(p.getProperty("version"));
            String klass = trim(p.getProperty("entry"));
            if (id == null || name == null || version == null || klass == null) {
                System.err.println("Skipping " + jar.getFileName() + ": " + MANIFEST + " missing one of id/name/version/entry");
                return null;
            }
            return new Manifest(id, name, version, klass);
        } catch (IOException e) {
            System.err.println("Failed to read " + jar.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    public static void disposeAll() {
        for (ModRegistry.LoadedMod m : ModRegistry.get().loadedMods()) {
            try { m.instance.onDisable(); }
            catch (Throwable t) {
                System.err.println("Mod " + m.id + " onDisable threw: " + t);
            }
        }
    }

    private static final class Manifest {
        final String id, name, version, entry;
        Manifest(String id, String name, String version, String entry) {
            this.id = id; this.name = name; this.version = version; this.entry = entry;
        }
    }

    private static final class ModContextImpl implements ModContext {
        private final String id;
        ModContextImpl(String id) { this.id = id; }

        @Override public String modId() { return id; }

        @Override public void registerCommand(String name, ChatCommand h) {
            ModRegistry.get().registerCommand(name, h);
        }
        @Override public void registerHud(HudRenderer r) { ModRegistry.get().registerHud(r); }
        @Override public void registerTick(Runnable r) { ModRegistry.get().registerTick(r); }
        @Override public void registerKeybind(int key, Runnable r) { ModRegistry.get().registerKeybind(key, r); }
        @Override public <E extends ModEvent> void addListener(Class<E> type, Consumer<E> listener) {
            ModRegistry.get().addListener(type, listener);
        }

        @Override public void chatLocal(String message) {
            try {
                client.Minecraft mc = client.Minecraft.mc;
                if (mc != null && mc.chat != null) {
                    mc.chat.addMessage("[" + id + "]", " " + message, true);
                }
            } catch (Throwable t) {
                System.err.println("chatLocal failed: " + t);
            }
        }
    }
}