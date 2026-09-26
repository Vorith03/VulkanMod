package net.vulkanmod.mixin.render;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Guards the legacy/converted ShaderInstance state reconciliation used by Forge
 * and Immersive Portals shaders. These uniforms are live Minecraft render state,
 * not static JSON defaults; dropping one can make otherwise-valid entity/item
 * geometry render black, fogged, misprojected, or otherwise invisible.
 */
public final class ShaderInstanceLegacyApplyContractTest {
    private static final String SHADER_INSTANCE_MIXIN_RESOURCE =
            "net/vulkanmod/mixin/render/ShaderInstanceM.class";

    private static final Set<String> REQUIRED_RENDER_SYSTEM_GETTERS = Set.of(
            "getModelViewMatrix",
            "getProjectionMatrix",
            "getInverseViewRotationMatrix",
            "getShaderColor",
            "getShaderGlintAlpha",
            "getShaderFogStart",
            "getShaderFogEnd",
            "getShaderFogColor",
            "getShaderFogShape",
            "getTextureMatrix",
            "getShaderGameTime",
            "getShaderLineWidth"
    );

    private ShaderInstanceLegacyApplyContractTest() {
    }

    public static void main(String[] args) throws Exception {
        ApplyVisitor visitor = inspectShaderInstanceMixin();
        require(visitor.foundApply, "Could not find ShaderInstanceM.apply()");

        Set<String> missing = new HashSet<>(REQUIRED_RENDER_SYSTEM_GETTERS);
        missing.removeAll(visitor.renderSystemGetters);
        require(missing.isEmpty(),
                "ShaderInstanceM.apply() is missing legacy render-state getters: " + missing);
        require(visitor.minecraftGetInstance,
                "ShaderInstanceM.apply() must obtain Minecraft for ScreenSize");
        require(visitor.minecraftGetWindow,
                "ShaderInstanceM.apply() must obtain the current Window for ScreenSize");
        require(visitor.windowWidth && visitor.windowHeight,
                "ShaderInstanceM.apply() must refresh both ScreenSize dimensions");
        require(visitor.shaderStateActivate,
                "ShaderInstanceM.apply() must activate converted shader sampler state");

        System.out.println("ShaderInstance legacy apply contract passed");
    }

    private static ApplyVisitor inspectShaderInstanceMixin() throws IOException {
        ApplyVisitor visitor = new ApplyVisitor();
        try(InputStream input = ShaderInstanceLegacyApplyContractTest.class.getClassLoader()
                .getResourceAsStream(SHADER_INSTANCE_MIXIN_RESOURCE)) {
            if(input == null)
                throw new AssertionError("Could not load " + SHADER_INSTANCE_MIXIN_RESOURCE);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return visitor;
    }

    private static void require(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }

    private static final class ApplyVisitor extends ClassVisitor {
        boolean foundApply;
        final Set<String> renderSystemGetters = new HashSet<>();
        boolean minecraftGetInstance;
        boolean minecraftGetWindow;
        boolean windowWidth;
        boolean windowHeight;
        boolean shaderStateActivate;

        ApplyVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"apply".equals(name) || !"()V".equals(descriptor))
                return null;

            foundApply = true;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(owner.equals("com/mojang/blaze3d/systems/RenderSystem")
                            && REQUIRED_RENDER_SYSTEM_GETTERS.contains(methodName)) {
                        renderSystemGetters.add(methodName);
                    } else if(owner.equals("net/minecraft/client/Minecraft")
                            && methodName.equals("getInstance")) {
                        minecraftGetInstance = true;
                    } else if(owner.equals("net/minecraft/client/Minecraft")
                            && methodName.equals("getWindow")) {
                        minecraftGetWindow = true;
                    } else if(owner.equals("com/mojang/blaze3d/platform/Window")
                            && methodName.equals("getWidth")) {
                        windowWidth = true;
                    } else if(owner.equals("com/mojang/blaze3d/platform/Window")
                            && methodName.equals("getHeight")) {
                        windowHeight = true;
                    } else if(owner.equals("net/vulkanmod/vulkan/shader/ShaderRenderState")
                            && methodName.equals("activate")) {
                        shaderStateActivate = true;
                    }
                }
            };
        }
    }
}
