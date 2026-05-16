package client.level;

public interface LevelListener {
    void lightColumnChanged(int x, int z, int minY, int maxY);
    void tileChanged(int x, int y, int z);
    void allChanged();
    void chunkLoaded(int cx, int cz);
    void chunkUnloaded(int cx, int cz);
}