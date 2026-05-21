package client.gfx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ChunkShader {

    public static final int ATTR_POS = 0;
    public static final int ATTR_NORMAL = 1;
    public static final int ATTR_COLOR = 2;
    public static final int ATTR_UV = 3;

    private static ChunkShader instance;

    private final int program;
    private final int uAtlas;
    private final int uSunDir;
    private final int uSunColor;
    private final int uAmbient;
    private final int uGamma;

    public static ChunkShader get() {
        if (instance == null) {
            if (!GL.hasShaderSupport()) {
                throw new RuntimeException(
                    "OpenGL 2.0 / GLSL 1.20 not available. Update your GPU driver");
            }
            instance = new ChunkShader();
        }
        return instance;
    }

    private ChunkShader() {
        String vsrc = loadResource("/client/shaders/default.vert");
        String fsrc = loadResource("/client/shaders/default.frag");

        int vs = compile(GL.VERTEX_SHADER,   vsrc, "default.vert");
        int fs = compile(GL.FRAGMENT_SHADER, fsrc, "default.frag");

        int prog = GL.createProgram();
        GL.attachShader(prog, vs);
        GL.attachShader(prog, fs);

        GL.bindAttribLocation(prog, ATTR_POS, "in_pos");
        GL.bindAttribLocation(prog, ATTR_NORMAL, "in_normal");
        GL.bindAttribLocation(prog, ATTR_COLOR, "in_color");
        GL.bindAttribLocation(prog, ATTR_UV, "in_uv");

        GL.linkProgram(prog);
        if (GL.getProgrami(prog, GL.LINK_STATUS) == 0) {
            int logLen = GL.getProgrami(prog, GL.INFO_LOG_LENGTH);
            String log = GL.getProgramInfoLog(prog, Math.max(1, logLen));
            GL.deleteProgram(prog);
            throw new RuntimeException("Chunk shader link failed: " + log);
        }

        GL.deleteShader(vs);
        GL.deleteShader(fs);

        this.program = prog;
        this.uAtlas = GL.getUniformLocation(prog, "atlas");
        this.uSunDir = GL.getUniformLocation(prog, "sunDir");
        this.uSunColor = GL.getUniformLocation(prog, "sunColor");
        this.uAmbient = GL.getUniformLocation(prog, "ambient");
        this.uGamma = GL.getUniformLocation(prog, "gamma");

        GL.useProgram(program);
        if (uAtlas >= 0) GL.uniform1i(uAtlas, 0);
        GL.useProgram(0);
    }

    public void use() { GL.useProgram(program); }
    public void unuse() { GL.useProgram(0); }

    public void setSunDir(float x, float y, float z) { if (uSunDir >= 0) GL.uniform3f(uSunDir, x, y, z); }
    public void setSunColor(float r, float g, float b) { if (uSunColor >= 0) GL.uniform3f(uSunColor, r, g, b); }
    public void setAmbient(float r, float g, float b) { if (uAmbient >= 0) GL.uniform3f(uAmbient, r, g, b); }
    public void setGamma(float g) { if (uGamma >= 0) GL.uniform1f(uGamma, g); }

    private static int compile(int type, String src, String label) {
        int sh = GL.createShader(type);
        GL.shaderSource(sh, src);
        GL.compileShader(sh);
        if (GL.getShaderi(sh, GL.COMPILE_STATUS) == 0) {
            int logLen = GL.getShaderi(sh, GL.INFO_LOG_LENGTH);
            String log = GL.getShaderInfoLog(sh, Math.max(1, logLen));
            GL.deleteShader(sh);
            throw new RuntimeException("Compile failed for " + label + ":\n" + log);
        }
        return sh;
    }

    private static String loadResource(String path) {
        try (InputStream in = ChunkShader.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("resource not found: " + path);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader resource " + path, e);
        }
    }
}