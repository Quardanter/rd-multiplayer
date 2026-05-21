package client.mods;

@FunctionalInterface
public interface ChatCommand {
    boolean handle(String args);
}