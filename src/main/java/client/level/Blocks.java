package client.level;

public final class Blocks {

    public static final Tile AIR = null;
    public static final Tile GRASS = new Tile(1, "Grass");
    public static final Tile COBBLE = new Tile(2, "Cobblestone");
    public static final Tile DIRT = new Tile(3, "Dirt");
    public static final Tile OBSIDIAN = new Tile(4, "Obsidian");
    public static final Tile SAND = new Tile(5, "Sand");
    public static final Tile BRICKS = new Tile(6, "Bricks");
    public static final Tile TNT = new Tile(7, "TNT", FaceTextures.column(8, 6, 7));

    //these mfs have to be in order!!
    private static final Tile[] TILES = { AIR, GRASS, COBBLE, DIRT, OBSIDIAN, SAND, BRICKS, TNT };

    public static Tile get(int id) {
        return id > 0 && id < TILES.length ? TILES[id] : null;
    }

    private Blocks() {}
}