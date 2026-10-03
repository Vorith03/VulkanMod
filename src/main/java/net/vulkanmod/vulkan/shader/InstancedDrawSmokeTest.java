package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.instancing.ModelGeometry;
import net.vulkanmod.render.instancing.LegacyFlywheelInstances;
import net.vulkanmod.render.instancing.SharedModelBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.IndexBuffer;
import net.vulkanmod.vulkan.memory.VertexBuffer;
import net.vulkanmod.vulkan.texture.ScreenshotReadback;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.vulkan.VK10.*;

/** Pixel oracle for two-binding fetches, not a claim of Flywheel material integration. */
public final class InstancedDrawSmokeTest {
    private static final int STRIDE = 108; // light, color, model mat4, normal mat3
    private InstancedDrawSmokeTest() {}

    public static void verify(Minecraft minecraft) throws Exception {
        Renderer renderer = Renderer.getInstance();
        boolean oldCull = VRenderSystem.cull, oldDepth = VRenderSystem.depthTest;
        boolean oldMask = VRenderSystem.depthMask, oldStencil = VRenderSystem.stencilTest;
        boolean oldBlend = PipelineState.blendInfo.enabled;
        int oldColorMask = VRenderSystem.colorMask;
        VertexBuffer model = null, instances = null;
        IndexBuffer indices = null;
        SharedModelBuffer shared32 = null;
        GraphicsPipeline instanced = null, instanced32 = null, ordinary = null;
        TextureTarget target = null;
        ByteBuffer vertices = MemoryUtil.memAlloc(48), data = MemoryUtil.memAlloc(STRIDE * 3);
        ByteBuffer indexData = MemoryUtil.memAlloc(12), normalizedData = MemoryUtil.memAlloc(STRIDE * 3);
        ByteBuffer prefixedData = MemoryUtil.memAlloc(STRIDE * 3 + 8);
        try {
            VRenderSystem.cull = false; VRenderSystem.depthTest = false;
            VRenderSystem.depthMask = false; VRenderSystem.stencilTest = false;
            PipelineState.blendInfo.enabled = false; VRenderSystem.colorMask = 15;
            var attributes = new ArrayList<InstanceVertexFormat.Attribute>();
            attributes.add(new InstanceVertexFormat.Attribute(8, InstanceVertexFormat.Format.UBYTE4_NORMALIZED, 4));
            attributes.add(new InstanceVertexFormat.Attribute(9, InstanceVertexFormat.Format.USHORT2, 0));
            for(int i = 0; i < 4; i++) attributes.add(new InstanceVertexFormat.Attribute(1+i, InstanceVertexFormat.Format.FLOAT4, 8+i*16));
            for(int i = 0; i < 3; i++) attributes.add(new InstanceVertexFormat.Attribute(5+i, InstanceVertexFormat.Format.FLOAT3, 72+i*12));
            var layout = new InstanceVertexFormat(STRIDE, attributes);
            Pipeline.Builder builder = new Pipeline.Builder(DefaultVertexFormat.POSITION).setInstanceFormat(layout);
            builder.setUniforms(new ArrayList<>(), List.of());
            String vertexShader = """
                    #version 450
                    layout(location=0) in vec3 Position;
                    layout(location=1) in mat4 Model;
                    layout(location=5) in mat3 Normal;
                    layout(location=8) in vec4 Color;
                    layout(location=9) in uvec2 Light;
                    layout(location=0) out vec4 color;
                    void main() {
                        gl_Position = Model * vec4(Position, 1.0);
                        float normalFactor = dot(Normal * vec3(0.25, 0.25, 0.5), vec3(1.0));
                        color = Color * vec4(vec3(normalFactor * float(Light.x) / 240.0), 1.0);
                    }
                    """;
            builder.compileShaders(vertexShader, fragment());
            instanced = builder.createGraphicsPipeline();
            builder = new Pipeline.Builder(DefaultVertexFormat.BLOCK).setInstanceFormat(LegacyFlywheelInstances.format(5));
            builder.setUniforms(new ArrayList<>(), List.of());
            builder.compileShaders(vertexShader.replace("location=1) in mat4", "location=5) in mat4")
                    .replace("location=5) in mat3", "location=9) in mat3")
                    .replace("location=8) in vec4", "location=12) in vec4")
                    .replace("location=9) in uvec2", "location=13) in vec4")
                    .replace("float(Light.x) / 240.0", "min(Light.x, Light.y) * 255.0 / 240.0"), fragment());
            instanced32 = builder.createGraphicsPipeline();
            builder = new Pipeline.Builder(DefaultVertexFormat.POSITION);
            builder.setUniforms(new ArrayList<>(), List.of());
            builder.compileShaders("""
                    #version 450
                    layout(location=0) in vec3 Position;
                    layout(location=0) out vec4 color;
                    void main() { gl_Position = vec4(Position.xy * 0.08, 0.5, 1.0); color = vec4(1.0); }
                    """, fragment());
            ordinary = builder.createGraphicsPipeline();
            if(ordinary.getInstanceFormat() != null) throw new AssertionError("Instance format leaked to ordinary pipeline");

            for(float[] p : new float[][]{{-1,-1,0},{1,-1,0},{1,1,0},{-1,1,0}})
                for(float f : p) vertices.putFloat(f);
            vertices.flip();
            for(short i : new short[]{0,1,2,2,3,0}) indexData.putShort(i);
            indexData.flip();
            // Prefixes ensure every buffer binding uses its uploaded slice offset.
            model = new VertexBuffer(12);
            model.copyToVertexBuffer(12, 1, vertices.duplicate().limit(12));
            model.copyToVertexBuffer(12, 4, vertices);
            indices = new IndexBuffer(14);
            indices.copyBuffer(indexData.duplicate().limit(2));
            indices.copyBuffer(indexData);
            byte[] largeVertices = new byte[65540 * ModelGeometry.STRIDE];
            ByteBuffer packed = ByteBuffer.wrap(largeVertices).order(ByteOrder.nativeOrder());
            int at = 65536 * ModelGeometry.STRIDE;
            for(float[] p : new float[][]{{-1,-1,0},{1,-1,0},{1,1,0},{-1,1,0}}) {
                for(int component=0; component<3; component++) packed.putFloat(at+component*4,p[component]);
                at += ModelGeometry.STRIDE;
            }
            shared32 = new SharedModelBuffer(new ModelGeometry(largeVertices,
                    new int[]{65536,65537,65538,65538,65539,65536}, new BitSet()));
            if(shared32.indexType() != VK_INDEX_TYPE_UINT32) throw new AssertionError("High index was truncated");
            instances = new VertexBuffer(STRIDE);
            putInstance(data, 0, 0, 0, 0, 255, 240, 1); // guard at firstInstance=0: never drawn
            putInstance(data, STRIDE, -0.5f, 255, 0, 0, 240, 0.5f);
            putInstance(data, STRIDE*2, 0.5f, 0, 255, 0, 120, 1);
            instances.copyToVertexBuffer(STRIDE, 1, data.duplicate().limit(STRIDE));
            instances.copyToVertexBuffer(STRIDE, 3, data);
            normalizedData.put(data.duplicate()).flip();
            for(int i=0; i<3; i++) {
                int offset = i * STRIDE;
                int block = Short.toUnsignedInt(data.getShort(offset)), sky = Short.toUnsignedInt(data.getShort(offset+2));
                // Swap the second visible record's channels: shader must use both normalized channels.
                normalizedData.put(offset,(byte)(i == 2 ? sky : block));
                normalizedData.put(offset+1,(byte)(i == 2 ? block : sky));
                normalizedData.put(offset+2,(byte)0); normalizedData.put(offset+3,(byte)0);
            }
            target = new TextureTarget(64, 32, true, Minecraft.ON_OSX);
            for(int pass = 0; pass < 6; pass++) {
                int phase = pass % 3;
                if(phase == 1) target.resize(80, 48, Minecraft.ON_OSX);
                renderer.resetBuffers(); renderer.beginFrame();
                target.setClearColor(0,0,0,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                Renderer.resetScissor(); Renderer.setDepthBias(0,0);
                int first = phase == 1 ? 2 : 1;
                int count = phase == 0 ? 2 : phase == 1 ? 1 : 0;
                if(pass < 3) {
                    Renderer.getDrawer().drawIndexedInstanced(instanced, model, instances, indices,
                            VK_INDEX_TYPE_UINT16, 4, 6, first, count);
                } else {
                    // Production shared mesh and append-only upload, with indices above the 16-bit range.
                    prefixedData.clear().position(4); prefixedData.put(normalizedData.duplicate());
                    prefixedData.limit(4 + STRIDE * 3).position(4);
                    Renderer.getDrawer().drawIndexedInstanced(instanced32, shared32.vertices(), prefixedData, shared32.indices(),
                            shared32.indexType(), shared32.geometry().vertexCount(), 6, first, count);
                    if(prefixedData.position()!=4 || prefixedData.limit()!=4+STRIDE*3)
                        throw new AssertionError("Instance upload changed caller cursor");
                    // Reusing/changing caller memory cannot overwrite already recorded instance draws.
                    if(phase == 0) {
                        putInstance(normalizedData, STRIDE, 0, 0, 0, 255, 240, 1);
                        normalizedData.put(STRIDE,(byte)240); normalizedData.put(STRIDE+1,(byte)240);
                        normalizedData.put(STRIDE+2,(byte)0); normalizedData.put(STRIDE+3,(byte)0);
                        Renderer.getDrawer().drawIndexedInstanced(instanced32, shared32.vertices(), normalizedData, shared32.indices(),
                                shared32.indexType(), shared32.geometry().vertexCount(), 6, 1, 1);
                        putInstance(normalizedData, STRIDE, -0.5f, 255, 0, 0, 240, 0.5f);
                        normalizedData.put(STRIDE,(byte)240); normalizedData.put(STRIDE+1,(byte)240);
                        normalizedData.put(STRIDE+2,(byte)0); normalizedData.put(STRIDE+3,(byte)0);
                    }
                }
                // An ordinary draw immediately after instancing must retain binding-0 semantics.
                renderer.bindGraphicsPipeline(ordinary); renderer.uploadAndBindUBOs(ordinary);
                Renderer.getDrawer().drawIndexed(model, indices, 6);
                var capture = ScreenshotReadback.request(target);
                target.unbindWrite(); minecraft.getMainRenderTarget().bindWrite(true);
                renderer.endFrame();
                try(NativeImage image = capture.get(10, TimeUnit.SECONDS)) {
                    expect(image, image.getWidth()/4, image.getHeight()/2, phase == 0 ? 0xFF000080 : 0xFF000000);
                    expect(image, 3*image.getWidth()/4, image.getHeight()/2, phase < 2 ? 0xFF008000 : 0xFF000000);
                    expect(image, image.getWidth()/2, image.getHeight()/2, 0xFFFFFFFF);
                    expect(image, 1, 1, 0xFF000000);
                    expect(image, image.getWidth()/2, image.getHeight()/2 + image.getHeight()/10,
                            pass == 3 ? 0xFFFF0000 : 0xFF000000);
                }
            }
            Initializer.LOGGER.info("Vulkan instanced draw smoke passed: model/normal matrices, packed color/light, slice offsets, firstInstance, zero count, target resize, ordinary draw isolation, 32-bit indices above 65535, normalized legacy light channels, shared mesh retirement and fence-owned append uploads");
        } finally {
            // Oracle only. Future adapter owners must retire through their frame fences.
            Vulkan.waitIdle();
            if(instanced != null) instanced.cleanUp();
            if(instanced32 != null) instanced32.cleanUp();
            if(ordinary != null) ordinary.cleanUp();
            if(model != null) model.freeBuffer();
            if(instances != null) instances.freeBuffer();
            if(indices != null) indices.freeBuffer();
            if(shared32 != null) { shared32.close(); shared32.close(); }
            if(target != null) target.destroyBuffers();
            MemoryUtil.memFree(vertices); MemoryUtil.memFree(data); MemoryUtil.memFree(indexData); MemoryUtil.memFree(normalizedData); MemoryUtil.memFree(prefixedData);
            VRenderSystem.cull = oldCull; VRenderSystem.depthTest = oldDepth;
            VRenderSystem.depthMask = oldMask; VRenderSystem.stencilTest = oldStencil;
            PipelineState.blendInfo.enabled = oldBlend; VRenderSystem.colorMask = oldColorMask;
        }
    }

    private static String fragment() {
        return "#version 450\nlayout(location=0) in vec4 color; layout(location=0) out vec4 outColor; void main() { outColor=color; }";
    }

    private static void putInstance(ByteBuffer data, int offset, float x, int r, int g, int b, int light, float normal) {
        data.putShort(offset, (short)light); data.putShort(offset+2, (short)240);
        data.put(offset+4,(byte)r); data.put(offset+5,(byte)g); data.put(offset+6,(byte)b); data.put(offset+7,(byte)255);
        for(int col = 0; col < 4; col++) for(int row = 0; row < 4; row++) {
            float value = col == row ? (col < 2 ? 0.3f : 1) : 0;
            if(col == 3 && row == 0) value = x;
            if(col == 3 && row == 2) value = 0.5f;
            data.putFloat(offset+8+col*16+row*4,value);
        }
        for(int col = 0; col < 3; col++) for(int row = 0; row < 3; row++)
            data.putFloat(offset+72+col*12+row*4,col == row ? normal : 0);
    }

    private static void expect(NativeImage image, int x, int y, int expected) {
        int actual = image.getPixelRGBA(x,y);
        for(int shift : new int[]{0,8,16,24}) {
            if(Math.abs((actual >>> shift & 255) - (expected >>> shift & 255)) > 1)
                throw new AssertionError(String.format("Instance pixel (%d,%d) expected %08x got %08x",x,y,expected,actual));
        }
    }
}
