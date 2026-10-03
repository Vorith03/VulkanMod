#version 450

layout(binding = 0, std140) uniform World {
    mat4 ViewProjection;
    vec4 CameraPosition;
    vec4 FogColor;
    vec4 Settings;
};
layout(binding = 1) uniform sampler2D Sampler0;
layout(binding = 2) uniform sampler2D Sampler2;

layout(location = 0) in vec2 texCoords;
layout(location = 1) in vec4 color;
layout(location = 2) in vec2 light;
layout(location = 3) in float diffuse;
layout(location = 4) in float distanceToCamera;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 atlas = texture(Sampler0, texCoords);
    vec3 illumination = texture(Sampler2, light * 0.99609375 + 0.03125).rgb;
    vec4 shaded = vec4(atlas.rgb * illumination * diffuse, atlas.a) * color;
    float fog = clamp((Settings.y - distanceToCamera) / (Settings.y - Settings.x), 0.0, 1.0);
    fragColor = vec4(mix(FogColor.rgb, shaded.rgb, fog), shaded.a);
    if (fragColor.a < Settings.z) discard;
}
