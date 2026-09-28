#!/usr/bin/env python3
"""Validate production-safe Immersive Portals mixin anchors in the distributable JAR."""

from pathlib import Path
import zipfile


JAR_GLOB = "VulkanMod_Forge_1.20.1-*-all.jar"
VIEW_AREA_MIXIN = (
    "net/vulkanmod/mixin/compatibility/"
    "ImmersivePortalsViewAreaRendererMixin.class"
)
RENDER_HELPER_MIXIN = (
    "net/vulkanmod/mixin/compatibility/"
    "ImmersivePortalsMyRenderHelperMixin.class"
)
STABLE_TARGET = (
    b"Lqouteall/imm_ptl/core/render/ViewAreaRenderer;"
    b"buildPortalViewAreaTrianglesBuffer("
    b"Lnet/minecraft/world/phys/Vec3;"
    b"Lqouteall/imm_ptl/core/render/PortalRenderable;"
    b"Lnet/minecraft/world/phys/Vec3;F)V"
)
FORBIDDEN_DEV_ONLY_TARGET = (
    b"Lnet/minecraft/client/renderer/ShaderInstance;apply()V"
)
FORBIDDEN_EARLY_FRAMEBUFFER_TARGET = (
    b"Lcom/mojang/blaze3d/platform/GlStateManager;_viewport(IIII)V"
)


def fail(message: str) -> None:
    raise SystemExit(message)


def require_stable_portal_draw_anchor(mixin_bytes: bytes, mixin_name: str) -> None:
    if STABLE_TARGET not in mixin_bytes:
        fail(
            f"Packaged {mixin_name} matrix repair is not anchored on "
            "IP's stable buildPortalViewAreaTrianglesBuffer call"
        )


def main() -> None:
    jars = sorted(Path("build/libs").glob(JAR_GLOB))
    if len(jars) != 1:
        fail(f"Expected exactly one distributable JAR matching {JAR_GLOB}, found {len(jars)}")

    jar = jars[0]
    with zipfile.ZipFile(jar) as archive:
        try:
            view_area_bytes = archive.read(VIEW_AREA_MIXIN)
            render_helper_bytes = archive.read(RENDER_HELPER_MIXIN)
        except KeyError as exc:
            fail(f"Distributable is missing required IP compatibility mixin: {exc}")

    require_stable_portal_draw_anchor(view_area_bytes, "ImmersivePortalsViewAreaRendererMixin")
    require_stable_portal_draw_anchor(render_helper_bytes, "ImmersivePortalsMyRenderHelperMixin")

    if FORBIDDEN_DEV_ONLY_TARGET in view_area_bytes:
        fail(
            "Packaged Immersive Portals view-area mixin still targets Mojmap "
            "ShaderInstance.apply() with remap=false; that anchor fails in production Forge"
        )

    if FORBIDDEN_EARLY_FRAMEBUFFER_TARGET in render_helper_bytes:
        fail(
            "Packaged Immersive Portals framebuffer matrix repair still targets _viewport; "
            "that call occurs before IP sets and applies its explicit portal matrices, so the "
            "restore is overwritten before the composite draw"
        )

    print(
        "Immersive Portals packaged injection anchor contract passed for view-area and "
        f"framebuffer composite paths: {jar.name}"
    )


if __name__ == "__main__":
    main()
