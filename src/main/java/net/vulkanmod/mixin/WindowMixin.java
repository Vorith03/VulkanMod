package net.vulkanmod.mixin;

import com.mojang.blaze3d.platform.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Options;
import net.vulkanmod.config.VideoResolution;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Final @Shadow private long window;

    @Shadow private boolean vsync;

    @Shadow protected abstract void updateFullscreen(boolean bl);

    @Shadow private boolean fullscreen;

    @Shadow @Final private static Logger LOGGER;

    @Shadow private int windowedX;
    @Shadow private int windowedY;
    @Shadow private int windowedWidth;
    @Shadow private int windowedHeight;
    @Shadow private int x;
    @Shadow private int y;
    @Shadow private int width;
    @Shadow private int height;

    @Shadow @Final private WindowEventHandler eventHandler;

    @Shadow public abstract int getWidth();

    @Shadow public abstract int getHeight();

    @Shadow private int framebufferWidth;

    @Shadow private int framebufferHeight;

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V"))
    private void redirect(int hint, int value) { }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V"))
    private void redirect2(long window) { }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;"))
    private GLCapabilities redirect2() {
        return null;
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",
                    remap = false
            )
    )
    private long createVulkanWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        Initializer.initialize();

        // Forge's default early display is an OpenGL window created before any
        // game mixins can run. Allow Forge to finish its documented handoff, then
        // create a separate NO_API window for Vulkan. Forge still calls its early
        // provider directly during parts of mod bootstrap, so keep the hidden GL
        // window alive until Minecraft construction has completed.
        long forgeWindow = ImmediateWindowHandler.setupMinecraftWindow(width, height, title, monitor);
        if (forgeWindow == 0L) {
            return createNoApiWindow(width.getAsInt(), height.getAsInt(), title.get(), monitor.getAsLong(), null, null);
        }

        int[] oldX = new int[1];
        int[] oldY = new int[1];
        int[] oldWidth = new int[1];
        int[] oldHeight = new int[1];
        GLFW.glfwGetWindowPos(forgeWindow, oldX, oldY);
        GLFW.glfwGetWindowSize(forgeWindow, oldWidth, oldHeight);
        boolean maximized = GLFW.glfwGetWindowAttrib(forgeWindow, GLFW_MAXIMIZED) == GLFW_TRUE;

        // Forge handed this OpenGL context to Minecraft on the render thread and
        // normally relies on Window's GL.createCapabilities() immediately after
        // setupMinecraftWindow(). VulkanMod suppresses that vanilla call because
        // the real game window is NO_API, so initialize capabilities explicitly
        // for the retained splash context before detaching it. Later direct Forge
        // splash ticks can then safely make this context current and issue GL calls.
        GL.createCapabilities();

        FMLLoader.progressWindowTick = () -> { };
        GLFW.glfwMakeContextCurrent(0L);
        GLFW.glfwHideWindow(forgeWindow);
        Initializer.retainForgeEarlyWindow(forgeWindow);

        long vulkanWindow = createNoApiWindow(
                oldWidth[0] > 0 ? oldWidth[0] : width.getAsInt(),
                oldHeight[0] > 0 ? oldHeight[0] : height.getAsInt(),
                title.get(),
                monitor.getAsLong(),
                oldX,
                oldY
        );
        if (maximized && monitor.getAsLong() == 0L) {
            GLFW.glfwMaximizeWindow(vulkanWindow);
        }

        Initializer.LOGGER.info("Created Vulkan NO_API window while retaining hidden Forge splash context");
        return vulkanWindow;
    }

    private static long createNoApiWindow(int width, int height, String title, long monitor, int[] x, int[] y) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        long newWindow = GLFW.glfwCreateWindow(width, height, title, monitor, 0L);
        if (newWindow == 0L) {
            throw new IllegalStateException("Failed to create Vulkan GLFW_NO_API window");
        }

        if (monitor == 0L && x != null && y != null) {
            GLFW.glfwSetWindowPos(newWindow, x[0], y[0]);
        }
        GLFW.glfwShowWindow(newWindow);
        return newWindow;
    }

    @Inject(method = "<init>", at = @At(value = "RETURN"))
    private void getHandle(WindowEventHandler windowEventHandler, ScreenManager screenManager, DisplayData displayData, String string, String string2, CallbackInfo ci) {
        VRenderSystem.setWindow(this.window);
    }

    @Overwrite
    public void updateVsync(boolean vsync) {
        this.vsync = vsync;
        Vulkan.setVsync(vsync);
    }

    @Overwrite
    public void toggleFullScreen() {
        this.fullscreen = !this.fullscreen;
        Options.fullscreenDirty = true;
    }

    @Overwrite
    public void updateDisplay() {
        RenderSystem.flipFrame(this.window);
        if (Options.fullscreenDirty) {
            Options.fullscreenDirty = false;
            this.updateFullscreen(this.vsync);
        }
    }

    @Overwrite
    private void setMode() {
        Config config = Initializer.getConfig();

        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if(this.fullscreen) {
            VideoMode videoMode = config.resolution.getVideoMode();
            if(videoMode == null) {
                LOGGER.error("Not supported resolution, fallback to first supported");
                videoMode = VideoResolution.getVideoResolutions()[0].getVideoMode();
            }
            this.windowedX = this.x;
            this.windowedY = this.y;
            this.windowedWidth = this.width;
            this.windowedHeight = this.height;

            this.x = 0;
            this.y = 0;
            this.width = videoMode.getWidth();
            this.height = videoMode.getHeight();
            GLFW.glfwSetWindowMonitor(this.window, monitor, this.x, this.y, this.width, this.height, videoMode.getRefreshRate());
        }
        else if(config.windowedFullscreen) {
            this.x = 0;
            this.y = 0;
            assert vidMode != null;
            this.width = vidMode.width();
            this.height = vidMode.height();
            GLFW.glfwSetWindowAttrib(this.window, GLFW_DECORATED, GLFW_FALSE);
            GLFW.glfwSetWindowMonitor(this.window, 0L, this.x, this.y, this.width, this.height, -1);
        } else {
            this.x = this.windowedX;
            this.y = this.windowedY;
            this.width = this.windowedWidth;
            this.height = this.windowedHeight;
            GLFW.glfwSetWindowAttrib(this.window, GLFW_DECORATED, GLFW_TRUE);
            GLFW.glfwSetWindowMonitor(this.window, 0L, this.x, this.y, this.width, this.height, -1);
        }
    }

    @Overwrite
    private void onFramebufferResize(long window, int width, int height) {
        if (window == this.window) {
            int k = this.getWidth();
            int m = this.getHeight();
            if (width != 0 && height != 0) {
                this.framebufferWidth = width;
                this.framebufferHeight = height;
                if (this.framebufferWidth != k || this.framebufferHeight != m) {
                    this.eventHandler.resizeDisplay();
                }
            }

            if(width > 0 && height > 0)
                Renderer.scheduleSwapChainUpdate();
        }
    }

    @Overwrite
    private void onResize(long window, int width, int height) {
        this.width = width;
        this.height = height;

        if(width > 0 && height > 0)
            Renderer.scheduleSwapChainUpdate();
    }
}
