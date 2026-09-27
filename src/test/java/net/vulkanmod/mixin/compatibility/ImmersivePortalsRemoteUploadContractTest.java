package net.vulkanmod.mixin.compatibility;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Guards the Vulkan-specific Immersive Portals boundaries required when
 * portal-world LevelRenderers do not own vanilla chunk dispatchers.
 */
public final class ImmersivePortalsRemoteUploadContractTest {
    private static final String REMOTE_UPLOAD_MIXIN_RESOURCE =
            "net/vulkanmod/mixin/compatibility/ImmersivePortalsMyRenderHelperMixin.class";
    private static final String LEVEL_RENDERER_MIXIN_RESOURCE =
            "net/vulkanmod/mixin/compatibility/ImmersivePortalsLevelRendererMixin.class";
    private static final String CALLBACK_INFO =
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String INJECT_DESC =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT_DESC =
            "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String AT_DESC =
            "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String DISPATCHER_CAMERA_TARGET =
            "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;setCamera(Lnet/minecraft/world/phys/Vec3;)V";
    private static final String DISPATCHER_CAMERA_HANDLER_DESC =
            "(Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;Lnet/minecraft/world/phys/Vec3;)V";

    private ImmersivePortalsRemoteUploadContractTest() {
    }

    public static void main(String[] args) throws Exception {
        RemoteUploadVisitor visitor = inspectMixin(REMOTE_UPLOAD_MIXIN_RESOURCE, new RemoteUploadVisitor());
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

        TerrainSetupVisitor terrainVisitor = inspectMixin(
                LEVEL_RENDERER_MIXIN_RESOURCE, new TerrainSetupVisitor());
        require(terrainVisitor.foundMethod,
                "Immersive Portals LevelRenderer compatibility must guard dispatcher camera setup");
        require(terrainVisitor.correctHandlerDescriptor,
                "Dispatcher camera redirect must accept ChunkRenderDispatcher and Vec3");
        require(terrainVisitor.wildcardMethodSelector,
                "Dispatcher camera redirect must scan merged LevelRenderer methods without depending on IP's generated handler name");
        require(terrainVisitor.redirectAtInvoke,
                "Dispatcher camera guard must redirect an INVOKE instruction");
        require(terrainVisitor.redirectTargetsDispatcherCamera,
                "Dispatcher camera guard must target only ChunkRenderDispatcher.setCamera(Vec3)");
        require(terrainVisitor.optionalWithoutIp,
                "Dispatcher camera redirect must remain optional when Immersive Portals is absent");

        ImmersivePortalsShaderReloadContractTest.main(args);
        System.out.println("Immersive Portals vanilla dispatcher contract passed");
    }

    private static <T extends ClassVisitor> T inspectMixin(String resource, T visitor) throws IOException {
        try(InputStream input = ImmersivePortalsRemoteUploadContractTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if(input == null)
                throw new AssertionError("Could not load " + resource);
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

    private static final class TerrainSetupVisitor extends ClassVisitor {
        boolean foundMethod;
        boolean correctHandlerDescriptor;
        boolean wildcardMethodSelector;
        boolean redirectAtInvoke;
        boolean redirectTargetsDispatcherCamera;
        boolean optionalWithoutIp;

        TerrainSetupVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"vulkanmod$skipVanillaChunkDispatcherCamera".equals(name))
                return null;

            foundMethod = true;
            correctHandlerDescriptor = DISPATCHER_CAMERA_HANDLER_DESC.equals(descriptor);

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    if(!REDIRECT_DESC.equals(annotationDescriptor))
                        return null;

                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String elementName, Object value) {
                            if("require".equals(elementName) && Integer.valueOf(0).equals(value))
                                optionalWithoutIp = true;
                        }

                        @Override
                        public AnnotationVisitor visitArray(String elementName) {
                            if(!"method".equals(elementName))
                                return null;

                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String ignored, Object value) {
                                    if("*".equals(value))
                                        wildcardMethodSelector = true;
                                }
                            };
                        }

                        @Override
                        public AnnotationVisitor visitAnnotation(String elementName, String descriptor) {
                            if(!"at".equals(elementName) || !AT_DESC.equals(descriptor))
                                return null;

                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String name, Object value) {
                                    if("value".equals(name) && "INVOKE".equals(value))
                                        redirectAtInvoke = true;
                                    if("target".equals(name) && DISPATCHER_CAMERA_TARGET.equals(value))
                                        redirectTargetsDispatcherCamera = true;
                                }
                            };
                        }
                    };
                }
            };
        }
    }
}
