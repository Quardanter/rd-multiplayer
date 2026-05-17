package client.world;

public final class WorldTime {
    private static final long DEFAULT_CYCLE_MS = 10L * 60L * 1000L;
    private static volatile long cycleMs = DEFAULT_CYCLE_MS;
    private static volatile long lastSyncSystemMs = System.currentTimeMillis();
    private static volatile float lastSyncFraction = 0.30f;

    public static void syncFromServer(float fraction, long cycleLengthMs) {
        if (cycleLengthMs > 0) cycleMs = cycleLengthMs;
        lastSyncFraction = wrap(fraction);
        lastSyncSystemMs = System.currentTimeMillis();
    }

    public static float fraction() {
        long elapsed = System.currentTimeMillis() - lastSyncSystemMs;
        float advance = (float) elapsed / cycleMs;
        return wrap(lastSyncFraction + advance);
    }

    private static float wrap(float f) {
        f = f % 1.0f;
        if (f < 0) f += 1.0f;
        return f;
    }

    // sun
    public static float[] sunDirection() {
        float f = fraction();
        double angle = 2.0 * Math.PI * (f - 0.25);
        float x = (float) Math.cos(angle);
        float y = (float) Math.sin(angle);
        return new float[] { x, y, 0f };
    }

    public static float sunStrength() {
        float f = fraction();
        if (f < 0.18f || f > 0.82f) return 0f;
        if (f < 0.25f) return (f - 0.18f) / 0.07f;
        if (f > 0.75f) return (0.82f - f) / 0.07f;
        return 1f;
    }

    // light colors
    public static float[] ambientLight() {
        return interpolatePalette(fraction(), AMBIENT_PALETTE);
    }

    public static float[] diffuseLight() {
        float[] base = interpolatePalette(fraction(), DIFFUSE_PALETTE);
        float s = sunStrength();
        return new float[] { base[0] * s, base[1] * s, base[2] * s };
    }

    // sky / fog colors
    public static float[] skyColor() { return interpolatePalette(fraction(), SKY_PALETTE); }
    public static float[] fogColor() { return interpolatePalette(fraction(), FOG_PALETTE); }

    private static final float[][] SKY_PALETTE = {
        // fraction, r,    g,    b
        { 0.00f,  0.01f, 0.01f, 0.05f },   // midnight
        { 0.18f,  0.01f, 0.01f, 0.05f },   // night
        { 0.25f,  0.95f, 0.55f, 0.30f },   // sunrise
        { 0.35f,  0.55f, 0.78f, 0.98f },   // morning
        { 0.50f,  0.50f, 0.80f, 1.00f },   // noon
        { 0.65f,  0.55f, 0.75f, 0.98f },   // afternoon
        { 0.75f,  0.90f, 0.45f, 0.25f },   // sunset
        { 0.82f,  0.01f, 0.01f, 0.05f },   // dusk ends
        { 1.00f,  0.01f, 0.01f, 0.05f },   // wrap
    };

    private static final float[][] FOG_PALETTE = {
        { 0.00f,  0.00f, 0.00f, 0.02f },
        { 0.18f,  0.00f, 0.00f, 0.02f },
        { 0.25f,  0.55f, 0.35f, 0.20f },
        { 0.35f,  0.70f, 0.85f, 1.00f },
        { 0.50f,  0.70f, 0.85f, 1.00f },
        { 0.65f,  0.70f, 0.85f, 1.00f },
        { 0.75f,  0.50f, 0.30f, 0.18f },
        { 0.82f,  0.00f, 0.00f, 0.02f },
        { 1.00f,  0.00f, 0.00f, 0.02f },
    };

    private static final float[][] AMBIENT_PALETTE = {
        { 0.00f,  0.04f, 0.04f, 0.09f },
        { 0.18f,  0.04f, 0.04f, 0.09f },
        { 0.25f,  0.45f, 0.40f, 0.40f },
        { 0.35f,  0.62f, 0.62f, 0.68f },
        { 0.50f,  0.65f, 0.65f, 0.70f },
        { 0.65f,  0.60f, 0.58f, 0.60f },
        { 0.75f,  0.45f, 0.38f, 0.35f },
        { 0.82f,  0.04f, 0.04f, 0.09f },
        { 1.00f,  0.04f, 0.04f, 0.09f },
    };

    private static final float[][] DIFFUSE_PALETTE = {
        { 0.00f,  0.00f, 0.00f, 0.00f },
        { 0.18f,  0.00f, 0.00f, 0.00f },
        { 0.25f,  1.00f, 0.65f, 0.35f },
        { 0.35f,  1.00f, 0.95f, 0.85f },
        { 0.50f,  1.00f, 0.97f, 0.90f },
        { 0.65f,  1.00f, 0.90f, 0.75f },
        { 0.75f,  1.00f, 0.55f, 0.25f },
        { 0.82f,  0.00f, 0.00f, 0.00f },
        { 1.00f,  0.00f, 0.00f, 0.00f },
    };

    private static float[] interpolatePalette(float f, float[][] palette) {
        for (int i = 0; i < palette.length - 1; i++) {
            float f0 = palette[i][0];
            float f1 = palette[i + 1][0];
            if (f >= f0 && f <= f1) {
                float t = (f1 == f0) ? 0f : (f - f0) / (f1 - f0);
                float[] a = palette[i];
                float[] b = palette[i + 1];
                return new float[] {
                    a[1] + (b[1] - a[1]) * t,
                    a[2] + (b[2] - a[2]) * t,
                    a[3] + (b[3] - a[3]) * t,
                };
            }
        }

        float[] last = palette[palette.length - 1];
        return new float[] { last[1], last[2], last[3] };
    }

    private WorldTime() {}
}