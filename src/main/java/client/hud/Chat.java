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
    public List<ChatMessage> messages;
    private int messageLimit;
    private String chatInput = "";
    private FontRenderer fontRenderer;
    private int w, h, x, y;
    public boolean toggled = false;

    private boolean isSelected = false;

    private long backspacePressedTime = 0;
    private long lastBackspaceTime = 0;
    private static final long INITIAL_BACKSPACE_DELAY = 400;
    private static final long REPEAT_BACKSPACE_DELAY = 50;

    public Chat(FontRenderer fontRenderer, int messageLimit, int x, int y, int w, int h) {
        this.fontRenderer = fontRenderer;
        this.messageLimit = messageLimit;
        this.w = w;
        this.h = h;
        this.x = x;
        this.y = y;
        this.toggled = false;
        this.messages = new ArrayList<>();
    }

    public void render(int dW, int dH) {
        handleBackspace();

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, dW, dH, 0, -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (toggled) {
            int inputBarHeight = 20;

            glDisable(GL_TEXTURE_2D);
            glColor4f(0f, 0f, 0f, 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(0, dH - inputBarHeight);
            glVertex2f(dW, dH - inputBarHeight);
            glVertex2f(dW, dH);
            glVertex2f(0, dH);
            glEnd();
            glEnable(GL_TEXTURE_2D);

            int inputTextY = (int) ((dH - inputBarHeight) - 1);

            if (isSelected && !chatInput.isEmpty()) {
                int prefixWidth = fontRenderer.getStringWidth("> ");
                int textWidth = fontRenderer.getStringWidth(chatInput);

                int selectX1 = 5 + prefixWidth;
                int selectX2 = selectX1 + textWidth;
                int selectY1 = dH - inputBarHeight + 2;
                int selectY2 = dH - 2;

                glDisable(GL_TEXTURE_2D);
                glColor4f(0.2f, 0.6f, 1.0f, 0.5f);
                glBegin(GL_QUADS);
                glVertex2f(selectX1, selectY1);
                glVertex2f(selectX2, selectY1);
                glVertex2f(selectX2, selectY2);
                glVertex2f(selectX1, selectY2);
                glEnd();
                glEnable(GL_TEXTURE_2D);
            }

            fontRenderer.drawString("> " + chatInput, 5, inputTextY, true);
        }

        int lineHeight = 21;
        int offsetY = dH - 20 - 30;
        long now = System.currentTimeMillis();
        long messageLifetime = 5000;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);

            if (!toggled && now - msg.timestamp > messageLifetime) continue;

            String fullText = msg.connectionMessage ?
                    msg.author + " " + msg.message :
                    "<" + msg.author + "> " + msg.message;

            List<String> wrappedLines = wrapText(fullText, w - 10);

            for (int j = wrappedLines.size() - 1; j >= 0; j--) {
                float boxTop = offsetY;
                float boxBottom = offsetY + lineHeight;

                glDisable(GL_TEXTURE_2D);
                glColor4f(0f, 0f, 0f, 0.3f);
                glBegin(GL_QUADS);
                glVertex2f(0, boxTop);
                glVertex2f(w, boxTop);
                glVertex2f(w, boxBottom);
                glVertex2f(0, boxBottom);
                glEnd();
                glEnable(GL_TEXTURE_2D);

                int msgTextY = (int) (boxTop - 1);

                Color color = msg.connectionMessage ? Color.YELLOW : Color.WHITE;
                fontRenderer.drawString(wrappedLines.get(j), 5, msgTextY, color, true);

                offsetY -= lineHeight;
                if (offsetY < 0) break;
            }

            if (offsetY < 0) break;
        }

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);

        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    public void handleKey(int key, char character) throws IOException {
        if (!toggled) return;

        boolean isCtrlDown = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

        if (isCtrlDown) {
            if (key == Keyboard.KEY_A) {
                isSelected = true;
                return;
            }
            if (key == Keyboard.KEY_C) {
                setClipboardString(chatInput);
                return;
            }
            if (key == Keyboard.KEY_V) {
                if (isSelected) {
                    chatInput = "";
                    isSelected = false;
                }
                chatInput += getClipboardString();
                return;
            }
        }

        switch (key) {
            case Keyboard.KEY_RETURN:
                if (!chatInput.isEmpty()) {
                    SocketClient.sendChat(Minecraft.mc.username, chatInput);
                    chatInput = "";
                }
                toggled = false;
                isSelected = false;
                break;

            case Keyboard.KEY_ESCAPE:
                chatInput = "";
                toggled = false;
                isSelected = false;
                break;

            case Keyboard.KEY_BACK:
                if (isSelected) {
                    chatInput = "";
                    isSelected = false;
                } else if (!chatInput.isEmpty()) {
                    chatInput = chatInput.substring(0, chatInput.length() - 1);
                }
                backspacePressedTime = System.currentTimeMillis();
                lastBackspaceTime = System.currentTimeMillis();
                break;

            default:
                if (!isCtrlDown && Character.isDefined(character) && !Character.isISOControl(character)) {
                    if (isSelected) {
                        chatInput = "";
                        isSelected = false;
                    }
                    chatInput += character;
                }
                break;
        }
    }

    private void handleBackspace() {
        if (toggled && Keyboard.isKeyDown(Keyboard.KEY_BACK)) {
            if (isSelected) return;

            long now = System.currentTimeMillis();
            long timeElapsed = now - backspacePressedTime;

            if (timeElapsed > INITIAL_BACKSPACE_DELAY) {
                if (now - lastBackspaceTime > REPEAT_BACKSPACE_DELAY) {
                    if (!chatInput.isEmpty()) {
                        chatInput = chatInput.substring(0, chatInput.length() - 1);
                    }
                    lastBackspaceTime = now;
                }
            }
        } else {
            backspacePressedTime = 0;
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        String[] words = text.split(" ");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            if (i > 0) {
                if (fontRenderer.getStringWidth(currentLine.toString() + " ") > maxWidth) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                } else {
                    currentLine.append(" ");
                }
            }

            for (int j = 0; j < word.length(); j++) {
                char c = word.charAt(j);

                if (fontRenderer.getStringWidth(currentLine.toString() + c) > maxWidth) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder();
                }

                currentLine.append(c);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private String getClipboardString() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return "";
        }
    }

    private void setClipboardString(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        } catch (Exception ignored) {}
    }

    public void addMessage(String author, String message) {
        ChatMessage msg = new ChatMessage(author, message, false);
        messages.add(msg);

        if (messages.size() >= messageLimit) {
            messages.remove(0);
        }
    }

    public void addConnectionMessage(String player, int type) {
        String action = (type == 0) ? "joined" : "left";
        ChatMessage msg = new ChatMessage(player, action + " the game", true);
        messages.add(msg);

        if (messages.size() >= messageLimit) {
            messages.remove(0);
        }
    }

    public static class ChatMessage {
        public String author;
        public String message;
        public long timestamp;
        public boolean connectionMessage;

        public ChatMessage(String author, String message, boolean connectionMessage) {
            this.author = author;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.connectionMessage = connectionMessage;
        }
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
        if (!toggled) {
            this.isSelected = false;
        }
    }
}