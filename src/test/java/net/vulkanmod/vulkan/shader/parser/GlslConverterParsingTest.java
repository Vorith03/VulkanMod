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

        verifyMultilineCommentDoesNotBecomeDeclaration();
        verifyFailureContext();
        System.out.println("GLSL declaration parsing tests passed");
    }

    private static void verifyMultilineCommentDoesNotBecomeDeclaration() {
        String vertex = """
                #version 150
                in vec3 Position;
                out vec4 vertexColor;
                void main() {
                    gl_Position = vec4(Position, 1.0);
                    vertexColor = vec4(1.0);
                }
                """;
        String fragment = """
                #version 150
                in vec4 vertexColor;
                out vec4 fragColor;
                void main() {
                    fragColor = vertexColor;
                }

                // MIT License...
                /* Copyright (c)2014 David Hoskins.

                Permission is hereby granted, free of charge, to any person obtaining a copy
                of this software and associated documentation files (the "Software"), to deal
                in the Software without restriction, including without limitation the rights
                to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
                copies of the Software.
                SOFTWARE.*/
                """;

        GlslConverter converter = new GlslConverter();
        converter.process(DefaultVertexFormat.POSITION_COLOR, vertex, fragment);

        require(converter.getFshConverted().contains(
                        "in the Software without restriction, including without limitation the rights"),
                "Multi-line comment text was not preserved in converted shader source");
        require(converter.getFshConverted().contains("layout(location = 0) in vec4 vertexColor;"),
                "Fragment declaration parsing did not recover after multi-line comment handling");
    }

    private static void verifyFailureContext() {
        String brokenVertex = """
                #version 150
                in vec3 Position
                void main() {
                    gl_Position = vec4(Position, 1.0);
                }
                """;
        String fragment = """
                #version 150
                out vec4 fragColor;
                void main() {
                    fragColor = vec4(1.0);
                }
                """;

        try {
            new GlslConverter().process(DefaultVertexFormat.POSITION, brokenVertex, fragment);
            throw new AssertionError("Malformed declaration unexpectedly parsed");
        } catch(IllegalArgumentException expected) {
            require(expected.getMessage().contains("Vertex shader declaration"),
                    "Parser failure did not identify the shader stage");
            require(expected.getMessage().contains("in vec3 Position"),
                    "Parser failure did not preserve the offending declaration");
            require(expected.getCause() != null
                            && expected.getCause().getMessage().contains("last char is not ;"),
                    "Parser failure did not retain the underlying cause");
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }
}
