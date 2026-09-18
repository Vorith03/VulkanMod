#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mkdir -p run/mods

build_backup="$(mktemp)"
init_script="$(mktemp --suffix=.gradle)"
cp build.gradle "$build_backup"
cleanup() {
  cp "$build_backup" build.gradle
  rm -f "$build_backup" "$init_script"
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
grep -F "Vulkan smoke test passed" vulkan-smoke-immersive-portals.log
if grep -E 'Validation Error|SYNC-HAZARD' vulkan-smoke-immersive-portals.log; then
  echo "Immersive Portals compatibility smoke produced invalid Vulkan" >&2
  exit 1
fi
