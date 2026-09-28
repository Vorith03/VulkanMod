#!/usr/bin/env python3
"""Validate production-safe Immersive Portals mixin anchors in the distributable JAR."""

from pathlib import Path
import sys
import zipfile


JAR_GLOB = "VulkanMod_Forge_1.20.1-*-all.jar"
VIEW_AREA_MIXIN = (
    "net/vulkanmod/mixin/compatibility/"
    "ImmersivePortalsViewAreaRendererMixin.class"
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


def fail(message: str) -> None:
    raise SystemExit(message)


def main() -> None:
    jars = sorted(Path("build/libs").glob(JAR_GLOB))
    if len(jars) != 1:
        fail(f"Expected exactly one distributable JAR matching {JAR_GLOB}, found {len(jars)}")

    jar = jars[0]
    with zipfile.ZipFile(jar) as archive:
        try:
            mixin_bytes = archive.read(VIEW_AREA_MIXIN)
        except KeyError:
            fail(f"Distributable is missing {VIEW_AREA_MIXIN}")

    if STABLE_TARGET not in mixin_bytes:
        fail(
            "Packaged Immersive Portals view-area matrix repair is not anchored on "
            "IP's stable buildPortalViewAreaTrianglesBuffer call"
        )

    if FORBIDDEN_DEV_ONLY_TARGET in mixin_bytes:
        fail(
            "Packaged Immersive Portals view-area mixin still targets Mojmap "
            "ShaderInstance.apply() with remap=false; that anchor fails in production Forge"
        )

    print(f"Immersive Portals packaged injection anchor contract passed: {jar.name}")


if __name__ == "__main__":
    main()
