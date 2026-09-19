package net.vulkanmod.vulkan.memory;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class VmaMappingContractTest {
    private static final String RESOURCE =
            "net/vulkanmod/vulkan/memory/MemoryManager.class";

    private VmaMappingContractTest() {
    }

    public static void main(String[] args) throws Exception {
        ContractVisitor visitor = inspect();

        require(visitor.mapAndCopyMapResultStored,
                "MapAndCopy must inspect the vmaMapMemory result");
        require(visitor.mapAndCopyHasFinally,
                "MapAndCopy must unmap through a finally path");
        require(visitor.mapAndCopyUnmapCalls >= 1,
                "MapAndCopy must unmap successful mappings");

        require(visitor.mapMapResultStored,
                "Map must inspect the vmaMapMemory result");
        require(visitor.mapFreesPointerOnFailure,
                "Map must free its native PointerBuffer when mapping fails");
        require(visitor.mapThrowsOnFailure,
                "Map must fail fast when vmaMapMemory fails");

        System.out.println("VMA mapping failure/unmap contract passed");
    }

    private static ContractVisitor inspect() throws IOException {
        try(InputStream input = VmaMappingContractTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if(input == null)
                throw new AssertionError("Could not load " + RESOURCE);

            ContractVisitor visitor = new ContractVisitor();
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return visitor;
        }
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class ContractVisitor extends ClassVisitor {
        boolean mapAndCopyMapResultStored;
        boolean mapAndCopyHasFinally;
        int mapAndCopyUnmapCalls;

        boolean mapMapResultStored;
        boolean mapFreesPointerOnFailure;
        boolean mapThrowsOnFailure;

        ContractVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("MapAndCopy".equals(name))
                return new MappingVisitor(true);
            if("Map".equals(name))
                return new MappingVisitor(false);
            return null;
        }

        private final class MappingVisitor extends MethodVisitor {
            private final boolean mapAndCopy;
            private boolean awaitingMapResult;

            MappingVisitor(boolean mapAndCopy) {
                super(Opcodes.ASM9);
                this.mapAndCopy = mapAndCopy;
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                if(mapAndCopy && type == null)
                    mapAndCopyHasFinally = true;
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String methodName,
                                        String methodDescriptor, boolean isInterface) {
                if(owner.equals("org/lwjgl/util/vma/Vma")
                        && methodName.equals("vmaMapMemory")) {
                    awaitingMapResult = true;
                }

                if(owner.equals("org/lwjgl/util/vma/Vma")
                        && methodName.equals("vmaUnmapMemory")
                        && mapAndCopy) {
                    mapAndCopyUnmapCalls++;
                }

                if(owner.equals("org/lwjgl/system/MemoryUtil")
                        && methodName.equals("memFree")
                        && !mapAndCopy) {
                    mapFreesPointerOnFailure = true;
                }

                if(owner.equals("java/lang/RuntimeException")
                        && methodName.equals("<init>")
                        && !mapAndCopy) {
                    mapThrowsOnFailure = true;
                }
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                if(awaitingMapResult && opcode == Opcodes.ISTORE) {
                    if(mapAndCopy)
                        mapAndCopyMapResultStored = true;
                    else
                        mapMapResultStored = true;
                    awaitingMapResult = false;
                }
            }
        }
    }
}
