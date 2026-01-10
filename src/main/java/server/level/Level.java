package server.level;

import client.level.LevelListener;
import client.phys.AABB;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
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

    /**
     * Three dimensional level containing all tiles
     *
     * @param width  Level width
     * @param height Level height
     * @param depth  Level depth
     */
    public Level(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;

        this.blocks = new byte[width * height * depth];
        this.lightDepths = new int[width * height];

        // Fill level with tiles
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                for (int z = 0; z < height; z++) {
                    // Calculate index from x, y and z
                    int index = (y * this.height + z) * this.width + x;

                    // Fill level with tiles
                    this.blocks[index] = (byte) ((y <= depth * 2 / 3) ? 1 : 0);
                }
            }
        }

        // Load level if it exists
        load();
    }

    /**
     * Load blocks from level.dat
     */
    public void load() {
        try {
            DataInputStream dis = new DataInputStream(new GZIPInputStream(Files.newInputStream(Paths.get("./server/level.dat"))));
            dis.readFully(this.blocks);
            dis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Store blocks in level.dat
     */
    public void save() {
        try {
            DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(Paths.get("./server/level.dat"))));
            dos.write(this.blocks);
            dos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setTile(int x, int y, int z, int id) {
        // Check if position is out of level
        if (x < 0 || y < 0 || z < 0 || x >= this.width || y >= this.depth || z >= this.height) {
            return;
        }

        // Set tile
        this.blocks[(y * this.height + z) * this.width + x] = (byte) id;
    }
}
