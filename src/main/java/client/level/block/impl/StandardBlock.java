package client.level.block.impl;

import client.level.FaceTextures;
import client.level.block.Block;

public class StandardBlock extends Block {
    public StandardBlock(int id, String name, int atlasId) {
        super(id, name, atlasId);
    }

    public StandardBlock(int id, String name, FaceTextures faces) {
        super(id, name, faces);
    }

    @Override
    public void onPlace(int x, int y, int z) {}

    @Override
    public void onBreak(int x, int y, int z) {}
}
