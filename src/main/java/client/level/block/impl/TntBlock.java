package client.level.block.impl;

import client.level.FaceTextures;
import client.level.Level;
import client.level.Tessellator;
import client.level.block.Block;

public class TntBlock extends Block {
    public TntBlock(int id, String name, FaceTextures faces) {
        super(id, name, faces);
    }

    @Override
    public void render(Tessellator t, Level level, int layer, int x, int y, int z) {
        super.render(t, level, layer, x, y, z);
    }

    @Override
    public void onPlace(int x, int y, int z) {}

    @Override
    public void onBreak(int x, int y, int z) {}
}
