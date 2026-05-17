package client.level;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

public class Tessellator {

    private static final int MAX_VERTICES = 100000;

    private final FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer textureCoordinateBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 2);
    private final FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);
    private final FloatBuffer normalBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * 3);

    private int vertices = 0;

    private boolean hasTexture = false;
    private float textureU, textureV;

    private boolean hasColor = false;
    private float red, green, blue;

    private boolean hasNormal = false;
    private float normalX, normalY, normalZ;

    public void init() {
        clear();
    }

    public void vertex(float x, float y, float z) {
        vertexBuffer.put(vertices * 3,     x);
        vertexBuffer.put(vertices * 3 + 1, y);
        vertexBuffer.put(vertices * 3 + 2, z);

        if (hasTexture) {
            textureCoordinateBuffer.put(vertices * 2,     textureU);
            textureCoordinateBuffer.put(vertices * 2 + 1, textureV);
        }
        if (hasColor) {
            colorBuffer.put(vertices * 3,     red);
            colorBuffer.put(vertices * 3 + 1, green);
            colorBuffer.put(vertices * 3 + 2, blue);
        }
        if (hasNormal) {
            normalBuffer.put(vertices * 3,     normalX);
            normalBuffer.put(vertices * 3 + 1, normalY);
            normalBuffer.put(vertices * 3 + 2, normalZ);
        }

        vertices++;
        if (vertices == MAX_VERTICES) flush();
    }

    public void texture(float u, float v) {
        hasTexture = true;
        textureU = u;
        textureV = v;
    }

    public void color(float r, float g, float b) {
        hasColor = true;
        red = r;
        green = g;
        blue = b;
    }

    public void normal(float nx, float ny, float nz) {
        hasNormal = true;
        normalX = nx;
        normalY = ny;
        normalZ = nz;
    }

    public void flush() {
        vertexBuffer.flip();
        textureCoordinateBuffer.flip();
        colorBuffer.flip();
        normalBuffer.flip();

        glVertexPointer(3, 0, vertexBuffer);
        if (hasTexture) glTexCoordPointer(2, 0, textureCoordinateBuffer);
        if (hasColor)   glColorPointer(3, 0, colorBuffer);
        if (hasNormal)  glNormalPointer(0, normalBuffer);

        glEnableClientState(GL_VERTEX_ARRAY);
        if (hasTexture) glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        if (hasColor)   glEnableClientState(GL_COLOR_ARRAY);
        if (hasNormal)  glEnableClientState(GL_NORMAL_ARRAY);

        glDrawArrays(GL_QUADS, 0, vertices);

        glDisableClientState(GL_VERTEX_ARRAY);
        if (hasTexture) glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        if (hasColor)   glDisableClientState(GL_COLOR_ARRAY);
        if (hasNormal)  glDisableClientState(GL_NORMAL_ARRAY);

        clear();
    }

    private void clear() {
        vertexBuffer.clear();
        textureCoordinateBuffer.clear();
        colorBuffer.clear();
        normalBuffer.clear();

        vertices = 0;
        hasTexture = false;
        hasColor = false;
        hasNormal = false;
    }
}