#version 120

attribute vec3 in_pos;
attribute vec3 in_normal;
attribute vec3 in_color;
attribute vec2 in_uv;

varying vec3 v_normal;
varying vec3 v_color;
varying vec2 v_uv;

void main() {
    gl_Position = gl_ModelViewProjectionMatrix * vec4(in_pos, 1.0);
    v_normal = in_normal;
    v_color = in_color;
    v_uv = in_uv;
}
