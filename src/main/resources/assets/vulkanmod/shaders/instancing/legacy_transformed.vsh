#version 450

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 4) in vec3 Normal;
layout(location = 5) in mat4 Model;
layout(location = 9) in mat3 NormalMat;
layout(location = 12) in vec4 InstanceColor;
layout(location = 13) in vec4 InstanceLight;

layout(binding = 0, std140) uniform World {
    mat4 ViewProjection;
    vec4 CameraPosition;
    vec4 FogColor;
    vec4 Settings; // fog start, fog end, alpha discard, reserved
};

layout(location = 0) out vec2 texCoords;
layout(location = 1) out vec4 color;
layout(location = 2) out vec2 light;
layout(location = 3) out float diffuse;
layout(location = 4) out float distanceToCamera;

void main() {
    vec3 position = (Model * vec4(Position, 1.0)).xyz;
    vec3 normal = normalize(NormalMat * Normal);
    vec3 squared = normal * normal * vec3(0.6, 0.25, 0.8);
    diffuse = min(squared.x + squared.y * (3.0 + normal.y) + squared.z, 1.0);
    vec3 relative = position - CameraPosition.xyz;
    distanceToCamera = max(length(relative.xz), abs(relative.y));
    gl_Position = ViewProjection * vec4(position, 1.0);
    texCoords = UV0;
    color = InstanceColor;
    light = InstanceLight.xy;
}
