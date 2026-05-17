package server.commands;

import server.commands.Command;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {

    private final Map<String, Command> commands = new HashMap<>();

    public void register(Command command) {
        commands.put(command.name(), command);
    }

    public void execute(String input) {

        String[] split = input.split(" ");
        String label = split[0];

        Command command = commands.get(label);

        if (command == null) {
            System.out.println("Unknown command.");
            return;
        }

        String[] args = new String[split.length - 1];

        System.arraycopy(split, 1, args, 0, args.length);

        command.execute(args);
    }
}