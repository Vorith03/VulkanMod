package net.vulkanmod.vulkan.shader.parser;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

public final class GlslConverterParsingTest {
    private GlslConverterParsingTest() {
    }

    public static void run() {
        String vertex = """
                #version 150
                in vec3 Position; // trailing vertex input comment with several words
                out vec4 vertexColor; // trailing vertex output comment with several words
                void main() {
                    gl_Position = vec4(Position, 1.0);
                    vertexColor = vec4(1.0);
                }
                """;
        String fragment = """
                #version 150
                in vec4 vertexColor; // trailing fragment input comment with several words
                out vec4 fragColor; // trailing fragment output comment with several words
                void main() {
                    fragColor = vertexColor;
                }
                """;

        GlslConverter converter = new GlslConverter();
        converter.process(DefaultVertexFormat.POSITION_COLOR, vertex, fragment);

        require(converter.getVshConverted().contains("layout(location = 0) in vec3 Position;"),
                "Vertex input declaration survived trailing comment");
        require(converter.getVshConverted().contains("layout(location = 0) out vec4 vertexColor;"),
                "Vertex output declaration survived trailing comment");
        require(converter.getFshConverted().contains("layout(location = 0) in vec4 vertexColor;"),
                "Fragment input declaration survived trailing comment");
        System.out.println("GLSL declaration parsing tests passed");
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
