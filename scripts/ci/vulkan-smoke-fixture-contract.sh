#!/usr/bin/env bash
set -euo pipefail

script="scripts/ci/vulkan-smoke.sh"
compat_script="scripts/ci/create-chronicles-compat-smoke.sh"
bash -n "$script"
bash -n "$compat_script"

require_literal() {
  local needle="$1"
  if ! grep -Fq "$needle" "$script"; then
    echo "Missing smoke-fixture contract: $needle" >&2
    exit 1
  fi
}

require_compat_literal() {
  local needle="$1"
  if ! grep -Fq "$needle" "$compat_script"; then
    echo "Missing combined compatibility-fixture contract: $needle" >&2
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

require_compat_literal '// VULKANMOD_CI_CREATE_CHRONICLES_COMPAT_RUNTIME'
require_compat_literal 'dev.ftb.mods:ftb-library-forge:2001.2.13'
require_compat_literal 'curse.maven:pick-up-notifier-351441:4613538'
require_compat_literal 'curse.maven:immersive-portals-for-forge-355440:6368524'
require_compat_literal 'curse.maven:distant-horizons-508933:8389142'
require_compat_literal 'curse.maven:entityculling-448233:5968677'
require_compat_literal 'vulkanmod.ciEntityCullingSmoke=true'
require_compat_literal '"tickCulling":false'
require_compat_literal 'cp "$entity_culling_backup" run/config/entityculling.json'
require_compat_literal 'maven.modrinth:Wb5oqrBJ:45EJNtBe'
require_compat_literal 'com.jozufozu.flywheel:flywheel-forge-1.20.1:0.6.11-13'
require_compat_literal 'maven.modrinth:LNytGWDc:6R069CcK'
require_compat_literal 'vulkanmod.ciFtbLibrarySmoke=true'
require_compat_literal 'vulkanmod.ciPickupNotifierSmoke=true'
require_compat_literal 'vulkanmod.ciImmersivePortalsSmoke=true'
require_compat_literal 'vulkanmod.ciDistantHorizonsSmoke=true'
require_compat_literal 'vulkanmod.ciCreateStencilSmoke=true'
require_compat_literal 'Immersive Portals dispatcher rewrite selected only the terrain-override context'
require_compat_literal 'Vulkan smoke test passed'

echo "Generic and combined compatibility smoke fixture contracts passed"
