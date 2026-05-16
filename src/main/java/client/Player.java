package client;

import client.level.Level;
import client.net.SocketClient;
import client.phys.AABB;
import global.Packets;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

public class Player {

    private final Level level;

    public double x, y, z;
    public double prevX, prevY, prevZ;
    public double motionX, motionY, motionZ;
    public float width, height;
    public float xRotation, yRotation;

    private boolean onGround;

    public AABB boundingBox;

    private final Object posLock = new Object();
    private boolean hasPendingReset = false;
    private double resetX, resetY, resetZ;

    private boolean flying = false;
    private long lastSpacePress = 0L;
    private static final long DOUBLE_SPACE_TIME = 300L;

    public Player(Level level) {
        this.level = level;

        resetPosition();
    }

    private void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;

        this.width = 0.3F;
        this.height = 0.9F;

        this.boundingBox = new AABB(x - width, y - height,
                z - width, x + width,
                y + height, z + width);
    }

    private void resetPosition() {
        float x, y, z;
        if (Minecraft.mc != null && Minecraft.mc.spawnReceived) {
            x = (float) Minecraft.mc.spawnX;
            y = (float) Minecraft.mc.spawnY;
            z = (float) Minecraft.mc.spawnZ;
        } else {
            x = 128.0F;
            y = (float) (this.level.depth + 3);
            z = 128.0F;
        }
        setPosition(x, y, z);
    }

    public void forcePosition(double targetX, double targetY, double targetZ) {
        synchronized (posLock) {
            this.resetX = targetX;
            this.resetY = targetY;
            this.resetZ = targetZ;
            this.hasPendingReset = true;
        }
    }

    public void turn(float x, float y) {
        this.yRotation += x * 0.15F;
        this.xRotation -= y * 0.15F;

        this.xRotation = Math.max(-90.0F, this.xRotation);
        this.xRotation = Math.min(90.0F, this.xRotation);
    }

    public void tick() throws IOException {
        synchronized (posLock) {
            if (hasPendingReset) {
                this.x = resetX;
                this.y = resetY;
                this.z = resetZ;
                this.prevX = resetX;
                this.prevY = resetY;
                this.prevZ = resetZ;
                this.motionX = 0.0D;
                this.motionY = 0.0D;
                this.motionZ = 0.0D;

                double minY = resetY - 1.62D;
                this.boundingBox = new AABB(resetX - width, minY, resetZ - width, resetX + width, minY + (2.0F * this.height), resetZ + width);
                this.hasPendingReset = false;
            }
        }

        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;

        float forward = 0.0F;
        float strafe = 0.0F;

        while (Keyboard.next()) {
            if (Keyboard.getEventKeyState()) {
                int key = Keyboard.getEventKey();

                if (key == Keyboard.KEY_T && !Minecraft.mc.chat.toggled) {
                    Minecraft.mc.chat.setToggled(true);
                    return;
                }

                if (key == Keyboard.KEY_SPACE && !Minecraft.mc.chat.toggled) {
                    long now = System.currentTimeMillis();

                    if (now - lastSpacePress <= DOUBLE_SPACE_TIME) {
                        flying = !flying;

                        this.motionX = 0.0D;
                        this.motionY = 0.0D;
                        this.motionZ = 0.0D;
                    }

                    lastSpacePress = now;
                }

                if (Minecraft.mc.chat.toggled) {
                    char c = Keyboard.getEventCharacter();
                    Minecraft.mc.chat.handleKey(key, c);
                }
            }
        }

        if (!Minecraft.mc.chat.toggled) {
            if (Keyboard.isKeyDown(Keyboard.KEY_W) || Keyboard.isKeyDown(Keyboard.KEY_UP)) forward--;
            if (Keyboard.isKeyDown(Keyboard.KEY_S) || Keyboard.isKeyDown(Keyboard.KEY_DOWN)) forward++;
            if (Keyboard.isKeyDown(Keyboard.KEY_A) || Keyboard.isKeyDown(Keyboard.KEY_LEFT)) strafe--;
            if (Keyboard.isKeyDown(Keyboard.KEY_D) || Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) strafe++;
        }

        if (flying) {
            float flySpeed = 0.12F;

            this.motionY = 0.0D;

            moveRelative(strafe, forward, flySpeed);

            if (Keyboard.isKeyDown(Keyboard.KEY_SPACE) && !Minecraft.mc.chat.toggled) {
                this.motionY += flySpeed;
            }

            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                    || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) && !Minecraft.mc.chat.toggled) {
                this.motionY -= flySpeed;
            }

            move(this.motionX, this.motionY, this.motionZ);

            this.motionX *= 0.6F;
            this.motionY *= 0.6F;
            this.motionZ *= 0.6F;
        } else {
            if ((Keyboard.isKeyDown(Keyboard.KEY_SPACE)
                    || Keyboard.isKeyDown(Keyboard.KEY_LWIN))
                    && this.onGround && !Minecraft.mc.chat.toggled) {
                this.motionY = 0.12F;
            }

            boolean sprinting = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                    || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

            float speed = sprinting ? 0.08F : 0.04F;

            moveRelative(strafe, forward, this.onGround ? speed : 0.01F);

            this.motionY -= 0.005D;

            move(this.motionX, this.motionY, this.motionZ);

            this.motionX *= 0.91F;
            this.motionY *= 0.98F;
            this.motionZ *= 0.91F;

            if (this.onGround) {
                this.motionX *= 0.8F;
                this.motionZ *= 0.8F;
            }
        }

        if (this.x != this.prevX || this.y != this.prevY || this.z != this.prevZ) {
            if (Minecraft.mc.socket.isConnected()) {
                SocketClient.sendPos(Packets.POS, this.x, this.y, this.z, this.yRotation, (int) Minecraft.mc.rtt);
            }
        }
    }

    public void move(double x, double y, double z) {
        double prevX = x;
        double prevY = y;
        double prevZ = z;

        List<AABB> aABBs = this.level.getCubes(this.boundingBox.expand(x, y, z));

        for (AABB abb : aABBs) {
            y = abb.clipYCollide(this.boundingBox, y);
        }

        this.boundingBox.move(0.0F, y, 0.0F);

        for (AABB aABB : aABBs) {
            x = aABB.clipXCollide(this.boundingBox, x);
        }

        this.boundingBox.move(x, 0.0F, 0.0F);

        for (AABB aABB : aABBs) {
            z = aABB.clipZCollide(this.boundingBox, z);
        }

        this.boundingBox.move(0.0F, 0.0F, z);

        this.onGround = prevY != y && prevY < 0.0F;

        if (prevX != x) this.motionX = 0.0D;
        if (prevY != y) this.motionY = 0.0D;
        if (prevZ != z) this.motionZ = 0.0D;

        this.x = (this.boundingBox.minX + this.boundingBox.maxX) / 2.0D;
        this.y = this.boundingBox.minY + 1.62D;
        this.z = (this.boundingBox.minZ + this.boundingBox.maxZ) / 2.0D;
    }

    private void moveRelative(float x, float z, float speed) {
        float distance = x * x + z * z;

        if (distance < 0.01F)
            return;

        distance = speed / (float) Math.sqrt(distance);
        x *= distance;
        z *= distance;

        double sin = Math.sin(Math.toRadians(this.yRotation));
        double cos = Math.cos(Math.toRadians(this.yRotation));

        this.motionX += x * cos - z * sin;
        this.motionZ += z * cos + x * sin;
    }

    public void sendPosition() throws IOException {
        if (Minecraft.mc.socket.isConnected()) {
            SocketClient.sendPos(Packets.POS, this.x, this.y, this.z, this.yRotation, (int) Minecraft.mc.rtt);
        }
    }
}