#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mkdir -p run/mods

build_backup="$(mktemp)"
options_backup="$(mktemp)"
init_script="$(mktemp --suffix=.gradle)"
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
if [[ -f run/options.txt ]]; then
  cp run/options.txt "$options_backup"
  options_existed=1
fi

cleanup() {
  cp "$build_backup" build.gradle
  if [[ "$options_existed" -eq 1 ]]; then
    cp "$options_backup" run/options.txt
  else
    rm -f run/options.txt
  fi
  rm -rf "$ci_pack_dir"
  if [[ "$real_pack_staged" -eq 1 ]]; then
    rm -f "$real_base_target" "$real_overlay_target"
  fi
  rm -f "$build_backup" "$options_backup" "$init_script"
}
trap cleanup EXIT

# Register CI-only repositories before ForgeGradle snapshots its deobfuscating
# repository set. Immersive Portals 3.0.7 is published through Curse Maven and
# requires Cloth Config 11.1+.
cat > "$init_script" <<'EOF'
gradle.beforeProject { project ->
    project.repositories {
        maven {
            url = project.uri('https://cursemaven.com')
            content { includeGroup 'curse.maven' }
        }
        maven { url = project.uri('https://maven.shedaniel.me/') }
    }
}
EOF

cat >> build.gradle <<'EOF'

// VULKANMOD_CI_IMMERSIVE_PORTALS_RUNTIME
dependencies {
    runtimeOnly(fg.deobf('curse.maven:immersive-portals-for-forge-355440:6368524')) { transitive = false }
    runtimeOnly(fg.deobf('me.shedaniel.cloth:cloth-config-forge:11.1.136'))
}
EOF

# When supplied, load the real ZIP bytes without ever committing them to the
# repository. CI uses the small synthetic pack because it cannot access private
# user assets; a synthetic pass must never count as a real-pack pass.
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
  smoke_timeout=240s
fi

mkdir -p run
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
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvulkanmod.smokeTest=true -Dvulkanmod.ciImmersivePortalsSmoke=true -Dvulkanmod.validation=true"

timeout "$smoke_timeout" xvfb-run -a ./gradlew --init-script "$init_script" runClient --stacktrace 2>&1 | tee vulkan-smoke-immersive-portals.log

grep -F "Immersive Portals 3.0.7 compatibility mixin smoke passed" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_CLIPPING_SHADER_OK: rendertype_solid transformed source, live clipping uniform, Vulkan pipeline" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_ALIASED_TERRAIN_CLIP_OK: vulkanmod:shaders/core/ci_ip_alias.json -> rendertype_cutout" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_ALIASED_MODEL_VIEW_CLIP_OK: vulkanmod:shaders/core/ci_ip_model_view_alias.json -> rendertype_entity_translucent" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_ALIASED_MODEL_VIEW_CLIP_OK: vulkanmod:shaders/core/ci_ip_particle_alias.json -> particle" vulkan-smoke-immersive-portals.log
verify_args=()
for pack in "${expected_packs[@]}"; do
  verify_args+=(--expected "$pack")
done
python3 scripts/ci/resource-pack-retention.py verify \
  --log vulkan-smoke-immersive-portals.log --options run/options.txt "${verify_args[@]}"
grep -F "Vulkan smoke test passed" vulkan-smoke-immersive-portals.log
if grep -F "Caught error loading resourcepacks, removing all selected resourcepacks" vulkan-smoke-immersive-portals.log; then
  echo "Immersive Portals compatibility smoke caused Minecraft to discard the selected resource pack" >&2
  exit 1
fi
if grep -E 'Validation Error|SYNC-HAZARD' vulkan-smoke-immersive-portals.log; then
  echo "Immersive Portals compatibility smoke produced invalid Vulkan" >&2
  exit 1
fi
