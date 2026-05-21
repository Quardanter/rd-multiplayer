package client.mods;

public final class ModEvents {
    private ModEvents() {}

    public static final class BlockBreak extends ModEvent {
        public final int x, y, z;
        public BlockBreak(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    public static final class BlockPlace extends ModEvent {
        public final int x, y, z;
        public final int blockId;
        public BlockPlace(int x, int y, int z, int id) { this.x = x; this.y = y; this.z = z; this.blockId = id; }
    }

    public static final class ChatReceived extends ModEvent {
        public final String author;
        public final String message;
        public ChatReceived(String author, String message) { this.author = author; this.message = message; }
    }

    public static final class PlayerConnection extends ModEvent {
        /** 0 = joined, 1 = left. */
        public final int type;
        public final String username;
        public PlayerConnection(int type, String username) { this.type = type; this.username = username; }
    }

    public static final class ServerConnected extends ModEvent {
        public final String host;
        public final int port;
        public ServerConnected(String host, int port) { this.host = host; this.port = port; }
    }

    public static final class ServerDisconnected extends ModEvent {
        public ServerDisconnected() {}
    }
}