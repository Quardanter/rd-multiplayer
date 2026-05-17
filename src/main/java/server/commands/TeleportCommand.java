package server.commands;

import global.Packets;
import server.Server;
import server.client.Client;

import java.util.Optional;

public class TeleportCommand implements Command {
    @Override
    public String name() {
        return "tp";
    }

    @Override
    public void execute(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: tp <user> <x> <y> <z>");
            return;
        }

        String[] parts = args[0].split(" ", 4);
        String username = parts[0];
        Optional<Client> client = Server.getClient(username);

        try {
            client.ifPresent(c -> {
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                c.setLastPos(x, y, z, System.currentTimeMillis());
                c.send(o -> {
                    o.writeByte(Packets.SET_POS);
                    o.writeDouble(x);
                    o.writeDouble(y);
                    o.writeDouble(z);
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
