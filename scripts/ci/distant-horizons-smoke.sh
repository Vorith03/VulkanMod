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

# Distant Horizons 3.2.0-b for 1.20.1 is published through Curse Maven.
cat > "$init_script" <<'EOF'
gradle.beforeProject { project ->
    project.repositories {
        maven {
            url = project.uri('https://cursemaven.com')
            content { includeGroup 'curse.maven' }
        }
    }
}
EOF

cat >> build.gradle <<'EOF'

// VULKANMOD_CI_DISTANT_HORIZONS_RUNTIME
dependencies {
    runtimeOnly(fg.deobf('curse.maven:distant-horizons-508933:8389142')) { transitive = false }
}
EOF

lvp_icd="$(find /usr/share/vulkan/icd.d -maxdepth 1 -name 'lvp_icd*.json' -print -quit)"
if [[ -z "$lvp_icd" ]]; then
  echo "Lavapipe Vulkan ICD was not installed" >&2
  exit 1
fi

export VK_ICD_FILENAMES="$lvp_icd"
export LIBGL_ALWAYS_SOFTWARE=1
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvulkanmod.smokeTest=true -Dvulkanmod.ciDistantHorizonsSmoke=true -Dvulkanmod.validation=true"

timeout 240s xvfb-run -a ./gradlew --init-script "$init_script" runClient --stacktrace 2>&1 | tee vulkan-smoke-distant-horizons.log

grep -F "Distant Horizons OpenGL LOD rendering is unavailable under Vulkan" vulkan-smoke-distant-horizons.log
grep -F "Distant Horizons 3.2.0-b compatibility smoke passed" vulkan-smoke-distant-horizons.log
grep -F "Vulkan smoke test passed" vulkan-smoke-distant-horizons.log
if grep -E 'FATAL ERROR in native method|No context is current|Validation Error|SYNC-HAZARD' vulkan-smoke-distant-horizons.log; then
  echo "Distant Horizons compatibility smoke entered an unsafe graphics path" >&2
  exit 1
fi
