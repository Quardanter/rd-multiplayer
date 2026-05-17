package client.player.remote;

import java.util.HashMap;
import java.util.Map;

public class PlayerManager {

    public Map<String, RemotePlayer> players = new HashMap<>();

    public synchronized void updatePlayer(String username, double x, double y, double z, float yaw, float pitch) {
        RemotePlayer p = players.get(username);
        if (p == null) {
            p = new RemotePlayer(username, x, y, z, yaw, 0);
            players.put(username, p);
        }
        p.x = x; p.y = y; p.z = z;
        p.yaw = yaw; p.pitch = pitch;
    }

    public synchronized void updatePing(String username, int ping) {
        RemotePlayer p = players.get(username);
        if (p == null) {
            p = new RemotePlayer(username, 0, 0, 0, 0f, ping);
            players.put(username, p);
        } else {
            p.ping = ping;
        }
    }

    public synchronized void setPendingSkin(String username, byte[] png) {
        RemotePlayer p = players.get(username);
        if (p == null) {
            p = new RemotePlayer(username,0, 0, 0, 0f, 0);
            players.put(username, p);
        }
        p.pendingSkinPng = png;
    }

    public synchronized void removePlayer(String username) {
        players.remove(username);
    }

    public RemotePlayer getPlayer(String username) {
        return players.get(username);
    }

    public Map<String, RemotePlayer> getPlayers() { return players; }
}