package net.vulkanmod.vulkan.shader.parser;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.Image;
import net.vulkanmod.vulkan.shader.descriptor.UBO;

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

public class GlslConverter {

//    private Queue<Integer> stack = new ArrayDeque<>();
    private int count;
    ShaderStage shaderStage;
    private State state;

    private UniformParser uniformParser;
    private InputOutputParser inOutParser;

    private String vshConverted;
    private String fshConverted;
    private boolean inBlockComment;

    public void process(VertexFormat vertexFormat, String vertShader, String fragShader) {
        this.uniformParser = new UniformParser(this);
        this.inOutParser = new InputOutputParser(this, vertexFormat);

        StringBuilder vshOut = new StringBuilder();
        StringBuilder fshOut = new StringBuilder();

        this.setShaderStage(ShaderStage.Vertex);
        this.inBlockComment = false;

        String[] lines = vertShader.split("\n");

        var iterator = Arrays.stream(lines).iterator();

        //TODO version
        while (iterator.hasNext()) {
            String line = iterator.next();

            String parsedLine = this.parseLine(line);
            if(parsedLine != null) {
                vshOut.append(parsedLine);
                vshOut.append("\n");
            }

        }

        vshOut.insert(0, this.inOutParser.createInOutCode());

        this.setShaderStage(ShaderStage.Fragment);
        this.inBlockComment = false;

        lines = fragShader.split("\n");

        iterator = Arrays.stream(lines).iterator();

        while (iterator.hasNext()) {
            String line = iterator.next();

            String parsedLine = this.parseLine(line);
            if(parsedLine != null) {
                fshOut.append(parsedLine);
                fshOut.append("\n");
            }
        }

        fshOut.insert(0, this.inOutParser.createInOutCode());

        String uniformBlock = this.uniformParser.createUniformsCode();
        vshOut.insert(0, uniformBlock);
        fshOut.insert(0, uniformBlock);

        String samplersVertCode = this.uniformParser.createSamplersCode(ShaderStage.Vertex);
        String samplersFragCode = this.uniformParser.createSamplersCode(ShaderStage.Fragment);

        vshOut.insert(0, samplersVertCode);
        fshOut.insert(0, samplersFragCode);

        vshOut.insert(0, "#version 450\n\n");
        fshOut.insert(0, "#version 450\n\n");

        //TODO check
        //TODO ubo
        this.vshConverted = vshOut.toString();
        this.fshConverted = fshOut.toString();

    }

    private String parseLine(String line) {

        String parseableLine = this.stripCommentsForParsing(line);
        StringTokenizer tokenizer = new StringTokenizer(parseableLine);

        // Keep comment-only lines in the converted source, but never interpret
        // text inside a multi-line GLSL comment as a declaration.
        if(!tokenizer.hasMoreTokens()) {
            return line.isBlank() ? null : line;
        }

        String token = tokenizer.nextToken();

        if(token.matches("uniform")) {
            this.state = State.MATCHING_UNIFORM;
        }
        else if(token.matches("in")) {
            this.state = State.MATCHING_IN_OUT;
        }
        else if(token.matches("out")) {
            this.state = State.MATCHING_IN_OUT;
        }
        else if(token.matches("#version")) {
            return null;
        }
        else {
            return line;
        }

        try {
            if(tokenizer.countTokens() < 2) {
                throw new IllegalArgumentException("Less than 3 tokens present");
            }

            if(feedToken(token)) {
                return null;
            }

            while (tokenizer.hasMoreTokens()) {
                token = tokenizer.nextToken();

                // One declaration is parsed per source line. Once the declaration is
                // complete, ignore trailing whitespace/comment tokens instead of
                // treating them as a second declaration.
                if(feedToken(token)) {
                    break;
                }
            }

            return null;
        } catch(RuntimeException failure) {
            throw new IllegalArgumentException(
                    "Failed to parse " + this.shaderStage + " shader declaration: " + line.trim(),
                    failure
            );
        }
    }

    private String stripCommentsForParsing(String line) {
        StringBuilder code = new StringBuilder(line.length());
        int cursor = 0;

        while(cursor < line.length()) {
            if(this.inBlockComment) {
                int commentEnd = line.indexOf("*/", cursor);
                if(commentEnd < 0) {
                    return code.toString();
                }

                this.inBlockComment = false;
                cursor = commentEnd + 2;
                continue;
            }

            int lineComment = line.indexOf("//", cursor);
            int blockComment = line.indexOf("/*", cursor);

            if(lineComment >= 0 && (blockComment < 0 || lineComment < blockComment)) {
                code.append(line, cursor, lineComment);
                break;
            }

            if(blockComment >= 0) {
                code.append(line, cursor, blockComment);
                this.inBlockComment = true;
                cursor = blockComment + 2;
                continue;
            }

            code.append(line, cursor, line.length());
            break;
        }

        return code.toString();
    }

    private boolean feedToken(String token) {
        return switch (this.state) {
            case MATCHING_UNIFORM -> this.uniformParser.parseToken(token);
            case MATCHING_IN_OUT -> this.inOutParser.parseToken(token);
        };
    }

    private void setShaderStage(ShaderStage shaderStage) {
        this.shaderStage = shaderStage;
        this.uniformParser.setCurrentUniforms(this.shaderStage);
        this.inOutParser.setShaderStage(this.shaderStage);
    }

    public UBO getUBO() {
        return this.uniformParser.getUbo();
    }

    public List<Image> getSamplerList() {
        return this.uniformParser.getSamplers();
    }

    public String getVshConverted() {
        return vshConverted;
    }

    public String getFshConverted() {
        return fshConverted;
    }

    enum ShaderStage {
        Vertex,
        Fragment
    }

    enum State {
        MATCHING_UNIFORM,
        MATCHING_IN_OUT
    }
}
