#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

mode="${1:-}"

usage() {
  echo "Usage: $0 {startup|no-splash|gpu-indirect-shadow|post-chain|depth-post-chain|screenshot|crash-assistant|chat-heads|flywheel|create}" >&2
  exit 2
}

fixture_backup_dir="$(mktemp -d)"
restore_build_gradle=0
restore_fml_toml=0
restore_ci_mods=0
restore_vk_layer_settings=0
vk_layer_settings_existed=0

snapshot_build_gradle() {
  if [[ "$restore_build_gradle" -eq 0 ]]; then
    cp -a build.gradle "$fixture_backup_dir/build.gradle"
    restore_build_gradle=1
  fi
}

snapshot_fml_toml() {
  if [[ "$restore_fml_toml" -eq 0 ]]; then
    test -f run/config/fml.toml
    cp -a run/config/fml.toml "$fixture_backup_dir/fml.toml"
    restore_fml_toml=1
  fi
}

snapshot_ci_mods() {
  if [[ "$restore_ci_mods" -ne 0 ]]; then
    return
  fi

  mkdir -p "$fixture_backup_dir/mods"
  mkdir -p run/mods
  shopt -s nullglob
  local jar
  for jar in run/mods/CrashAssistant-*.jar run/mods/chat_heads-*.jar run/mods/flywheel-*.jar run/mods/create-*.jar; do
    cp -a "$jar" "$fixture_backup_dir/mods/"
  done
  shopt -u nullglob
  restore_ci_mods=1
}

clear_ci_mods() {
  snapshot_ci_mods
  rm -f run/mods/CrashAssistant-*.jar run/mods/chat_heads-*.jar run/mods/flywheel-*.jar
}

snapshot_vk_layer_settings() {
  if [[ "$restore_vk_layer_settings" -ne 0 ]]; then
    return
  fi

  mkdir -p run
  if [[ -f run/vk_layer_settings.txt ]]; then
    cp -a run/vk_layer_settings.txt "$fixture_backup_dir/vk_layer_settings.txt"
    vk_layer_settings_existed=1
  fi
  restore_vk_layer_settings=1
}

restore_fixture_state() {
  local status=$?
  set +e

  if [[ "$restore_vk_layer_settings" -ne 0 ]]; then
    mkdir -p run
    if [[ "$vk_layer_settings_existed" -ne 0 ]]; then
      cp -a "$fixture_backup_dir/vk_layer_settings.txt" run/vk_layer_settings.txt
    else
      rm -f run/vk_layer_settings.txt
    fi
  fi

  if [[ "$restore_build_gradle" -ne 0 ]]; then
    cp -a "$fixture_backup_dir/build.gradle" build.gradle
  fi

  if [[ "$restore_fml_toml" -ne 0 ]]; then
    mkdir -p run/config
    cp -a "$fixture_backup_dir/fml.toml" run/config/fml.toml
  fi

  if [[ "$restore_ci_mods" -ne 0 ]]; then
    mkdir -p run/mods
    clear_ci_mods
    shopt -s nullglob
    local jar
    for jar in "$fixture_backup_dir"/mods/*.jar; do
      cp -a "$jar" run/mods/
    done
    shopt -u nullglob
  fi

  rm -rf "$fixture_backup_dir"
  exit "$status"
}

trap restore_fixture_state EXIT

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
    grep -F "Vertex/Forge consumer contract smoke passed" vulkan-smoke.log
    grep -F "Forge shader registration/reload contract smoke passed" vulkan-smoke.log
    grep -F "Forge RenderTarget stencil capability smoke passed" vulkan-smoke.log
    grep -F "Terrain region cache smoke test passed" vulkan-smoke.log
    grep -F "Terrain region batching: enabled" vulkan-smoke.log
    grep -F "Terrain voxel lifecycle smoke passed (capture=false)" vulkan-smoke.log
    grep -F "Terrain publication drain smoke passed" vulkan-smoke.log
    ;;

  no-splash)
    snapshot_fml_toml
    if grep -Fq 'earlyWindowControl = true' run/config/fml.toml; then
      sed -i 's/^earlyWindowControl = true$/earlyWindowControl = false/' run/config/fml.toml
    fi
    grep -F 'earlyWindowControl = false' run/config/fml.toml

    run_client "-Dvulkanmod.smokeTest=true -Dvulkanmod.experimentalSectionVoxels=true" vulkan-smoke-no-splash.log
    grep -F "ImmediateWindowProvider not loading because splash screen is disabled" vulkan-smoke-no-splash.log
    grep -F "Created Vulkan NO_API window without Forge early splash context" vulkan-smoke-no-splash.log
    grep -F "Vulkan smoke test passed" vulkan-smoke-no-splash.log
    grep -F "Liquid vertex alpha/UV smoke test passed" vulkan-smoke-no-splash.log
    grep -F "Vertex/Forge consumer contract smoke passed" vulkan-smoke-no-splash.log
    grep -F "Forge shader registration/reload contract smoke passed" vulkan-smoke-no-splash.log
    grep -F "Forge RenderTarget stencil capability smoke passed" vulkan-smoke-no-splash.log
    grep -F "Terrain region cache smoke test passed" vulkan-smoke-no-splash.log
    grep -F "Terrain voxel lifecycle smoke passed (capture=true)" vulkan-smoke-no-splash.log
    grep -F "Terrain publication drain smoke passed" vulkan-smoke-no-splash.log
    ;;

  gpu-indirect-shadow)
    clear_ci_mods
    snapshot_vk_layer_settings
    mkdir -p run
    export VK_LAYER_SETTINGS_PATH="$repo_root/run"
    echo 'khronos_validation.enables = VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT' > run/vk_layer_settings.txt
    run_client "-Dvulkanmod.smokeTest=true -Dvulkanmod.smokeExitAtConstructor=true -Dvulkanmod.experimentalGpuIndirectCommands=true -Dvulkanmod.ciGpuIndirectShadowSmoke=true -Dvulkanmod.ciGpuLiveSelectionDiagnosticSmoke=true -Dvulkanmod.validation=true" vulkan-gpu-indirect-shadow-smoke.log
    grep -F "VULKANMOD_GPU_INDIRECT_SHADOW_READY" vulkan-gpu-indirect-shadow-smoke.log
    grep -F "VULKANMOD_GPU_INDIRECT_SHADOW_SMOKE_OK" vulkan-gpu-indirect-shadow-smoke.log
    grep -F "VULKANMOD_GPU_LIVE_DIAGNOSTIC_SMOKE_OK" vulkan-gpu-indirect-shadow-smoke.log
    grep -F "Vulkan smoke test passed" vulkan-gpu-indirect-shadow-smoke.log
    if grep -E 'Validation Error|SYNC-HAZARD' vulkan-gpu-indirect-shadow-smoke.log; then
      echo "GPU indirect shadow smoke produced invalid Vulkan" >&2
      exit 1
    fi
    rm -f run/vk_layer_settings.txt
    ;;

  post-chain)
    clear_ci_mods
    run_client "-Dvulkanmod.ciPostChainSmoke=true" vulkan-post-chain-smoke.log
    grep -F "Vulkan vanilla post-chain execution smoke passed" vulkan-post-chain-smoke.log
    ;;

  depth-post-chain)
    clear_ci_mods
    snapshot_vk_layer_settings
    mkdir -p run
    # The smoke must reject invalid Vulkan that happens not to crash Lavapipe.
    # Layer settings enable synchronization validation on Ubuntu's layer version.
    export VK_LAYER_SETTINGS_PATH="$repo_root/run"
    echo 'khronos_validation.enables = VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT' > run/vk_layer_settings.txt
    run_client "-Dvulkanmod.ciDepthPostChainSmoke=true -Dvulkanmod.validation=true" vulkan-depth-post-chain-smoke.log
    grep -F "Vulkan vanilla depth post-chain execution smoke passed" vulkan-depth-post-chain-smoke.log
    grep -F "Depth inputs initialized and copied; four PostChain processes submitted in two frames" vulkan-depth-post-chain-smoke.log
    if grep -E 'Validation Error|SYNC-HAZARD|Effect sampler .*white fallback' vulkan-depth-post-chain-smoke.log; then
      echo "Depth post-chain smoke produced invalid Vulkan or an unbound sampler" >&2
      exit 1
    fi
    rm -f run/vk_layer_settings.txt
    ;;

  screenshot)
    clear_ci_mods
    snapshot_vk_layer_settings
    mkdir -p run
    export VK_LAYER_SETTINGS_PATH="$repo_root/run"
    echo 'khronos_validation.enables = VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT' > run/vk_layer_settings.txt
    run_client "-Dvulkanmod.ciScreenshotSmoke=true -Dvulkanmod.validation=true" vulkan-screenshot-smoke.log
    grep -F "Vulkan screenshot readback smoke passed" vulkan-screenshot-smoke.log
    grep -F "Packed texture upload Vulkan smoke passed" vulkan-screenshot-smoke.log
    if grep -E 'Validation Error|SYNC-HAZARD' vulkan-screenshot-smoke.log; then
      echo "Screenshot readback smoke produced invalid Vulkan" >&2
      exit 1
    fi
    rm -f run/vk_layer_settings.txt
    ;;

  crash-assistant)
    mkdir -p run/mods
    clear_ci_mods
    curl -fL --retry 3 \
      'https://cdn.modrinth.com/data/ix1qq8Ux/versions/mcLRynoF/CrashAssistant-forge-1.19.2-1.20.1-1.9.7.jar' \
      -o 'run/mods/CrashAssistant-forge-1.19.2-1.20.1-1.9.7.jar'

    # Crash Assistant ships production/SRG mixin bytecode inside a nested JAR.
    # ForgeGradle cannot deobfuscate that nested payload for Mojmap runClient, so
    # retain the constructor-boundary exit for this compatibility-only fixture.
    # The ordinary startup fixtures continue through baked-model publication.
    run_client "-Dvulkanmod.smokeTest=true -Dvulkanmod.smokeExitAtConstructor=true" vulkan-smoke-crash-assistant.log
    grep -F "Vulkan smoke test passed" vulkan-smoke-crash-assistant.log
    grep -F "CrashAssistant-forge-1.19.2-1.20.1-1.9.7.jar" vulkan-smoke-crash-assistant.log
    if grep -F "No context is current or a function that is not available in the current context was called" vulkan-smoke-crash-assistant.log; then
      echo "Crash Assistant attempted an OpenGL probe without a current context" >&2
      exit 1
    fi
    ;;

  chat-heads)
    mkdir -p run/mods
    clear_ci_mods
    snapshot_build_gradle

    # runClient uses Mojmap-named development classes while Chat Heads is a
    # production/SRG mod. Feed the exact 0.13.18 Forge artifact through
    # ForgeGradle's deobfuscation layer; copying the raw JAR into run/mods makes
    # its @Shadow SRG names fail before our compatibility target is exercised.
    if ! grep -Fq '// VULKANMOD_CI_CHAT_HEADS_RUNTIME' build.gradle; then
      cat >> build.gradle <<'EOF'

// VULKANMOD_CI_CHAT_HEADS_RUNTIME
repositories {
    maven { url = 'https://api.modrinth.com/maven' }
}
dependencies {
    runtimeOnly fg.deobf('maven.modrinth:Wb5oqrBJ:45EJNtBe')
}
EOF
    fi

    run_client "-Dvulkanmod.smokeTest=true" vulkan-smoke-chat-heads.log
    grep -F "Vulkan smoke test passed" vulkan-smoke-chat-heads.log
    grep -F "chat_heads" vulkan-smoke-chat-heads.log
    if grep -E 'chat_heads\.mixins\.json:ChatComponentMixin.*FAILED|InvalidInjectionException.*chatheads\$captureGuiMessage' vulkan-smoke-chat-heads.log; then
      echo "Chat Heads mixin compatibility regression detected" >&2
      exit 1
    fi
    ;;

  flywheel)
    clear_ci_mods
    snapshot_build_gradle

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

  create)
    clear_ci_mods
    snapshot_build_gradle

    # Exercise the exact Create 0.5.1.j artifact from the user's modpack family.
    # Loading StencilElement forces Sponge Mixin to validate the raw-LWJGL
    # stencil redirects against Create's real bytecode.
    if ! grep -Fq '// VULKANMOD_CI_CREATE_RUNTIME' build.gradle; then
      cat >> build.gradle <<'EOF'

// VULKANMOD_CI_CREATE_RUNTIME
repositories {
    maven { url = 'https://api.modrinth.com/maven' }
}
dependencies {
    runtimeOnly fg.deobf('maven.modrinth:LNytGWDc:6R069CcK')
}
EOF
    fi

    run_client "-Dvulkanmod.smokeTest=true -Dvulkanmod.ciCreateStencilSmoke=true" vulkan-smoke-create.log
    grep -F "Vulkan smoke test passed" vulkan-smoke-create.log
    grep -F "Forge RenderTarget stencil capability smoke passed" vulkan-smoke-create.log
    grep -F "Create 0.5.1.j stencil compatibility mixin target loaded" vulkan-smoke-create.log
    if grep -F "No context is current or a function that is not available in the current context was called" vulkan-smoke-create.log; then
      echo "Create attempted an unbridged OpenGL call without a current context" >&2
      exit 1
    fi
    ;;

  *)
    usage
    ;;
esac
