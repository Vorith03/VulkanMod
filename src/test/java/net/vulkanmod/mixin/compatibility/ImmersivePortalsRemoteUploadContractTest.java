package net.vulkanmod.mixin.compatibility;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Guards the Vulkan-specific Immersive Portals remote-upload boundary that is
 * required when portal-world LevelRenderers do not own vanilla chunk
 * dispatchers.
 */
public final class ImmersivePortalsRemoteUploadContractTest {
    private static final String MIXIN_RESOURCE =
            "net/vulkanmod/mixin/compatibility/ImmersivePortalsMyRenderHelperMixin.class";
    private static final String CALLBACK_INFO =
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String INJECT_DESC =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String AT_DESC =
            "Lorg/spongepowered/asm/mixin/injection/At;";

    private ImmersivePortalsRemoteUploadContractTest() {
    }

    public static void main(String[] args) throws Exception {
        RemoteUploadVisitor visitor = inspectMixin();
        require(visitor.foundMethod,
                "Immersive Portals compatibility mixin must define the remote-upload guard");
        require(visitor.staticMethod,
                "Immersive Portals earlyRemoteUpload guard must remain static");
        require(visitor.injectTargetsEarlyRemoteUpload,
                "Remote-upload guard must inject into earlyRemoteUpload()V");
        require(visitor.injectAtHead,
                "Remote-upload guard must cancel at method HEAD before dispatcher access");
        require(visitor.cancellable,
                "Remote-upload guard injection must remain cancellable");
        require(visitor.cancelCalls == 1,
                "Remote-upload guard must cancel exactly once");
        System.out.println("Immersive Portals remote upload contract passed");
    }

    private static RemoteUploadVisitor inspectMixin() throws IOException {
        RemoteUploadVisitor visitor = new RemoteUploadVisitor();
        try(InputStream input = ImmersivePortalsRemoteUploadContractTest.class.getClassLoader()
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

    private static final class RemoteUploadVisitor extends ClassVisitor {
        boolean foundMethod;
        boolean staticMethod;
        boolean injectTargetsEarlyRemoteUpload;
        boolean injectAtHead;
        boolean cancellable;
        int cancelCalls;

        RemoteUploadVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"vulkanmod$skipVanillaRemoteChunkUpload".equals(name))
                return null;

            foundMethod = true;
            staticMethod = (access & Opcodes.ACC_STATIC) != 0;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    if(!INJECT_DESC.equals(annotationDescriptor))
                        return null;

                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String elementName, Object value) {
                            if("cancellable".equals(elementName) && Boolean.TRUE.equals(value))
                                cancellable = true;
                        }

                        @Override
                        public AnnotationVisitor visitArray(String elementName) {
                            if("method".equals(elementName)) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String ignored, Object value) {
                                        if("earlyRemoteUpload()V".equals(value))
                                            injectTargetsEarlyRemoteUpload = true;
                                    }
                                };
                            }

                            if("at".equals(elementName)) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                                        if(!AT_DESC.equals(descriptor))
                                            return null;
                                        return new AnnotationVisitor(Opcodes.ASM9) {
                                            @Override
                                            public void visit(String name, Object value) {
                                                if("value".equals(name) && "HEAD".equals(value))
                                                    injectAtHead = true;
                                            }
                                        };
                                    }
                                };
                            }

                            return null;
                        }
                    };
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(CALLBACK_INFO.equals(owner)
                            && "cancel".equals(methodName)
                            && "()V".equals(methodDescriptor)) {
                        cancelCalls++;
                    }
                }
            };
        }
    }
}
