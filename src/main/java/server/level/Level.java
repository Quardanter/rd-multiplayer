package server.level;

import client.level.LevelListener;
import client.phys.AABB;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Level {
    public final int width;
    public final int height;
    public final int depth;

    public final byte[] blocks;
    private final int[] lightDepths;

    public Level(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;

        this.blocks = new byte[width * height * depth];
        this.lightDepths = new int[width * height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                for (int z = 0; z < height; z++) {
                    int index = (y * this.height + z) * this.width + x;

                    this.blocks[index] = (byte) ((y <= depth * 2 / 3) ? 1 : 0);
                }
            }
        }

        load();
    }

    private static final Path LEVEL_PATH = Paths.get("level.dat");

    public void load() {
        if (!Files.exists(LEVEL_PATH)) {
            System.out.println("No level.dat found, generating level...");
            return;
        }
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(Files.newInputStream(LEVEL_PATH)))) {
            dis.readFully(this.blocks);
            System.out.println("Level loaded from level.dat");
        } catch (Exception e) {
            System.err.println("Failed to load level.dat: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Path parent = LEVEL_PATH.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(LEVEL_PATH)))) {
                dos.write(this.blocks);
            }
            System.out.println("Level saved.");
        } catch (Exception e) {
            System.err.println("Failed to save level.dat: " + e.getMessage());
        }
    }

    public void setTile(int x, int y, int z, int id) {
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return;
        }

        this.blocks[(y * this.height + z) * this.width + x] = (byte) id;
    }


    public byte[] getBlocks() {return this.blocks;}

    public int getWidth() { return this.width; }
    public int getHeight() { return this.height; }
    public int getDepth() { return this.depth; }
}
