package client.gfx;

import org.lwjgl.opengl.ARBVertexBufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class GL {
    private GL() {}

    // primitives
    public static final int QUADS = GL11.GL_QUADS;
    public static final int TRIANGLES = GL11.GL_TRIANGLES;
    public static final int LINES = GL11.GL_LINES;

    // data types
    public static final int FLOAT = GL11.GL_FLOAT;
    public static final int UNSIGNED_BYTE = GL11.GL_UNSIGNED_BYTE;
    public static final int UNSIGNED_INT = GL11.GL_UNSIGNED_INT;

    // capabilities
    public static final int TEXTURE_2D = GL11.GL_TEXTURE_2D;
    public static final int BLEND = GL11.GL_BLEND;
    public static final int DEPTH_TEST = GL11.GL_DEPTH_TEST;
    public static final int CULL_FACE = GL11.GL_CULL_FACE;
    public static final int LIGHTING = GL11.GL_LIGHTING;
    public static final int LIGHT0 = GL11.GL_LIGHT0;
    public static final int COLOR_MATERIAL = GL11.GL_COLOR_MATERIAL;
    public static final int FOG = GL11.GL_FOG;
    public static final int ALPHA_TEST = GL11.GL_ALPHA_TEST;

    // blend factors
    public static final int SRC_ALPHA = GL11.GL_SRC_ALPHA;
    public static final int ONE_MINUS_SRC_ALPHA = GL11.GL_ONE_MINUS_SRC_ALPHA;
    public static final int ONE = GL11.GL_ONE;
    public static final int ZERO = GL11.GL_ZERO;
    public static final int CURRENT_BIT = GL11.GL_CURRENT_BIT;

    // depth / shading
    public static final int LEQUAL = GL11.GL_LEQUAL;
    public static final int LESS = GL11.GL_LESS;
    public static final int ALWAYS = GL11.GL_ALWAYS;
    public static final int SMOOTH = GL11.GL_SMOOTH;
    public static final int FLAT = GL11.GL_FLAT;

    // clear / buffer bits
    public static final int COLOR_BUFFER_BIT = GL11.GL_COLOR_BUFFER_BIT;
    public static final int DEPTH_BUFFER_BIT = GL11.GL_DEPTH_BUFFER_BIT;
    public static final int STENCIL_BUFFER_BIT = GL11.GL_STENCIL_BUFFER_BIT;

    // vertex / array client state
    public static final int VERTEX_ARRAY = GL11.GL_VERTEX_ARRAY;
    public static final int NORMAL_ARRAY = GL11.GL_NORMAL_ARRAY;
    public static final int COLOR_ARRAY = GL11.GL_COLOR_ARRAY;
    public static final int TEXTURE_COORD_ARRAY = GL11.GL_TEXTURE_COORD_ARRAY;

    // texture filtering
    public static final int NEAREST = GL11.GL_NEAREST;
    public static final int LINEAR = GL11.GL_LINEAR;

    // texture parameters
    public static final int TEXTURE_MIN_FILTER = GL11.GL_TEXTURE_MIN_FILTER;
    public static final int TEXTURE_MAG_FILTER = GL11.GL_TEXTURE_MAG_FILTER;
    public static final int TEXTURE_WRAP_S = GL11.GL_TEXTURE_WRAP_S;
    public static final int TEXTURE_WRAP_T = GL11.GL_TEXTURE_WRAP_T;
    public static final int REPEAT = GL11.GL_REPEAT;
    public static final int CLAMP = GL11.GL_CLAMP;

    // Light model / lighting params
    public static final int LIGHT_MODEL_LOCAL_VIEWER = GL11.GL_LIGHT_MODEL_LOCAL_VIEWER;
    public static final int LIGHT_MODEL_TWO_SIDE = GL11.GL_LIGHT_MODEL_TWO_SIDE;
    public static final int LIGHT_MODEL_AMBIENT = GL11.GL_LIGHT_MODEL_AMBIENT;
    public static final int FRONT = GL11.GL_FRONT;
    public static final int BACK = GL11.GL_BACK;
    public static final int FRONT_AND_BACK = GL11.GL_FRONT_AND_BACK;
    public static final int AMBIENT_AND_DIFFUSE = GL11.GL_AMBIENT_AND_DIFFUSE;
    public static final int POSITION = GL11.GL_POSITION;
    public static final int AMBIENT = GL11.GL_AMBIENT;
    public static final int DIFFUSE = GL11.GL_DIFFUSE;
    public static final int SPECULAR = GL11.GL_SPECULAR;

    // Pixel formats
    public static final int RGBA = GL11.GL_RGBA;
    public static final int RGB = GL11.GL_RGB;

    // selection / picking
    public static final int SELECT = GL11.GL_SELECT;
    public static final int RENDER = GL11.GL_RENDER;

    // matrix stacks
    public static final int MODELVIEW = GL11.GL_MODELVIEW;
    public static final int PROJECTION = GL11.GL_PROJECTION;
    public static final int TEXTURE = GL11.GL_TEXTURE;

    // buffer objects
    public static final int ARRAY_BUFFER = ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB;
    public static final int STATIC_DRAW = ARBVertexBufferObject.GL_STATIC_DRAW_ARB;
    public static final int DYNAMIC_DRAW = ARBVertexBufferObject.GL_DYNAMIC_DRAW_ARB;
    public static final int STREAM_DRAW = ARBVertexBufferObject.GL_STREAM_DRAW_ARB;

    // capability state
    public static void enable(int cap) { GL11.glEnable(cap); }
    public static void disable(int cap) { GL11.glDisable(cap); }
    public static void enableClientState(int cap) { GL11.glEnableClientState(cap); }
    public static void disableClientState(int cap) { GL11.glDisableClientState(cap); }

    // clearing
    public static void clear(int mask) { GL11.glClear(mask); }
    public static void clearColor(float r, float g, float b, float a) { GL11.glClearColor(r, g, b, a); }
    public static void clearDepth(double d) { GL11.glClearDepth(d); }

    // depth / blending / shading
    public static void depthFunc(int f) { GL11.glDepthFunc(f); }
    public static void depthMask(boolean enabled) { GL11.glDepthMask(enabled); }
    public static void blendFunc(int sfactor, int dfactor) { GL11.glBlendFunc(sfactor, dfactor); }
    public static void shadeModel(int model) { GL11.glShadeModel(model); }
    public static void alphaFunc(int func, float ref) { GL11.glAlphaFunc(func, ref); }

    // immediate mode primitives
    public static void begin(int mode) { GL11.glBegin(mode); }
    public static void end() { GL11.glEnd(); }
    public static void vertex3f(float x, float y, float z) { GL11.glVertex3f(x, y, z); }
    public static void color4f(float r, float g, float b, float a) { GL11.glColor4f(r, g, b, a); }
    public static void color3f(float r, float g, float b) { GL11.glColor3f(r, g, b); }
    public static void normal3f(float x, float y, float z) { GL11.glNormal3f(x, y, z); }
    public static void texCoord2f(float u, float v) { GL11.glTexCoord2f(u, v); }

    // vertex / texcoord / color / normal pointers (both client-array and VBO offset forms)
    public static void vertexPointer(int size, int stride, FloatBuffer buf) { GL11.glVertexPointer(size, stride, buf); }
    public static void normalPointer(int stride, FloatBuffer buf) { GL11.glNormalPointer(stride, buf); }
    public static void colorPointer(int size, int stride, FloatBuffer buf) { GL11.glColorPointer(size, stride, buf); }
    public static void texCoordPointer(int size, int stride, FloatBuffer buf) { GL11.glTexCoordPointer(size, stride, buf); }

    // VBO offset
    public static void vertexPointer(int size, int type, int stride, long offset) { GL11.glVertexPointer(size, type, stride, offset); }
    public static void normalPointer(int type, int stride, long offset) { GL11.glNormalPointer(type, stride, offset); }
    public static void colorPointer(int size, int type, int stride, long offset) { GL11.glColorPointer(size, type, stride, offset); }
    public static void texCoordPointer(int size, int type, int stride, long offset) { GL11.glTexCoordPointer(size, type, stride, offset); }

    // drawing
    public static void drawArrays(int mode, int first, int count) { GL11.glDrawArrays(mode, first, count); }
    public static void drawElements(int mode, IntBuffer indices) { GL11.glDrawElements(mode, indices); }

    // texture binding / params / lifecycle
    public static int genTexture() { return GL11.glGenTextures(); }
    public static void deleteTexture(int id) { GL11.glDeleteTextures(id); }
    public static void bindTexture(int id) { GL11.glBindTexture(TEXTURE_2D, id); }
    public static void bindTexture(int target, int id) { GL11.glBindTexture(target, id); }
    public static void texParameteri(int target, int pname, int value) { GL11.glTexParameteri(target, pname, value); }
    public static void texImage2D(int target, int level, int internalFmt, int w, int h, int border, int fmt, int type, ByteBuffer pixels) { GL11.glTexImage2D(target, level, internalFmt, w, h, border, fmt, type, pixels);
    }

    // lighting / fog / color material
    public static void lightModel(int pname, FloatBuffer params) { GL11.glLightModel(pname, params); }
    public static void lightModeli(int pname, int value) { GL11.glLightModeli(pname, value); }
    public static void light(int light, int pname, FloatBuffer params) { GL11.glLight(light, pname, params); }
    public static void colorMaterial(int face, int mode) { GL11.glColorMaterial(face, mode); }
    public static void fogi(int pname, int value) { GL11.glFogi(pname, value); }
    public static void fogf(int pname, float value) { GL11.glFogf(pname, value); }
    public static void fog (int pname, FloatBuffer params) { GL11.glFog(pname, params); }

    // matrix / viewport / projection
    public static void viewport(int x, int y, int w, int h) { GL11.glViewport(x, y, w, h); }
    public static void matrixMode(int mode) { GL11.glMatrixMode(mode); }
    public static void loadIdentity() { GL11.glLoadIdentity(); }
    public static void pushMatrix() { GL11.glPushMatrix(); }
    public static void popMatrix() { GL11.glPopMatrix(); }
    public static void translatef(float x, float y, float z) { GL11.glTranslatef(x, y, z); }
    public static void rotatef(float a, float x, float y, float z){ GL11.glRotatef(a, x, y, z); }
    public static void scalef(float x, float y, float z) { GL11.glScalef(x, y, z); }
    public static void ortho(double l, double r, double b, double t, double n, double f) { GL11.glOrtho(l, r, b, t, n, f); }
    public static void frustum(double l, double r, double b, double t, double n, double f) { GL11.glFrustum(l, r, b, t, n, f); }

    // selection / picking
    public static void selectBuffer(IntBuffer buf) { GL11.glSelectBuffer(buf); }
    public static int renderMode(int mode) { return GL11.glRenderMode(mode); }
    public static void initNames() { GL11.glInitNames(); }
    public static void pushName(int name) { GL11.glPushName(name); }
    public static void popName() { GL11.glPopName(); }

    // pixel readbacks
    public static void readPixels(int x, int y, int w, int h, int fmt, int type, ByteBuffer dst) {
        GL11.glReadPixels(x, y, w, h, fmt, type, dst);
    }

    // buffer objects
    public static int genBuffer() { return ARBVertexBufferObject.glGenBuffersARB(); }
    public static void deleteBuffer(int id) { ARBVertexBufferObject.glDeleteBuffersARB(id); }
    public static void bindBuffer(int target, int id) { ARBVertexBufferObject.glBindBufferARB(target, id); }
    public static void bufferData(int target, FloatBuffer data, int usage) {
        ARBVertexBufferObject.glBufferDataARB(target, data, usage);
    }
    public static void bufferData(int target, ByteBuffer data, int usage) {
        ARBVertexBufferObject.glBufferDataARB(target, data, usage);
    }

    // shader pipeline (GL 2.0 / GLSL 1.20)
    public static final int VERTEX_SHADER = GL20.GL_VERTEX_SHADER;
    public static final int FRAGMENT_SHADER = GL20.GL_FRAGMENT_SHADER;
    public static final int COMPILE_STATUS = GL20.GL_COMPILE_STATUS;
    public static final int LINK_STATUS = GL20.GL_LINK_STATUS;
    public static final int INFO_LOG_LENGTH = GL20.GL_INFO_LOG_LENGTH;

    public static boolean hasShaderSupport() {
        ContextCapabilities caps = GLContext.getCapabilities();
        return caps.OpenGL20;
    }

    public static int createShader(int type) { return GL20.glCreateShader(type); }
    public static void shaderSource(int shader, CharSequence source) { GL20.glShaderSource(shader, source); }
    public static void compileShader(int shader) { GL20.glCompileShader(shader); }
    public static int getShaderi(int shader, int pname) { return GL20.glGetShaderi(shader, pname); }
    public static String getShaderInfoLog(int shader, int maxLen) { return GL20.glGetShaderInfoLog(shader, maxLen); }
    public static void deleteShader(int shader) { GL20.glDeleteShader(shader); }

    public static int createProgram() { return GL20.glCreateProgram(); }
    public static void attachShader(int program, int shader) { GL20.glAttachShader(program, shader); }
    public static void linkProgram(int program) { GL20.glLinkProgram(program); }
    public static int getProgrami(int program, int pname) { return GL20.glGetProgrami(program, pname); }
    public static String getProgramInfoLog(int program, int maxLen) { return GL20.glGetProgramInfoLog(program, maxLen); }
    public static void useProgram(int program) { GL20.glUseProgram(program); }
    public static void deleteProgram(int program) { GL20.glDeleteProgram(program); }

    public static int getUniformLocation(int program, CharSequence name) { return GL20.glGetUniformLocation(program, name); }
    public static int getAttribLocation(int program, CharSequence name) { return GL20.glGetAttribLocation(program, name); }
    public static void bindAttribLocation(int program, int index, CharSequence name) { GL20.glBindAttribLocation(program, index, name); }

    public static void uniform1i(int loc, int v) { GL20.glUniform1i(loc, v); }
    public static void uniform1f(int loc, float v) { GL20.glUniform1f(loc, v); }
    public static void uniform3f(int loc, float a, float b, float c) { GL20.glUniform3f(loc, a, b, c); }
    public static void uniform4f(int loc, float a, float b, float c, float d) { GL20.glUniform4f(loc, a, b, c, d); }

    // vertex attribute arrays
    public static void enableVertexAttribArray(int index) { GL20.glEnableVertexAttribArray(index); }
    public static void disableVertexAttribArray(int index) { GL20.glDisableVertexAttribArray(index); }
    public static void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    // multitexture
    public static final int TEXTURE0 = GL13.GL_TEXTURE0;
    public static void activeTexture(int unit) { GL13.glActiveTexture(unit); }
}