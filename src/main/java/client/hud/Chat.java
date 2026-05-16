package client.hud;

import client.FontRenderer;
import client.Minecraft;
import client.net.SocketClient;
import org.lwjgl.input.Keyboard;

import java.awt.*;
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

            int inputTextY = (int) ((dH - inputBarHeight)-1);
            fontRenderer.drawString("> " + chatInput, 5, inputTextY, true);
        }

        int lineHeight = 21;
        int offsetY = dH - 20 - 30;
        long now = System.currentTimeMillis();
        long messageLifetime = 5000;

        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);

            if (!toggled && now - msg.timestamp > messageLifetime) continue;

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

            int msgTextY = (int) (boxTop-1);
            if (msg.connectionMessage) {
                fontRenderer.drawString(msg.author + " " + msg.message, 5, msgTextY, Color.YELLOW, true);
            } else {
                fontRenderer.drawString("<" + msg.author + "> " + msg.message, 5, msgTextY, true);
            }

            offsetY -= lineHeight;
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

    private long lastBackspaceTime = 0;
    private long backspaceDelay = 100;

    public void handleKey(int key, char character) throws IOException {
        if (!toggled) return;

        switch (key) {
            case 28:
                if (!chatInput.isEmpty()) {
                    SocketClient.sendChat(Minecraft.mc.username, chatInput);
                    chatInput = "";
                }
                toggled = false;
                break;

            case 1:
                chatInput = "";
                toggled = false;
                break;

            case 14:
                long now = System.currentTimeMillis();
                if (now - lastBackspaceTime > backspaceDelay && !chatInput.isEmpty()) {
                    chatInput = chatInput.substring(0, chatInput.length() - 1);
                    lastBackspaceTime = now;
                }
                break;

            default:
                if (Character.isDefined(character) && !Character.isISOControl(character)) {
                    chatInput += character;
                }
                break;
        }
    }

    public void addMessage(String author, String message) {
        ChatMessage msg = new ChatMessage(author, message, false);
        messages.add(msg);

        if (messages.size() >= messageLimit) {
            messages.remove(0);
        }
    }

    public void addConnectionMessage(String player, int type) {
        // 0 = connect, 1 = disconnect
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
    }
}
