package client.net;

import client.Position;

import java.util.HashMap;
import java.util.Map;

public class PlayerManager {

    public Map<String, Position> players = new HashMap<>();

    public void updatePlayer(String username, double x, double y, double z, float yaw, int ping) {
        if (players.containsKey(username)) {
            Position p = players.get(username);
            p.x = x; p.y = y; p.z = z; p.yaw = yaw; p.ping = ping;
        } else {
            players.put(username, new Position(x, y, z, yaw, ping));
        }
    }

    public void removePlayer(String username) {
        players.remove(username);
    }

    public Position getPlayer(String username) {
        return players.get(username);
    }

    public Map<String, Position> getPlayers() { return players; }
}