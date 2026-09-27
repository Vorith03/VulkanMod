package net.vulkanmod.mixin.compatibility;

import net.vulkanmod.mixin.MixinPlugin;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

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
    private static final String CALLBACK_INFO_RETURNABLE =
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
    private static final String INJECT_DESC =
            "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String AT_DESC =
            "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String LEVEL_RENDERER_TARGET =
            "net.minecraft.client.renderer.LevelRenderer";
    private static final String LEVEL_RENDERER_COMPAT_MIXIN =
            "net.vulkanmod.mixin.compatibility.ImmersivePortalsLevelRendererMixin";
    private static final String IP_TERRAIN_OVERRIDE_METHOD =
            "ip_allowOverrideTerrainSetup";
    private static final String DISPATCHER_OWNER =
            "net/minecraft/client/renderer/chunk/ChunkRenderDispatcher";
    private static final String DISPATCHER_CAMERA_DESC =
            "(Lnet/minecraft/world/phys/Vec3;)V";

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
                "Immersive Portals LevelRenderer compatibility must keep Vulkan terrain setup authoritative");
        require(terrainVisitor.injectTargetsTerrainOverride,
                "Terrain setup guard must target IP's ip_allowOverrideTerrainSetup()Z helper");
        require(terrainVisitor.injectAtHead,
                "Terrain setup guard must override IP's helper at method HEAD");
        require(terrainVisitor.cancellable,
                "Terrain setup guard must remain cancellable");
        require(terrainVisitor.optionalWithoutIp,
                "Terrain setup guard must remain optional when Immersive Portals is absent");
        require(terrainVisitor.falseConstants >= 1 && terrainVisitor.setReturnValueCalls == 1,
                "Terrain setup guard must force IP's helper to false exactly once");

        verifyFinalLevelRendererRewrite();
        verifyRewriteFailsClosed();
        verifyRewriteIsInertWithoutIp();

        ImmersivePortalsShaderReloadContractTest.main(args);
        System.out.println("Immersive Portals vanilla dispatcher contract passed");
    }

    private static void verifyFinalLevelRendererRewrite() {
        ClassNode target = newLevelRendererFixture(true, 1);
        MethodNode callback = findMethod(target, "ip$secondaryWorldSetup");
        MethodInsnNode staleCall = findDispatcherCall(callback);
        require(staleCall != null, "fixture must begin with one dispatcher camera invocation");

        new MixinPlugin().postApply(
                LEVEL_RENDERER_TARGET, target, LEVEL_RENDERER_COMPAT_MIXIN, null);

        require(findDispatcherCall(callback) == null,
                "postApply must remove IP's final merged dispatcher camera invocation");
        require(countOpcode(callback, Opcodes.POP2) == 1,
                "postApply must replace the receiver + Vec3 invocation operands with exactly one POP2");
    }

    private static void verifyRewriteFailsClosed() {
        expectIllegalState(
                () -> new MixinPlugin().postApply(
                        LEVEL_RENDERER_TARGET,
                        newLevelRendererFixture(true, 0),
                        LEVEL_RENDERER_COMPAT_MIXIN,
                        null),
                "postApply must reject an IP LevelRenderer with no dispatcher camera invocation");

        expectIllegalState(
                () -> new MixinPlugin().postApply(
                        LEVEL_RENDERER_TARGET,
                        newLevelRendererFixture(true, 2),
                        LEVEL_RENDERER_COMPAT_MIXIN,
                        null),
                "postApply must reject an ambiguous IP LevelRenderer with multiple dispatcher camera invocations");
    }

    private static void verifyRewriteIsInertWithoutIp() {
        ClassNode target = newLevelRendererFixture(false, 1);
        MethodNode callback = findMethod(target, "ip$secondaryWorldSetup");

        new MixinPlugin().postApply(
                LEVEL_RENDERER_TARGET, target, LEVEL_RENDERER_COMPAT_MIXIN, null);

        require(findDispatcherCall(callback) != null,
                "postApply must not rewrite LevelRenderer when IP's merged terrain helper is absent");
        require(countOpcode(callback, Opcodes.POP2) == 0,
                "non-IP LevelRenderer must not gain dispatcher-discard bytecode");
    }

    private static ClassNode newLevelRendererFixture(boolean includeIpMarker, int dispatcherCalls) {
        ClassNode target = new ClassNode(Opcodes.ASM9);
        target.name = "net/minecraft/client/renderer/LevelRenderer";

        if(includeIpMarker) {
            MethodNode marker = new MethodNode(
                    Opcodes.ACC_PRIVATE,
                    IP_TERRAIN_OVERRIDE_METHOD,
                    "()Z",
                    null,
                    null);
            marker.instructions.add(new InsnNode(Opcodes.ICONST_1));
            marker.instructions.add(new InsnNode(Opcodes.IRETURN));
            target.methods.add(marker);
        }

        MethodNode callback = new MethodNode(
                Opcodes.ACC_PRIVATE,
                "ip$secondaryWorldSetup",
                "()V",
                null,
                null);
        for(int i = 0; i < dispatcherCalls; i++) {
            callback.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            callback.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            // Deliberately use an arbitrary method name. The compatibility patch
            // must be descriptor/owner based so production remapping cannot make
            // it miss IP's injected call again.
            callback.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    DISPATCHER_OWNER,
                    "mappedOrObfuscatedName" + i,
                    DISPATCHER_CAMERA_DESC,
                    false));
        }
        callback.instructions.add(new InsnNode(Opcodes.RETURN));
        target.methods.add(callback);
        return target;
    }

    private static MethodNode findMethod(ClassNode target, String name) {
        for(MethodNode method : target.methods) {
            if(name.equals(method.name))
                return method;
        }
        throw new AssertionError("Could not find fixture method " + name);
    }

    private static MethodInsnNode findDispatcherCall(MethodNode method) {
        for(AbstractInsnNode instruction = method.instructions.getFirst();
            instruction != null;
            instruction = instruction.getNext()) {
            if(instruction instanceof MethodInsnNode invocation
                    && invocation.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && DISPATCHER_OWNER.equals(invocation.owner)
                    && DISPATCHER_CAMERA_DESC.equals(invocation.desc)) {
                return invocation;
            }
        }
        return null;
    }

    private static int countOpcode(MethodNode method, int opcode) {
        int count = 0;
        for(AbstractInsnNode instruction = method.instructions.getFirst();
            instruction != null;
            instruction = instruction.getNext()) {
            if(instruction.getOpcode() == opcode)
                count++;
        }
        return count;
    }

    private static void expectIllegalState(ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch(IllegalStateException expected) {
            return;
        } catch(Exception other) {
            throw new AssertionError(message + "; got " + other, other);
        }
        throw new AssertionError(message);
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
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
        boolean injectTargetsTerrainOverride;
        boolean injectAtHead;
        boolean cancellable;
        boolean optionalWithoutIp;
        int falseConstants;
        int setReturnValueCalls;

        TerrainSetupVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"vulkanmod$keepVulkanTerrainSetup".equals(name))
                return null;

            foundMethod = true;

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
                            if("require".equals(elementName) && Integer.valueOf(0).equals(value))
                                optionalWithoutIp = true;
                        }

                        @Override
                        public AnnotationVisitor visitArray(String elementName) {
                            if("method".equals(elementName)) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String ignored, Object value) {
                                        if("ip_allowOverrideTerrainSetup()Z".equals(value))
                                            injectTargetsTerrainOverride = true;
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
                public void visitInsn(int opcode) {
                    if(opcode == Opcodes.ICONST_0)
                        falseConstants++;
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(CALLBACK_INFO_RETURNABLE.equals(owner)
                            && "setReturnValue".equals(methodName)
                            && "(Ljava/lang/Object;)V".equals(methodDescriptor)) {
                        setReturnValueCalls++;
                    }
                }
            };
        }
    }
}
