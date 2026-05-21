#version 120

uniform sampler2D atlas;
uniform vec3 sunDir;
uniform vec3 sunColor;
uniform vec3 ambient;
uniform float gamma;

varying vec3 v_normal;
varying vec3 v_color;
varying vec2 v_uv;

void main() {
    vec4 texel = texture2D(atlas, v_uv);

    vec3 N = normalize(v_normal);
    float lambert = max(dot(N, normalize(sunDir)), 0.0);

    vec3 lit = (ambient + sunColor * lambert) * v_color;

    vec3 rgb = texel.rgb * lit * gamma;
    gl_FragColor = vec4(rgb, texel.a);
}
