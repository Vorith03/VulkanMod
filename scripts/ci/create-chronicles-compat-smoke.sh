#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mkdir -p run/mods

build_backup="$(mktemp)"
options_backup="$(mktemp)"
entity_culling_backup="$(mktemp)"
entity_culling_existed=0
init_script="$(mktemp --suffix=.gradle)"
mods_backup_dir="$(mktemp -d)"
ci_pack_dir="run/resourcepacks/vulkanmod-ci-selected-pack"
real_base_pack="${VULKANMOD_REAL_BASE_PACK:-}"
real_overlay_pack="${VULKANMOD_REAL_OVERLAY_PACK:-}"
real_base_target="run/resourcepacks/vulkanmod-real-base-64x.zip"
real_overlay_target="run/resourcepacks/vulkanmod-real-overlay-64x.zip"
real_pack_staged=0
options_existed=0

if [[ -n "$real_base_pack" && -z "$real_overlay_pack" || -z "$real_base_pack" && -n "$real_overlay_pack" ]]; then
  echo "Set both VULKANMOD_REAL_BASE_PACK and VULKANMOD_REAL_OVERLAY_PACK" >&2
  exit 1
fi

cp build.gradle "$build_backup"
if [[ -f run/config/entityculling.json ]]; then
  cp run/config/entityculling.json "$entity_culling_backup"
  entity_culling_existed=1
fi
if [[ -f run/options.txt ]]; then
  cp run/options.txt "$options_backup"
  options_existed=1
fi

shopt -s nullglob
for jar in run/mods/*.jar; do
  cp -a "$jar" "$mods_backup_dir/"
done
rm -f run/mods/*.jar
shopt -u nullglob

cleanup() {
  local status=$?
  set +e
  cp "$build_backup" build.gradle
  if [[ "$entity_culling_existed" -eq 1 ]]; then
    cp "$entity_culling_backup" run/config/entityculling.json
  else
    rm -f run/config/entityculling.json
  fi
  if [[ "$options_existed" -eq 1 ]]; then
    cp "$options_backup" run/options.txt
  else
    rm -f run/options.txt
  fi
  rm -rf "$ci_pack_dir"
  if [[ "$real_pack_staged" -eq 1 ]]; then
    rm -f "$real_base_target" "$real_overlay_target"
  fi
  rm -f run/mods/*.jar
  shopt -s nullglob
  for jar in "$mods_backup_dir"/*.jar; do
    cp -a "$jar" run/mods/
  done
  shopt -u nullglob
  rm -rf "$mods_backup_dir"
  rm -f "$build_backup" "$options_backup" "$init_script" "$entity_culling_backup"
  exit "$status"
}
trap cleanup EXIT

# Register every repository before ForgeGradle snapshots the deobfuscating
# repository set. This fixture intentionally represents one compatible slice of
# Create Chronicles rather than repeatedly launching one-mod mini-fixtures.
cat > "$init_script" <<'EOF'
gradle.beforeProject { project ->
    project.repositories {
        maven {
            url = project.uri('https://cursemaven.com')
            content { includeGroup 'curse.maven' }
        }
        maven { url = project.uri('https://maven.shedaniel.me/') }
        maven { url = project.uri('https://maven.ftb.dev/releases') }
        maven { url = project.uri('https://maven.architectury.dev/') }
        maven { url = project.uri('https://api.modrinth.com/maven') }
        maven { url = project.uri('https://modmaven.dev/') }
    }
}
EOF

cat >> build.gradle <<'EOF'

// VULKANMOD_CI_CREATE_CHRONICLES_COMPAT_RUNTIME
dependencies {
    runtimeOnly(fg.deobf('dev.ftb.mods:ftb-library-forge:2001.2.13')) { transitive = false }
    runtimeOnly(fg.deobf('dev.architectury:architectury-forge:9.0.8')) { transitive = false }
    runtimeOnly(fg.deobf('curse.maven:pick-up-notifier-351441:4613538')) { transitive = false }
    runtimeOnly(fg.deobf('curse.maven:puzzles-lib-495476:6283733')) { transitive = false }
    runtimeOnly(fg.deobf('curse.maven:immersive-portals-for-forge-355440:6368524')) { transitive = false }
    runtimeOnly(fg.deobf('me.shedaniel.cloth:cloth-config-forge:11.1.136'))
    runtimeOnly(fg.deobf('curse.maven:distant-horizons-508933:8389142')) { transitive = false }
    runtimeOnly(fg.deobf('curse.maven:entityculling-448233:5968677')) { transitive = false }
    runtimeOnly fg.deobf('maven.modrinth:Wb5oqrBJ:45EJNtBe')
    runtimeOnly fg.deobf('com.jozufozu.flywheel:flywheel-forge-1.20.1:0.6.11-13')
    runtimeOnly fg.deobf('maven.modrinth:LNytGWDc:6R069CcK')
}
EOF

# Keep the resource-pack retention coverage from the Immersive Portals fixture.
# Private real packs are staged only when CI supplies both paths; otherwise the
# deterministic synthetic pack exercises the same selected-pack lifecycle.
if [[ -n "$real_base_pack" ]]; then
  python3 scripts/ci/resource-pack-retention.py preflight "$real_base_pack" "$real_overlay_pack"
  mkdir -p run/resourcepacks
  if [[ -e "$real_base_target" || -e "$real_overlay_target" ]]; then
    echo "Refusing to overwrite existing real-pack smoke fixture" >&2
    exit 1
  fi
  real_pack_staged=1
  cp -- "$real_base_pack" "$real_base_target"
  cp -- "$real_overlay_pack" "$real_overlay_target"
  selected_packs='resourcePacks:["vanilla","file/vulkanmod-real-base-64x.zip","file/vulkanmod-real-overlay-64x.zip"]'
  expected_packs=("file/vulkanmod-real-base-64x.zip" "file/vulkanmod-real-overlay-64x.zip")
  smoke_timeout=900s
else
  mkdir -p "$ci_pack_dir"
  cat > "$ci_pack_dir/pack.mcmeta" <<'EOF'
{
  "pack": {
    "pack_format": 15,
    "description": "VulkanMod CI selected-resource-pack retention fixture"
  }
}
EOF
  selected_packs='resourcePacks:["vanilla","file/vulkanmod-ci-selected-pack"]'
  expected_packs=("file/vulkanmod-ci-selected-pack")
  smoke_timeout=420s
fi

mkdir -p run
mkdir -p run/config
# Render occlusion only: this fixture does not change client simulation ticks.
printf '%s\n' '{"tickCulling":false}' > run/config/entityculling.json
touch run/options.txt
if grep -q '^resourcePacks:' run/options.txt; then
  sed -i "s|^resourcePacks:.*$|$selected_packs|" run/options.txt
else
  printf '%s\n' "$selected_packs" >> run/options.txt
fi
if grep -q '^incompatibleResourcePacks:' run/options.txt; then
  sed -i 's|^incompatibleResourcePacks:.*$|incompatibleResourcePacks:[]|' run/options.txt
else
  printf '%s\n' 'incompatibleResourcePacks:[]' >> run/options.txt
fi

lvp_icd="$(find /usr/share/vulkan/icd.d -maxdepth 1 -name 'lvp_icd*.json' -print -quit 2>/dev/null || true)"
if [[ -z "$lvp_icd" ]]; then
  echo "Lavapipe Vulkan ICD was not installed" >&2
  exit 1
fi

export VK_ICD_FILENAMES="$lvp_icd"
export LIBGL_ALWAYS_SOFTWARE=1
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvulkanmod.smokeTest=true -Dvulkanmod.ciFtbLibrarySmoke=true -Dvulkanmod.ciPickupNotifierSmoke=true -Dvulkanmod.ciImmersivePortalsSmoke=true -Dvulkanmod.ciDistantHorizonsSmoke=true -Dvulkanmod.ciCreateStencilSmoke=true -Dvulkanmod.ciEntityCullingSmoke=true -Dvulkanmod.validation=true -Dmixin.debug.export=true -Dmixin.debug.export.filter=net.minecraft.client.renderer.* -Dmixin.debug.export.decompile=false"

rm -rf run/.mixin.out .mixin.out
timeout "$smoke_timeout" xvfb-run -a ./gradlew --init-script "$init_script" runClient --stacktrace 2>&1 | tee vulkan-smoke-create-chronicles-compat.log

# Preserve the transformed-bytecode assertion that guards the semantic
# Immersive Portals terrain override boundary.
exported_level_renderer="$(find run/.mixin.out .mixin.out \
  -type f -path '*/net/minecraft/client/renderer/LevelRenderer.class' \
  -print -quit 2>/dev/null || true)"
if [[ -z "$exported_level_renderer" ]]; then
  echo "Mixin did not export the final LevelRenderer for Immersive Portals verification" >&2
  exit 1
fi
javap -c -p "$exported_level_renderer" > vulkan-smoke-create-chronicles-compat-levelrenderer.javap
grep -F "ip_allowOverrideTerrainSetup" vulkan-smoke-create-chronicles-compat-levelrenderer.javap
python3 - vulkan-smoke-create-chronicles-compat-levelrenderer.javap <<'PY'
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
lines = path.read_text(encoding="utf-8").splitlines()
method_blocks = []
current = None
for line in lines:
    stripped = line.strip()
    is_method_header = (
        line.startswith("  ")
        and not line.startswith("    ")
        and "(" in stripped
        and stripped.endswith(";")
    )
    if is_method_header:
        if current is not None:
            method_blocks.append("\n".join(current))
        current = [line]
    elif current is not None:
        current.append(line)
if current is not None:
    method_blocks.append("\n".join(current))

helper_call = "ip_allowOverrideTerrainSetup:()Z"
dispatcher = re.compile(
    r"ChunkRenderDispatcher\.[^:\s]+:\(Lnet/minecraft/world/phys/Vec3;\)V"
)
helper_contexts = [block for block in method_blocks if helper_call in block]
if not helper_contexts:
    raise SystemExit(
        "Final LevelRenderer has no method invoking Immersive Portals' terrain override helper"
    )

contextual_survivors = sum(len(dispatcher.findall(block)) for block in helper_contexts)
if contextual_survivors != 0:
    raise SystemExit(
        "Final LevelRenderer still invokes ChunkRenderDispatcher(Vec3) inside an Immersive Portals terrain-override context"
    )

global_survivors = len(dispatcher.findall("\n".join(lines)))
if global_survivors != 1:
    raise SystemExit(
        "Expected the one unrelated Immersive Portals 3.0.7 ChunkRenderDispatcher(Vec3) call to survive; "
        f"found {global_survivors}"
    )

print("Immersive Portals dispatcher rewrite selected only the terrain-override context")
PY
python3 scripts/ci/entity-culling-hook-contract.py vulkan-smoke-create-chronicles-compat-levelrenderer.javap
exported_game_renderer="$(find run/.mixin.out .mixin.out \
  -type f -path '*/net/minecraft/client/renderer/GameRenderer.class' \
  -print -quit 2>/dev/null || true)"
if [[ -z "$exported_game_renderer" ]]; then
  echo "Mixin did not export GameRenderer for portal scale-fallback verification" >&2
  exit 1
fi
javap -c -p "$exported_game_renderer" > vulkan-render-scale-portals-gamerenderer.javap
python3 scripts/ci/render-scale-hook-contract.py vulkan-render-scale-portals-gamerenderer.javap --portals

# Every compatibility contract still has its own positive marker. A combined
# launch is successful only when all of them are observed in the same process.
grep -F "FTB Library scissor compatibility mixin smoke passed" vulkan-smoke-create-chronicles-compat.log
grep -F "Pick Up Notifier 8.0.0 framebuffer compatibility smoke passed" vulkan-smoke-create-chronicles-compat.log
grep -F "Immersive Portals 3.0.7 compatibility mixin smoke passed" vulkan-smoke-create-chronicles-compat.log
grep -F "VULKANMOD_IP_PORTAL_MATRIX_RESTORE_OK: explicit portal matrices survive converted ShaderInstance.apply" vulkan-smoke-create-chronicles-compat.log
grep -F "VULKANMOD_IP_CLIPPING_SHADER_OK: rendertype_solid transformed source, live clipping uniform, Vulkan pipeline" vulkan-smoke-create-chronicles-compat.log
grep -F "VULKANMOD_IP_ALIASED_TERRAIN_CLIP_OK: vulkanmod:shaders/core/ci_ip_alias.json -> rendertype_cutout" vulkan-smoke-create-chronicles-compat.log
grep -F "VULKANMOD_IP_ALIASED_MODEL_VIEW_CLIP_OK: vulkanmod:shaders/core/ci_ip_model_view_alias.json -> rendertype_entity_translucent" vulkan-smoke-create-chronicles-compat.log
grep -F "VULKANMOD_IP_ALIASED_MODEL_VIEW_CLIP_OK: vulkanmod:shaders/core/ci_ip_particle_alias.json -> particle" vulkan-smoke-create-chronicles-compat.log
grep -F "Distant Horizons OpenGL LOD rendering is unavailable under Vulkan" vulkan-smoke-create-chronicles-compat.log
grep -F "Distant Horizons Forge afterLevelRenderEvent compatibility target verified without early class initialization" vulkan-smoke-create-chronicles-compat.log
grep -F "Distant Horizons 3.2.0-b compatibility smoke passed" vulkan-smoke-create-chronicles-compat.log
grep -F "chat_heads" vulkan-smoke-create-chronicles-compat.log
grep -F "Flywheel 0.6 compatibility smoke test passed" vulkan-smoke-create-chronicles-compat.log
grep -F "Flywheel CPU adapter smoke passed" vulkan-smoke-create-chronicles-compat.log
grep -F "EntityCulling native compatibility smoke passed" vulkan-smoke-create-chronicles-compat.log
grep -F "Forge RenderTarget stencil capability smoke passed" vulkan-smoke-create-chronicles-compat.log
grep -F "Create 0.5.1.j stencil compatibility mixin target loaded" vulkan-smoke-create-chronicles-compat.log
grep -F "Vulkan smoke test passed" vulkan-smoke-create-chronicles-compat.log

verify_args=()
for pack in "${expected_packs[@]}"; do
  verify_args+=(--expected "$pack")
done
python3 scripts/ci/resource-pack-retention.py verify \
  --log vulkan-smoke-create-chronicles-compat.log --options run/options.txt "${verify_args[@]}"

if grep -F "Caught error loading resourcepacks, removing all selected resourcepacks" vulkan-smoke-create-chronicles-compat.log; then
  echo "Combined compatibility smoke caused Minecraft to discard the selected resource pack" >&2
  exit 1
fi
if grep -E 'chat_heads\.mixins\.json:ChatComponentMixin.*FAILED|InvalidInjectionException.*chatheads\$captureGuiMessage' vulkan-smoke-create-chronicles-compat.log; then
  echo "Chat Heads mixin compatibility regression detected" >&2
  exit 1
fi
if grep -E 'FATAL ERROR in native method|No context is current|Validation Error|SYNC-HAZARD' vulkan-smoke-create-chronicles-compat.log; then
  echo "Combined Create Chronicles compatibility smoke entered an unsafe graphics path" >&2
  exit 1
fi
