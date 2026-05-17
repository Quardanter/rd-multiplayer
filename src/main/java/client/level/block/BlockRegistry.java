package client.level.block;

import client.level.FaceTextures;
import client.level.block.impl.StandardBlock;
import client.level.block.impl.TntBlock;

public final class BlockRegistry {
    public static final Block AIR = null;
    public static final Block GRASS = new StandardBlock(1, "Grass", FaceTextures.column(0,1,3));
    public static final Block COBBLE = new StandardBlock(2, "Cobblestone", 2);
    public static final Block DIRT = new StandardBlock(3, "Dirt",3);
    public static final Block OBSIDIAN = new StandardBlock(4, "Obsidian", 4);
    public static final Block SAND = new StandardBlock(5, "Sand", 5);
    public static final Block BRICKS = new StandardBlock(6, "Bricks", 6);
    public static final Block TNT = new TntBlock(7, "TNT", FaceTextures.column(9, 7, 8));

    //these mfs have to be in order!!
    private static final Block[] BLOCKS = { AIR, GRASS, COBBLE, DIRT, OBSIDIAN, SAND, BRICKS, TNT };

    public static Block get(int id) {
        return id > 0 && id < BLOCKS.length ? BLOCKS[id] : null;
    }

    private BlockRegistry() {}
}