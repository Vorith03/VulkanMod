#version 150

#moj_import <vulkanmod:ci_shader_include.glsl>

uniform vec4 ColorModulator;

in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec4 color = vulkanmod_ci_passthrough(vertexColor);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
