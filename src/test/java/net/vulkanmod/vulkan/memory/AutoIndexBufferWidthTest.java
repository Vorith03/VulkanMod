package net.vulkanmod.vulkan.memory;

import org.lwjgl.system.MemoryUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public final class AutoIndexBufferWidthTest {
    private AutoIndexBufferWidthTest() {
    }

    public static void main(String[] args) throws Exception {
        require(AutoIndexBuffer.indexTypeForVertexCount(65536) == IndexBuffer.IndexType.SHORT,
                "65536 vertices must remain representable by UINT16 indices");
        require(AutoIndexBuffer.indexTypeForVertexCount(65537) == IndexBuffer.IndexType.INT,
                "Vertex counts above 65536 must use UINT32 indices");

        verifyQuadBoundary();
        verifyTriangleFanBoundary();
        verifyTriangleStripBoundary();
        verifyBindTypePropagation();

        System.out.println("Automatic index width/bind contract passed");
    }

    private static void verifyQuadBoundary() {
        ByteBuffer shortBytes = AutoIndexBuffer.genQuadIdxs(65536, IndexBuffer.IndexType.SHORT);
        try {
            ShortBuffer shortIndices = shortBytes.asShortBuffer();
            int last = shortIndices.capacity() - 1;
            require(Short.toUnsignedInt(shortIndices.get(last)) == 65535,
                    "UINT16 quad boundary must preserve vertex 65535");
        } finally {
            MemoryUtil.memFree(shortBytes);
        }

        ByteBuffer intBytes = AutoIndexBuffer.genQuadIdxs(65540, IndexBuffer.IndexType.INT);
        try {
            IntBuffer indices = intBytes.asIntBuffer();
            int base = indices.capacity() - 6;
            require(indices.get(base) == 65536, "UINT32 quad i0");
            require(indices.get(base + 1) == 65537, "UINT32 quad i1");
            require(indices.get(base + 2) == 65538, "UINT32 quad i2");
            require(indices.get(base + 3) == 65536, "UINT32 quad i3");
            require(indices.get(base + 4) == 65538, "UINT32 quad i4");
            require(indices.get(base + 5) == 65539, "UINT32 quad i5");
        } finally {
            MemoryUtil.memFree(intBytes);
        }
    }

    private static void verifyTriangleFanBoundary() {
        ByteBuffer bytes = AutoIndexBuffer.genTriangleFanIdxs(65538, IndexBuffer.IndexType.INT);
        try {
            IntBuffer indices = bytes.asIntBuffer();
            int base = indices.capacity() - 3;
            require(indices.get(base) == 0, "UINT32 fan center");
            require(indices.get(base + 1) == 65536, "UINT32 fan edge 1");
            require(indices.get(base + 2) == 65537, "UINT32 fan edge 2");
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    private static void verifyTriangleStripBoundary() {
        ByteBuffer bytes = AutoIndexBuffer.genTriangleStripIdxs(65538, IndexBuffer.IndexType.INT);
        try {
            IntBuffer indices = bytes.asIntBuffer();
            int base = indices.capacity() - 3;
            require(indices.get(base) == 65535, "UINT32 strip i0");
            require(indices.get(base + 1) == 65536, "UINT32 strip i1");
            require(indices.get(base + 2) == 65537, "UINT32 strip i2");
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    private static void verifyBindTypePropagation() throws IOException {
        DrawerVisitor drawer = inspect(
                "net/vulkanmod/vulkan/Drawer.class", new DrawerVisitor());
        require(drawer.drawUsesAutoIndexType,
                "Drawer.draw() must propagate the automatic index buffer Vulkan type");
        require(drawer.bindUsesAutoIndexType,
                "Drawer.bindAutoIndexBuffer() must bind the automatic index buffer Vulkan type");
        require(drawer.typedDrawBindsParameter,
                "Typed drawIndexed overload must pass its index-type parameter to Vulkan");

        VboVisitor vbo = inspect(
                "net/vulkanmod/render/VBO.class", new VboVisitor());
        require(vbo.recordsAutoIndexType,
                "VBO sequential-index upload must record the auto-index Vulkan type");
        require(vbo.typedDrawCalls >= 2,
                "VBO indexed draws must use the typed Drawer overload");
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try(InputStream input = AutoIndexBufferWidthTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if(input == null)
                throw new AssertionError("Could not load " + resource);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class DrawerVisitor extends ClassVisitor {
        boolean drawUsesAutoIndexType;
        boolean bindUsesAutoIndexType;
        boolean typedDrawBindsParameter;

        DrawerVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("draw".equals(name) && descriptor.contains("Ljava/nio/ByteBuffer;")) {
                return typeGetterVisitor(() -> drawUsesAutoIndexType = true);
            }

            if("bindAutoIndexBuffer".equals(name)) {
                return typeGetterVisitor(() -> bindUsesAutoIndexType = true);
            }

            if("drawIndexed".equals(name)
                    && descriptor.equals("(Lnet/vulkanmod/vulkan/memory/VertexBuffer;" +
                    "Lnet/vulkanmod/vulkan/memory/IndexBuffer;II)V")) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("org/lwjgl/vulkan/VK10")
                                && methodName.equals("vkCmdBindIndexBuffer"))
                            typedDrawBindsParameter = true;
                    }
                };
            }
            return null;
        }

        private MethodVisitor typeGetterVisitor(Runnable found) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/vulkan/memory/AutoIndexBuffer")
                            && methodName.equals("getVkIndexType"))
                        found.run();
                }
            };
        }
    }

    private static final class VboVisitor extends ClassVisitor {
        boolean recordsAutoIndexType;
        int typedDrawCalls;

        VboVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("configureIndexBuffer".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/vulkan/memory/AutoIndexBuffer")
                                && methodName.equals("getVkIndexType"))
                            recordsAutoIndexType = true;
                    }
                };
            }

            if("drawWithShader".equals(name) || "drawChunkLayer".equals(name)) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if(owner.equals("net/vulkanmod/vulkan/Drawer")
                                && methodName.equals("drawIndexed")
                                && methodDescriptor.endsWith("II)V"))
                            typedDrawCalls++;
                    }
                };
            }
            return null;
        }
    }
}
