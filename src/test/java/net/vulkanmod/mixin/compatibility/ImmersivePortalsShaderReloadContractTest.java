package net.vulkanmod.mixin.compatibility;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Guards the ownership boundary for Immersive Portals' custom ShaderInstances.
 * VulkanMod replaces GameRenderer.reloadShaders(), so IP's portal shaders must
 * be appended to every replacement set once its loader signal is ready. The
 * startup bridge must then trigger that single reload path rather than emitting
 * a second untracked set of ShaderInstances.
 */
public final class ImmersivePortalsShaderReloadContractTest {
    private static final String GAME_RENDERER_MIXIN =
            "net/vulkanmod/mixin/render/GameRendererMixin.class";
    private static final String SHADER_COMPAT =
            "net/vulkanmod/compatibility/ImmersivePortalsShaderCompat.class";
    private static final String COMPAT_OWNER =
            "net/vulkanmod/compatibility/ImmersivePortalsShaderCompat";

    private ImmersivePortalsShaderReloadContractTest() {
    }

    public static void main(String[] args) throws Exception {
        ReloadVisitor reload = inspect(GAME_RENDERER_MIXIN, new ReloadVisitor());
        require(reload.foundReload,
                "Could not find GameRendererMixin.reloadShaders()");
        require(reload.appendPortalShadersCalls == 1,
                "Every Vulkan shader reload must append Immersive Portals custom shaders exactly once");

        CompatVisitor compat = inspect(SHADER_COMPAT, new CompatVisitor());
        require(compat.foundAppend,
                "ImmersivePortalsShaderCompat must expose the reload append bridge");
        require(compat.appendEmitCalls == 1,
                "The append bridge must emit Immersive Portals' registered custom shaders exactly once");
        require(compat.foundRebuild,
                "ImmersivePortalsShaderCompat must retain its startup rebuild bridge");
        require(compat.rebuildReloadCalls == 1,
                "Startup IP initialization must invoke the Vulkan GameRenderer shader reload exactly once");
        require(compat.rebuildEmitCalls == 0,
                "Startup rebuild must not emit a second untracked set of Immersive Portals shaders");

        System.out.println("Immersive Portals shader reload lifecycle contract passed");
    }

    private static <T extends ClassVisitor> T inspect(String resource, T visitor) throws IOException {
        try(InputStream input = ImmersivePortalsShaderReloadContractTest.class.getClassLoader()
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

    private static final class ReloadVisitor extends ClassVisitor {
        boolean foundReload;
        int appendPortalShadersCalls;

        ReloadVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if(!"reloadShaders".equals(name))
                return null;

            foundReload = true;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(COMPAT_OWNER.equals(owner)
                            && "appendPortalShadersIfReady".equals(methodName)) {
                        appendPortalShadersCalls++;
                    }
                }
            };
        }
    }

    private static final class CompatVisitor extends ClassVisitor {
        boolean foundAppend;
        boolean foundRebuild;
        int appendEmitCalls;
        int rebuildEmitCalls;
        int rebuildReloadCalls;

        CompatVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            final boolean append = "appendPortalShadersIfReady".equals(name);
            final boolean rebuild = "rebuildShaders".equals(name);
            if(!append && !rebuild)
                return null;

            if(append)
                foundAppend = true;
            if(rebuild)
                foundRebuild = true;

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName,
                                            String methodDescriptor, boolean isInterface) {
                    if(COMPAT_OWNER.equals(owner) && "emitPortalShaders".equals(methodName)) {
                        if(append)
                            appendEmitCalls++;
                        if(rebuild)
                            rebuildEmitCalls++;
                    }
                    if(rebuild
                            && owner.equals("net/vulkanmod/mixin/compatibility/ImmersivePortalsGameRendererInvoker")
                            && methodName.equals("vulkanmod$reloadShaders")) {
                        rebuildReloadCalls++;
                    }
                }
            };
        }
    }
}
