package net.vulkanmod.mixin.render;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

public final class EmergencySaveMixinContractTest {
    private static final String RESOURCE =
            "net/vulkanmod/mixin/render/MinecraftMixin.class";

    private EmergencySaveMixinContractTest() {
    }

    public static void main(String[] args) throws Exception {
        ContractVisitor visitor = inspect();
        require(!visitor.hasSkipEmergencySaveMethod,
                "VulkanMod must not define an emergency-save suppression method");
        require(visitor.emergencySaveReferences == 0,
                "VulkanMod's Minecraft mixin must not intercept Minecraft.emergencySave()");
        System.out.println("Minecraft emergency-save preservation contract passed");
    }

    private static ContractVisitor inspect() throws IOException {
        try(InputStream input = EmergencySaveMixinContractTest.class.getClassLoader()
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
        boolean hasSkipEmergencySaveMethod;
        int emergencySaveReferences;

        ContractVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if("skipEmergencySave".equals(name))
                hasSkipEmergencySaveMethod = true;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("net/minecraft/client/Minecraft")
                            && methodName.equals("emergencySave")
                            && methodDescriptor.equals("()V"))
                        emergencySaveReferences++;
                }
            };
        }
    }
}
