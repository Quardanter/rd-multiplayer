package client.net;

import client.Position;

import java.util.HashMap;
import java.util.Map;

public class PlayerManager {

    public Map<String, Position> players = new HashMap<>();

    public void updatePlayer(String username, double x, double y, double z) {
        if (players.containsKey(username)) {
            players.get(username).x = x;
            players.get(username).y = y;
            players.get(username).z = z;
        } else {
            players.put(username, new Position(x, y, z));
        }

        System.out.println("Player " + username + " is at X/Y/Z: " + x + ", " + y + ", " + z);
    }


    public void removePlayer(String username) {
        players.remove(username);
    }

    public Position getPlayer(String username) {
        return players.get(username);
    }

    public Map<String, Position> getPlayers() { return players; }
}