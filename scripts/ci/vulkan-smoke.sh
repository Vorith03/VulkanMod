#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mode="${1:-}"

usage() {
  echo "Usage: $0 {startup|no-splash|post-chain|crash-assistant|flywheel}" >&2
  exit 2
}

configure_lavapipe() {
  local lvp_icd
  lvp_icd="$(find /usr/share/vulkan/icd.d -maxdepth 1 -name 'lvp_icd*.json' -print -quit)"
  if [[ -z "$lvp_icd" ]]; then
    echo "Lavapipe Vulkan ICD was not installed" >&2
    exit 1
  fi

  echo "Using software Vulkan ICD: $lvp_icd"
  export VK_ICD_FILENAMES="$lvp_icd"
  export LIBGL_ALWAYS_SOFTWARE=1
}

run_client() {
  local property="$1"
  local log_file="$2"

  configure_lavapipe
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} ${property}"
  timeout 240s xvfb-run -a ./gradlew runClient --stacktrace 2>&1 | tee "$log_file"
}

case "$mode" in
  startup)
    run_client "-Dvulkanmod.smokeTest=true" vulkan-smoke.log
    grep -F "Vulkan smoke test passed" vulkan-smoke.log
    grep -F "Liquid vertex alpha/UV smoke test passed" vulkan-smoke.log
    grep -F "Terrain region cache smoke test passed" vulkan-smoke.log
    grep -F "Terrain region batching: enabled" vulkan-smoke.log
    ;;

  no-splash)
    test -f run/config/fml.toml
    if grep -Fq 'earlyWindowControl = true' run/config/fml.toml; then
      sed -i 's/^earlyWindowControl = true$/earlyWindowControl = false/' run/config/fml.toml
    fi
    grep -F 'earlyWindowControl = false' run/config/fml.toml

    run_client "-Dvulkanmod.smokeTest=true" vulkan-smoke-no-splash.log
    grep -F "ImmediateWindowProvider not loading because splash screen is disabled" vulkan-smoke-no-splash.log
    grep -F "Created Vulkan NO_API window without Forge early splash context" vulkan-smoke-no-splash.log
    grep -F "Vulkan smoke test passed" vulkan-smoke-no-splash.log
    grep -F "Liquid vertex alpha/UV smoke test passed" vulkan-smoke-no-splash.log
    grep -F "Terrain region cache smoke test passed" vulkan-smoke-no-splash.log
    ;;

  post-chain)
    rm -f run/mods/CrashAssistant-*.jar run/mods/flywheel-*.jar
    run_client "-Dvulkanmod.ciPostChainSmoke=true" vulkan-post-chain-smoke.log
    grep -F "Vulkan vanilla post-chain construction smoke passed" vulkan-post-chain-smoke.log
    ;;

  crash-assistant)
    mkdir -p run/mods
    rm -f run/mods/CrashAssistant-*.jar run/mods/flywheel-*.jar
    curl -fL --retry 3 \
      'https://cdn.modrinth.com/data/ix1qq8Ux/versions/mcLRynoF/CrashAssistant-forge-1.19.2-1.20.1-1.9.7.jar' \
      -o 'run/mods/CrashAssistant-forge-1.19.2-1.20.1-1.9.7.jar'

    run_client "-Dvulkanmod.smokeTest=true" vulkan-smoke-crash-assistant.log
    grep -F "Vulkan smoke test passed" vulkan-smoke-crash-assistant.log
    grep -F "CrashAssistant-forge-1.19.2-1.20.1-1.9.7.jar" vulkan-smoke-crash-assistant.log
    if grep -F "No context is current or a function that is not available in the current context was called" vulkan-smoke-crash-assistant.log; then
      echo "Crash Assistant attempted an OpenGL probe without a current context" >&2
      exit 1
    fi
    ;;

  flywheel)
    rm -f run/mods/CrashAssistant-*.jar run/mods/flywheel-*.jar

    # runClient uses Mojmap-named development classes, while the published
    # Flywheel JAR is reobfuscated. Add its dev-runtime dependency only for this
    # CI invocation, and mark the block so repeated local runs do not append it.
    if ! grep -Fq '// VULKANMOD_CI_FLYWHEEL_RUNTIME' build.gradle; then
      cat >> build.gradle <<'EOF'

// VULKANMOD_CI_FLYWHEEL_RUNTIME
repositories {
    maven { url = 'https://modmaven.dev/' }
}
dependencies {
    runtimeOnly fg.deobf('com.jozufozu.flywheel:flywheel-forge-1.20.1:0.6.11-13')
}
EOF
    fi

    run_client "-Dvulkanmod.smokeTest=true" vulkan-smoke-flywheel.log
    grep -F "Vulkan smoke test passed" vulkan-smoke-flywheel.log
    grep -F "Flywheel 0.6 compatibility smoke test passed" vulkan-smoke-flywheel.log
    ;;

  *)
    usage
    ;;
esac
