package net.vulkanmod.mixin.compatibility;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Guards the Vulkan boundary for Immersive Portals' OpenGL occlusion queries.
 * Once the unsupported query is replaced by a conservative visible result, its
 * callback is query-only geometry and must not execute GL-only shader paths.
 */
public final class ImmersivePortalsQueryBypassContractTest {
    private static final String MIXIN_RESOURCE =
            "net/vulkanmod/mixin/compatibility/ImmersivePortalsQueryManagerMixin.class";

    private ImmersivePortalsQueryBypassContractTest() {
    }

    public static void main(String[] args) throws Exception {
        QueryBypassVisitor visitor = inspectMixin();
        require(visitor.booleanBypass.found,
                "Missing Immersive Portals boolean query bypass");
        require(visitor.booleanBypass.setReturnValue,
                "Boolean query bypass must return a conservative visible result");
        require(!visitor.booleanBypass.runsCallback,
                "Boolean query bypass must not execute query-only rendering geometry");

        require(visitor.sampleBypass.found,
                "Missing Immersive Portals sample-count query bypass");
        require(visitor.sampleBypass.setReturnValue,
                "Sample-count query bypass must return a nonzero conservative result");
        require(!visitor.sampleBypass.runsCallback,
                "Sample-count query bypass must not execute query-only rendering geometry");

        System.out.println("Immersive Portals query bypass contract passed");
    }

    private static QueryBypassVisitor inspectMixin() throws IOException {
        QueryBypassVisitor visitor = new QueryBypassVisitor();
        try(InputStream input = ImmersivePortalsQueryBypassContractTest.class.getClassLoader()
                .getResourceAsStream(MIXIN_RESOURCE)) {
            if(input == null)
                throw new AssertionError("Could not load " + MIXIN_RESOURCE);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return visitor;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class QueryBypassVisitor extends ClassVisitor {
        final MethodState booleanBypass = new MethodState();
        final MethodState sampleBypass = new MethodState();

        QueryBypassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodState state;
            if("vulkanmod$assumeVisibleWithoutGlBooleanQuery".equals(name)) {
                state = booleanBypass;
            } else if("vulkanmod$assumeVisibleWithoutGlSampleQuery".equals(name)) {
                state = sampleBypass;
            } else {
                return null;
            }

            state.found = true;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("java/lang/Runnable") && methodName.equals("run"))
                        state.runsCallback = true;
                    if(owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable")
                            && methodName.equals("setReturnValue"))
                        state.setReturnValue = true;
                }
            };
        }
    }

    private static final class MethodState {
        boolean found;
        boolean runsCallback;
        boolean setReturnValue;
    }
}
