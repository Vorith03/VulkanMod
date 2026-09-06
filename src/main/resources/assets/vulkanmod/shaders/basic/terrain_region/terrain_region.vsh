#version 460

//light.glsl
#define MINECRAFT_LIGHT_POWER   0.6
#define MINECRAFT_AMBIENT_LIGHT 0.4

vec4 minecraft_sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texelFetch(lightMap, (uv & 255) >> 4, 0);
}

layout(binding = 0) uniform UniformBufferObject {
   mat4 MVP;
   mat4 ModelViewMat;
};

layout(push_constant) uniform pushConstant {
    vec3 ChunkOffset;
};

layout(binding = 3) uniform sampler2D Sampler2;

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) out vec2 texCoord0;
//layout(location = 3) out vec4 normal;

// firstInstance contains three 3-bit region-local section coordinates.
layout(location = 0) in ivec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in uvec2 UV0;
layout(location = 3) in ivec2 UV2;

void main() {
    uint section = uint(gl_InstanceIndex);
    vec3 sectionOffset = vec3(section & 7u, (section >> 3u) & 7u, (section >> 6u) & 7u) * 16.0;
    vec3 pos = vec3(Position) * (1.0 / 1900.0) + sectionOffset + ChunkOffset;
    gl_Position = MVP * vec4(pos, 1.0);
    vertexDistance = length((ModelViewMat * vec4(pos, 1.0)).xyz);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = vec2(UV0) * (1.0 / 65536.0);
}
