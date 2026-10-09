package net.vulkanmod.gl;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.framebuffer.RenderTargetManager;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30C;

import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD;

/** Single mip-0 texture-backed legacy FBOs. Renderbuffer storage remains unsupported. */
public final class GlFramebuffer {
    private static int nextId = 1;
    private static final Int2ReferenceOpenHashMap<GlFramebuffer> map = new Int2ReferenceOpenHashMap<>();
    private static int boundId;
    private static GlFramebuffer boundFramebuffer;

    public static int genFramebufferId() {
        int id = nextId++;
        map.put(id, new GlFramebuffer());
        return id;
    }

    public static int getBoundFramebufferId() { return boundId; }

    public static boolean isBoundFramebuffer(Framebuffer framebuffer) {
        return boundFramebuffer != null && boundFramebuffer.framebuffer == framebuffer;
    }

    private static void validateTarget(int target) {
        if(target != GL30C.GL_FRAMEBUFFER)
            throw new UnsupportedOperationException("Only unified GL_FRAMEBUFFER bindings are supported");
    }

    public static void bindFramebuffer(int target, int id) {
        validateTarget(target);
        GlFramebuffer next = id == 0 ? null : map.get(id);
        if(id != 0 && next == null) throw new IllegalArgumentException("Unknown framebuffer: " + id);
        boundId = id;
        boundFramebuffer = next;
        if(next == null) {
            if(Renderer.getInstance().isRecordingFrame()) RenderTargetManager.bindMain(false, 0, 0);
        }
        else next.beginRendering();
    }

    public static void deleteFramebuffer(int id) {
        GlFramebuffer removed = map.remove(id);
        if(removed == null) return;
        if(removed == boundFramebuffer) {
            boundId = 0;
            boundFramebuffer = null;
        }
        removed.retireBacking();
    }

    /** Invalidate borrowed framebuffers before a texture's storage/view is retired. */
    public static void textureStorageChanged(int textureId) {
        if(textureId == 0) return;
        for(GlFramebuffer framebuffer : map.values()) {
            if(framebuffer.colorId == textureId || framebuffer.depthId == textureId)
                framebuffer.retireBacking();
        }
    }

    public static void glFramebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        validateTarget(target);
        if(boundFramebuffer == null) throw new IllegalStateException("No generated framebuffer bound");
        if(attachment != GL30C.GL_COLOR_ATTACHMENT0 && attachment != GL30C.GL_DEPTH_ATTACHMENT)
            throw new UnsupportedOperationException("Only color attachment 0 and depth are supported");
        if(texTarget != GL11.GL_TEXTURE_2D || level != 0)
            throw new UnsupportedOperationException("Only mip-0 GL_TEXTURE_2D attachments are supported");
        if(texture != 0 && GlTexture.getTexture(texture) == null)
            throw new IllegalArgumentException("Unknown attachment texture: " + texture);
        boundFramebuffer.retireBacking();
        if(attachment == GL30C.GL_COLOR_ATTACHMENT0) boundFramebuffer.colorId = texture;
        else boundFramebuffer.depthId = texture;
        boundFramebuffer.beginRendering();
    }

    public static void bindRenderbuffer(int target, int id) {
        if(target != GL30C.GL_RENDERBUFFER) throw new IllegalArgumentException("Target is not GL_RENDERBUFFER");
        if(id != 0) throw new UnsupportedOperationException("Legacy renderbuffer storage is not implemented");
    }

    public static void glFramebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer) {
        throw new UnsupportedOperationException("Legacy renderbuffer attachments are not implemented; use RenderTarget or texture attachments");
    }

    public static void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        throw new UnsupportedOperationException("Legacy renderbuffer storage is not implemented; use RenderTarget or texture attachments");
    }

    public static int glCheckFramebufferStatus(int target) {
        validateTarget(target);
        return boundFramebuffer == null ? GL30C.GL_FRAMEBUFFER_COMPLETE : boundFramebuffer.status();
    }

    private int colorId, depthId;
    private Framebuffer framebuffer;
    private RenderPass renderPass;

    private int status() {
        if(colorId == 0 && depthId == 0) return GL30C.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;
        VulkanImage color = colorId == 0 ? null : GlTexture.getVulkanImage(colorId);
        VulkanImage depth = depthId == 0 ? null : GlTexture.getVulkanImage(depthId);
        if((colorId != 0 && (color == null || !color.supportsColorAttachment()))
                || (depthId != 0 && (depth == null || !depth.supportsDepthAttachment())))
            return GL30C.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
        if(color != null && depth != null && (color.width != depth.width || color.height != depth.height))
            return GL30C.GL_FRAMEBUFFER_UNSUPPORTED;
        return GL30C.GL_FRAMEBUFFER_COMPLETE;
    }

    private void beginRendering() {
        Renderer renderer = Renderer.getInstance();
        if(!renderer.isRecordingFrame()) return;
        if(status() != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            // Assembly may bind an incomplete FBO, but it must not leave draws
            // targeting the previously bound, unrelated attachment.
            if(renderer.getBoundRenderPass() != null) renderer.endRenderPass();
            renderer.setBoundFramebuffer(null);
            return;
        }
        if(framebuffer == null) {
            Framebuffer created = new Framebuffer(GlTexture.getVulkanImage(colorId), GlTexture.getVulkanImage(depthId));
            try {
                renderPass = new RenderPass.Builder(created).setLoadOp(VK_ATTACHMENT_LOAD_OP_LOAD).build();
                framebuffer = created;
            } catch(RuntimeException | Error failure) {
                created.cleanUp();
                throw failure;
            }
        }
        RenderTargetManager.bind(framebuffer, renderPass, false, 0, 0);
    }

    private void retireBacking() {
        if(framebuffer == null) return;
        Renderer renderer = Renderer.getInstance();
        if(renderer.getBoundRenderPass() == renderPass) {
            renderer.endRenderPass();
            RenderTargetManager.bindMain(false, 0, 0);
        }
        // This framebuffer borrows the images. Only its native framebuffer/pass die.
        framebuffer.cleanUp();
        renderPass.cleanUp();
        framebuffer = null;
        renderPass = null;
    }
}
