#!/usr/bin/env bash
set -euo pipefail

script="scripts/ci/vulkan-smoke.sh"
bash -n "$script"

require_literal() {
  local needle="$1"
  if ! grep -Fq "$needle" "$script"; then
    echo "Missing smoke-fixture contract: $needle" >&2
    exit 1
  fi
}

case_body() {
  local mode="$1"
  sed -n "/^  ${mode})$/,/^    ;;/p" "$script"
}

require_in_mode() {
  local mode="$1"
  local needle="$2"
  if ! case_body "$mode" | grep -Fq "$needle"; then
    echo "Smoke mode '$mode' is missing fixture guard '$needle'" >&2
    exit 1
  fi
}

require_literal 'trap restore_fixture_state EXIT'
require_literal 'cp -a "$fixture_backup_dir/build.gradle" build.gradle'
require_literal 'cp -a "$fixture_backup_dir/fml.toml" run/config/fml.toml'
require_literal 'for jar in "$fixture_backup_dir"/mods/*.jar; do'
require_literal 'cp -a run/vk_layer_settings.txt "$fixture_backup_dir/vk_layer_settings.txt"'
require_literal 'cp -a "$fixture_backup_dir/vk_layer_settings.txt" run/vk_layer_settings.txt'

require_in_mode no-splash snapshot_fml_toml
require_in_mode chat-heads snapshot_build_gradle
require_in_mode flywheel snapshot_build_gradle
require_in_mode create snapshot_build_gradle
require_in_mode create 'maven.modrinth:LNytGWDc:6R069CcK'
require_in_mode create 'vulkanmod.ciCreateStencilSmoke=true'

for mode in gpu-indirect-shadow depth-post-chain screenshot; do
  require_in_mode "$mode" snapshot_vk_layer_settings
done

for mode in gpu-indirect-shadow post-chain depth-post-chain screenshot crash-assistant chat-heads flywheel create; do
  require_in_mode "$mode" clear_ci_mods
done

echo "Generic Vulkan smoke fixture isolation contract passed"
