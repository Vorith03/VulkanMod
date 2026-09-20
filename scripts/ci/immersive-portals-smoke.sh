#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mkdir -p run/mods

build_backup="$(mktemp)"
options_backup="$(mktemp)"
init_script="$(mktemp --suffix=.gradle)"
ci_pack_dir="run/resourcepacks/vulkanmod-ci-selected-pack"
options_existed=0

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

# Reproduce the user-visible #720 failure boundary as well as the internal shader
# alias. The real full-pack failure caused Minecraft to remove both selected
# PureBDcraft packs after a Forge shader-registration exception. Use a tiny local
# pack so this smoke proves a selected pack survives the same reload lifecycle
# when the alias bridge succeeds.
mkdir -p "$ci_pack_dir"
cat > "$ci_pack_dir/pack.mcmeta" <<'EOF'
{
  "pack": {
    "pack_format": 15,
    "description": "VulkanMod CI selected-resource-pack retention fixture"
  }
}
EOF

mkdir -p run
touch run/options.txt
if grep -q '^resourcePacks:' run/options.txt; then
  sed -i 's|^resourcePacks:.*$|resourcePacks:["vanilla","file/vulkanmod-ci-selected-pack"]|' run/options.txt
else
  printf '%s\n' 'resourcePacks:["vanilla","file/vulkanmod-ci-selected-pack"]' >> run/options.txt
fi
if grep -q '^incompatibleResourcePacks:' run/options.txt; then
  sed -i 's|^incompatibleResourcePacks:.*$|incompatibleResourcePacks:[]|' run/options.txt
else
  printf '%s\n' 'incompatibleResourcePacks:[]' >> run/options.txt
fi

lvp_icd="$(find /usr/share/vulkan/icd.d -maxdepth 1 -name 'lvp_icd*.json' -print -quit)"
if [[ -z "$lvp_icd" ]]; then
  echo "Lavapipe Vulkan ICD was not installed" >&2
  exit 1
fi

export VK_ICD_FILENAMES="$lvp_icd"
export LIBGL_ALWAYS_SOFTWARE=1
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvulkanmod.smokeTest=true -Dvulkanmod.ciImmersivePortalsSmoke=true -Dvulkanmod.validation=true"

timeout 240s xvfb-run -a ./gradlew --init-script "$init_script" runClient --stacktrace 2>&1 | tee vulkan-smoke-immersive-portals.log

grep -F "Immersive Portals 3.0.7 compatibility mixin smoke passed" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_CLIPPING_SHADER_OK: rendertype_solid transformed source, live clipping uniform, Vulkan pipeline" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_ALIASED_TERRAIN_CLIP_OK: vulkanmod:shaders/core/ci_ip_alias.json -> rendertype_cutout" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_ALIASED_MODEL_VIEW_CLIP_OK: vulkanmod:shaders/core/ci_ip_model_view_alias.json -> rendertype_entity_translucent" vulkan-smoke-immersive-portals.log
grep -F "VULKANMOD_IP_ALIASED_MODEL_VIEW_CLIP_OK: vulkanmod:shaders/core/ci_ip_particle_alias.json -> particle" vulkan-smoke-immersive-portals.log
grep -F "Reloading ResourceManager:" vulkan-smoke-immersive-portals.log | grep -F "file/vulkanmod-ci-selected-pack"
grep -F 'resourcePacks:["vanilla","file/vulkanmod-ci-selected-pack"]' run/options.txt
grep -F "Vulkan smoke test passed" vulkan-smoke-immersive-portals.log
if grep -F "Caught error loading resourcepacks, removing all selected resourcepacks" vulkan-smoke-immersive-portals.log; then
  echo "Immersive Portals compatibility smoke caused Minecraft to discard the selected resource pack" >&2
  exit 1
fi
if grep -E 'Validation Error|SYNC-HAZARD' vulkan-smoke-immersive-portals.log; then
  echo "Immersive Portals compatibility smoke produced invalid Vulkan" >&2
  exit 1
fi
