package client.hud;

import client.FontRenderer;
import client.Minecraft;
import client.net.SocketClient;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class Chat {
    private static final int INPUT_HEIGHT = 20;
    private static final int LINE_HEIGHT = 21;

    private static final long MESSAGE_LIFETIME = 5000L;

    private static final long BACKSPACE_DELAY = 400L;
    private static final long BACKSPACE_REPEAT = 50L;
    private static final long KEY_DELAY = 400L;
    private static final long KEY_REPEAT = 50L;

    private final FontRenderer font;
    private final List<ChatMessage> messages = new ArrayList<>();

    private final int maxMessages;

    private final int x, y, width, height;

    private String input = "";

    public boolean toggled;

    private boolean selected;

    private long backspaceStart;
    private long lastBackspace;

    private int heldKey = 0;
    private char heldChar = 0;
    private long heldStart = 0;
    private long lastHeld = 0;

    public Chat(FontRenderer font, int maxMessages, int x, int y, int width, int height) {
        this.font = font;
        this.maxMessages = maxMessages;

        this.x = x;
        this.y = y;

        this.width = width;
        this.height = height;
    }

    public void render(int displayWidth, int displayHeight) {
        if(!Minecraft.mc.info.hudEnabled) return;
        handleBackspace();
        handleKeyRepeat();

        setupRender(displayWidth, displayHeight);

        if (toggled) {
            renderInput(displayWidth, displayHeight);
        }

        renderMessages(displayHeight);

        endRender();
    }

    private void renderInput(int displayWidth, int displayHeight) {
        drawRect(0, displayHeight - INPUT_HEIGHT, displayWidth, INPUT_HEIGHT, 0f, 0f, 0f, 0.3f);

        if (selected && !input.isEmpty()) {
            int start = 5 + font.getStringWidth("> ");
            int end = start + font.getStringWidth(input);

            drawRect(start, displayHeight - INPUT_HEIGHT + 2, end - start, INPUT_HEIGHT - 4, 0.2f, 0.6f, 1f, 0.5f);
        }

        font.drawString("> " + input, 5, displayHeight - INPUT_HEIGHT - 1, true);
    }

    private void renderMessages(int displayHeight) {
        int offsetY = displayHeight - 50;

        long now = System.currentTimeMillis();

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);

            if (!toggled && now - msg.time > MESSAGE_LIFETIME) {
                continue;
            }

            String text = msg.system ? msg.author + " " + msg.message : "<" + msg.author + "> " + msg.message;

            List<String> lines = wrap(text, width - 10);

            for (int j = lines.size() - 1; j >= 0; j--) {
                drawRect(0, offsetY, width, LINE_HEIGHT, 0f, 0f, 0f, 0.3f);

                font.drawString(lines.get(j), 5, offsetY - 1, msg.system ? Color.YELLOW : Color.WHITE, true);

                offsetY -= LINE_HEIGHT;

                if (offsetY < 0) {
                    return;
                }
            }
        }
    }

    public void handleKey(int key, char character) throws IOException {
        if (!toggled) {
            return;
        }

        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (ctrl) {
            switch (key) {
                case Keyboard.KEY_A:
                    selected = true;
                    return;

                case Keyboard.KEY_C:
                    copy(input);
                    return;

                case Keyboard.KEY_V:
                    if (selected) {
                        input = "";
                        selected = false;
                    }

                    input += paste();
                    return;
            }
        }

        switch (key) {

            case Keyboard.KEY_RETURN:
                sendMessage();
                return;

            case Keyboard.KEY_ESCAPE:
                close();
                return;

            case Keyboard.KEY_BACK:
                backspace();

                backspaceStart = System.currentTimeMillis();
                lastBackspace = backspaceStart;
                return;
        }

        if (!ctrl && Character.isDefined(character) && !Character.isISOControl(character)) {

            if (selected) {
                input = "";
                selected = false;
            }

            input += character;

            heldKey = key;
            heldChar = character;
            heldStart = System.currentTimeMillis();
            lastHeld = heldStart;
        } else {
            heldKey = 0;
            heldChar = 0;
            heldStart = 0;
        }
    }

    private void sendMessage() throws IOException {
        if (!input.isEmpty()) {
            SocketClient.sendChat(Minecraft.mc.username, input);
            input = "";
        }

        toggled = false;
        selected = false;
    }

    private void close() {
        input = "";
        toggled = false;
        selected = false;
    }

    private void backspace() {
        if (selected) {
            input = "";
            selected = false;
            return;
        }

        if (!input.isEmpty()) {
            input = input.substring(0, input.length() - 1);
        }
    }

    private void handleKeyRepeat() {
        if (!toggled || selected || heldKey == 0 || !Keyboard.isKeyDown(heldKey)) {
            heldKey = 0;
            heldChar = 0;
            heldStart = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (heldStart == 0 || now - heldStart < KEY_DELAY) return;

        if (now - lastHeld >= KEY_REPEAT) {
            input += heldChar;
            lastHeld = now;
        }
    }

    private void handleBackspace() {
        if (!toggled || !Keyboard.isKeyDown(Keyboard.KEY_BACK) || selected) {
            backspaceStart = 0;
            return;
        }

        long now = System.currentTimeMillis();

        if (backspaceStart == 0 || now - backspaceStart < BACKSPACE_DELAY) {
            return;
        }

        if (now - lastBackspace >= BACKSPACE_REPEAT) {

            if (!input.isEmpty()) {
                input = input.substring(0, input.length() - 1);
            }

            lastBackspace = now;
        }
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {

            String test = line.length() == 0
                    ? word
                    : line + " " + word;

            if (font.getStringWidth(test) <= maxWidth) {
                if (line.length() > 0) {
                    line.append(" ");
                }

                line.append(word);
                continue;
            }

            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }

            for (char c : word.toCharArray()) {

                if (font.getStringWidth(line.toString() + c) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }

                line.append(c);
            }
        }

        if (line.length() > 0) {
            lines.add(line.toString());
        }

        return lines;
    }

    private void drawRect(float x, float y, float width, float height, float r, float g, float b, float a) {
        glDisable(GL_TEXTURE_2D);
        glColor4f(r, g, b, a);

        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();

        glEnable(GL_TEXTURE_2D);
    }

    private void setupRender(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();

        glOrtho(0, width, height, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void endRender() {
        glDisable(GL_BLEND);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glPopMatrix();

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();

        glMatrixMode(GL_MODELVIEW);
    }

    private String paste() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void copy(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (Exception ignored) {
        }
    }

    public void addMessage(String author, String message) {
        add(new ChatMessage(author, message, false));
    }

    public void addMessage(String author, String message, boolean system) {
        add(new ChatMessage(author, message, system));
    }

    public void addConnectionMessage(String player, int type) {
        add(new ChatMessage(
                player,
                (type == 0 ? "joined" : "left") + " the game",
                true
        ));
    }

    private void add(ChatMessage message) {
        messages.add(message);

        while (messages.size() > maxMessages) {
            messages.remove(0);
        }
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;

        if (!toggled) {
            selected = false;
        }
    }

    public static class ChatMessage {
        public final String author;
        public final String message;

        public final boolean system;
        public final long time = System.currentTimeMillis();

        public ChatMessage(String author, String message, boolean system) {
            this.author = author;
            this.message = message;
            this.system = system;
        }
    }
}