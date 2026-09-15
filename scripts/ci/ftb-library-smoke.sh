#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mkdir -p run/mods
rm -f run/mods/CrashAssistant-*.jar run/mods/chat_heads-*.jar run/mods/flywheel-*.jar

build_backup="$(mktemp)"
init_script="$(mktemp --suffix=.gradle)"
cp build.gradle "$build_backup"
cleanup() {
  cp "$build_backup" build.gradle
  rm -f "$build_backup" "$init_script"
}
trap cleanup EXIT

# ForgeGradle snapshots the repositories used by its deobfuscating repository
# before the project build script has finished evaluating. Register the two
# CI-only mod repositories from an init script before project evaluation so
# fg.deobf() can resolve the real FTB Library and Architectury artifacts.
cat > "$init_script" <<'EOF'
gradle.beforeProject { project ->
    project.repositories {
        maven { url = project.uri('https://maven.ftb.dev/releases') }
        maven { url = project.uri('https://maven.architectury.dev/') }
    }
}
EOF

# Keep FTB Library out of VulkanMod's normal dependency graph. The fixture is
# present only for this CI invocation and is deobfuscated for runClient.
cat >> build.gradle <<'EOF'

// VULKANMOD_CI_FTB_LIBRARY_RUNTIME
dependencies {
    runtimeOnly(fg.deobf('dev.ftb.mods:ftb-library-forge:2001.2.13')) { transitive = false }
    runtimeOnly(fg.deobf('dev.architectury:architectury-forge:9.0.8')) { transitive = false }
}
EOF

lvp_icd="$(find /usr/share/vulkan/icd.d -maxdepth 1 -name 'lvp_icd*.json' -print -quit)"
if [[ -z "$lvp_icd" ]]; then
  echo "Lavapipe Vulkan ICD was not installed" >&2
  exit 1
fi

export VK_ICD_FILENAMES="$lvp_icd"
export LIBGL_ALWAYS_SOFTWARE=1
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dvulkanmod.smokeTest=true -Dvulkanmod.ciFtbLibrarySmoke=true"

timeout 240s xvfb-run -a ./gradlew --init-script "$init_script" runClient --stacktrace 2>&1 | tee vulkan-smoke-ftb-library.log

grep -F "FTB Library scissor compatibility mixin smoke passed" vulkan-smoke-ftb-library.log
grep -F "Vulkan smoke test passed" vulkan-smoke-ftb-library.log
