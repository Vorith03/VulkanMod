#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

# Fast pre-push validation for agents with an executable checkout. This checks
# Java/Mixin compilation, processed resources, test compilation, and the
# lightweight terrain batch layout regression without building/running Minecraft.
./gradlew classes testRegionBatchLayout --stacktrace "$@"
