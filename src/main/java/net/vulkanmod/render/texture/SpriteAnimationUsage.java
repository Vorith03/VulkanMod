package net.vulkanmod.render.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.VSpriteContentsI;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class SpriteAnimationUsage {
    private static final ThreadLocal<Set<SpriteContents>> BUILD_SPRITES = new ThreadLocal<>();
    private SpriteAnimationUsage() {}
    public static boolean enabled() {
        return Initializer.CONFIG != null && Initializer.CONFIG.animateOnlyUsedTextures;
    }
    public static void beginBuild() {
        if(enabled()) {
            if(BUILD_SPRITES.get() != null) throw new IllegalStateException("Nested sprite capture");
            BUILD_SPRITES.set(Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }
    public static Set<SpriteContents> endBuild() {
        Set<SpriteContents> sprites = BUILD_SPRITES.get();
        BUILD_SPRITES.remove();
        return sprites == null ? Set.of() : sprites;
    }
    public static void use(TextureAtlasSprite sprite) { if(sprite != null) use(sprite.contents()); }
    public static void use(SpriteContents sprite) {
        if(!enabled() || sprite == null) return;
        VSpriteContentsI contents = (VSpriteContentsI)sprite;
        if(contents.vulkanmod$isStaticSprite()) return;
        SpriteAnimationState state = contents.vulkanmod$animationState();
        if(state == null) return;
        Set<SpriteContents> sprites = BUILD_SPRITES.get();
        if(sprites != null) sprites.add(sprite);
        else if(RenderSystem.isOnRenderThread()) state.use();
    }
}
