package net.vulkanmod.render.chunk.build;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class ChunkTaskOriginSnapshotTest {
    private static final String BUILD_TASK_RESOURCE =
            "net/vulkanmod/render/chunk/build/ChunkTask$BuildTask.class";
    private static final String RENDER_SECTION_RESOURCE =
            "net/vulkanmod/render/chunk/RenderSection.class";
    private static final String EXPECTED_CONSTRUCTOR =
            "(Lnet/vulkanmod/render/chunk/RenderSection;" +
            "Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;" +
            "Lnet/minecraft/core/BlockPos;" +
            "ZLnet/vulkanmod/render/chunk/build/TaskDispatcher;)V";

    private ChunkTaskOriginSnapshotTest() {
    }

    public static void main(String[] args) throws Exception {
        BuildTaskVisitor buildTask = inspectBuildTask();
        require(buildTask.finalOriginField,
                "BuildTask must own a final BlockPos origin snapshot");
        require(buildTask.snapshotConstructor,
                "BuildTask constructor must receive the same immutable origin used to capture its region");
        require(buildTask.compileReadsOriginField,
                "BuildTask.compile() must read its captured origin");
        require(buildTask.mutableOriginReads == 0,
                "BuildTask.compile() still reads mutable RenderSection origin coordinates");

        RenderSectionVisitor renderSection = inspectRenderSection();
        require(renderSection.snapshotConstructorCall,
                "RenderSection.createCompileTask() must pass the captured BlockPos into BuildTask");

        System.out.println("Chunk task immutable-origin contract passed");
    }

    private static BuildTaskVisitor inspectBuildTask() throws IOException {
        try (InputStream input = ChunkTaskOriginSnapshotTest.class.getClassLoader()
                .getResourceAsStream(BUILD_TASK_RESOURCE)) {
            if(input == null) {
                throw new AssertionError("Could not load " + BUILD_TASK_RESOURCE);
            }
            BuildTaskVisitor visitor = new BuildTaskVisitor();
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static RenderSectionVisitor inspectRenderSection() throws IOException {
        try (InputStream input = ChunkTaskOriginSnapshotTest.class.getClassLoader()
                .getResourceAsStream(RENDER_SECTION_RESOURCE)) {
            if(input == null) {
                throw new AssertionError("Could not load " + RENDER_SECTION_RESOURCE);
            }
            RenderSectionVisitor visitor = new RenderSectionVisitor();
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class BuildTaskVisitor extends ClassVisitor {
        boolean finalOriginField;
        boolean snapshotConstructor;
        boolean compileReadsOriginField;
        int mutableOriginReads;

        BuildTaskVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                       String signature, Object value) {
            if("origin".equals(name)
                    && "Lnet/minecraft/core/BlockPos;".equals(descriptor)
                    && (access & Opcodes.ACC_FINAL) != 0) {
                finalOriginField = true;
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("<init>".equals(name) && EXPECTED_CONSTRUCTOR.equals(descriptor)) {
                snapshotConstructor = true;
            }
            if(!"compile".equals(name)) {
                return null;
            }

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
                    if(opcode == Opcodes.GETFIELD
                            && owner.equals("net/vulkanmod/render/chunk/build/ChunkTask$BuildTask")
                            && fieldName.equals("origin")
                            && fieldDescriptor.equals("Lnet/minecraft/core/BlockPos;")) {
                        compileReadsOriginField = true;
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/vulkanmod/render/chunk/RenderSection")
                            && (methodName.equals("xOffset")
                            || methodName.equals("yOffset")
                            || methodName.equals("zOffset"))) {
                        mutableOriginReads++;
                    }
                }
            };
        }
    }

    private static final class RenderSectionVisitor extends ClassVisitor {
        boolean snapshotConstructorCall;

        RenderSectionVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"createCompileTask".equals(name)) {
                return null;
            }
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(opcode == Opcodes.INVOKESPECIAL
                            && owner.equals("net/vulkanmod/render/chunk/build/ChunkTask$BuildTask")
                            && methodName.equals("<init>")
                            && EXPECTED_CONSTRUCTOR.equals(methodDescriptor)) {
                        snapshotConstructorCall = true;
                    }
                }
            };
        }
    }
}
