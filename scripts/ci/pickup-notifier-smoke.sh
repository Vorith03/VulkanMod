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

# Register Curse Maven before ForgeGradle snapshots repositories so the exact
# Create Chronicles Pick Up Notifier/Puzzles Lib artifacts can be deobfuscated
# for the Mojmap runClient fixture.
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

// VULKANMOD_CI_PICKUP_NOTIFIER_RUNTIME
dependencies {
    runtimeOnly(fg.deobf('curse.maven:pick-up-notifier-351441:4613538')) { transitive = false }
    runtimeOnly(fg.deobf('curse.maven:puzzles-lib-495476:6283733')) { transitive = false }
}
EOF

lvp_icd="$(find /usr/share/vulkan/icd.d -maxdepth 1 -name 'lvp_icd*.json' -print -quit)"
if [[ -z "$lvp_icd" ]]; then
  echo "Lavapipe Vulkan ICD was not installed" >&2
  exit 1
fi

export VK_ICD_FILENAMES="$lvp_icd"
export LIBGL_ALWAYS_SOFTWARE=1
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvulkanmod.smokeTest=true -Dvulkanmod.ciPickupNotifierSmoke=true -Dvulkanmod.validation=true"

timeout 240s xvfb-run -a ./gradlew --init-script "$init_script" runClient --stacktrace 2>&1 | tee vulkan-smoke-pickup-notifier.log

grep -F "Pick Up Notifier 8.0.0 framebuffer compatibility smoke passed" vulkan-smoke-pickup-notifier.log
grep -F "Vulkan smoke test passed" vulkan-smoke-pickup-notifier.log
if grep -E 'Validation Error|SYNC-HAZARD' vulkan-smoke-pickup-notifier.log; then
  echo "Pick Up Notifier framebuffer smoke produced invalid Vulkan" >&2
  exit 1
fi
