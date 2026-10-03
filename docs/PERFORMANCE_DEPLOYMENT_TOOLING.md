# Separate server and pregeneration tooling

`scripts/performance/deployment.py` is an offline Python 3.11+ CLI for Linux. It does not launch a server, change an existing world, accept an EULA, install mods or send console commands. It produces reviewable deployment inputs. Actual offloading and pregeneration remain pending until the user provides a matching server distribution, deployment host and chosen world/region.

## Audit client/server parity

Use the actual Prism game directory (`.minecraft`, containing `mods` and `config`) and an unpacked matching server distribution with **Minecraft 1.20.1 / Forge 47.3.0 already installed**. Make an explicit side-exception policy from `scripts/performance/parity-policy.example.json`; its two client-only IDs and optional server-only Chunky ID are examples, not an inferred Create Chronicles mod list.

```bash
python3 scripts/performance/deployment.py audit \
  --client /path/to/prism/.minecraft \
  --server /path/to/matching-installed-server \
  --policy /path/to/parity-policy.json \
  --out /path/to/new-parity-report.json
```

The audit checks top-level Forge mod IDs, exact shared JAR hashes, duplicate IDs, explicit client/server exceptions, and config/defaultconfigs/KubeJS/scripts/global-pack hashes. Opaque library JARs are classified as `file:filename.jar` rather than guessing sidedness. Hashing a top-level JAR covers its embedded dependencies, but the audit does not extract JarJar dependency IDs or resolve dependency/version ranges. Forge's `displayTest` is a connection-compatibility setting and is not evidence that a mod can run on a dedicated server; nothing is auto-removed based on it. Source: [Forge sides documentation](https://docs.minecraftforge.net/en/1.20.x/concepts/sides/).

Shared hash differences block staging even when declared versions match. Explicitly resolve differences in source distributions and rerun the audit; policy exceptions only classify one-sided entries, not arbitrary shared-code differences. The audit also verifies the selected Forge argument file, its Minecraft/Forge version values and run.sh selection. It emits file hashes and metadata, not config contents. A ready report is permission to stage a copy, not proof of successful login, registry/datapack parity or gameplay behavior.

## Stage a new deployment

```bash
python3 scripts/performance/deployment.py prepare \
  --client /path/to/prism/.minecraft \
  --server /path/to/matching-installed-server \
  --policy /path/to/parity-policy.json \
  --world /path/to/prism/.minecraft/saves/ChosenWorld \
  --heap-gib 6 \
  --out /path/to/new-server-copy
```

The command requires a passing parity audit, separate input/output trees and a new output path. It copies the installed server's libraries, launch files, mods and shared data directories. With `--world`, it obtains Minecraft's Linux POSIX session record lock before copying the world, retaining its serverconfig and datapacks. An open world is rejected; the source is retained and its level.dat hash is recorded. The copied world omits the stale session lock, and its staged server.properties selects `level-name=world`. Omit `--world` to prepare an empty deployment.

The staged user_jvm_args retains non-heap options and replaces Xms/Xmx with 1 GiB initial heap and the explicitly selected maximum. The report records the source argument-file hash. Choose host-specific memory, simulation distance and server configuration using actual host/server timing evidence; this tool does not invent a faster simulation profile. No EULA file is copied/accepted and no port/firewall/RCON/security settings are changed. Inspect the output, complete the ordinary Forge server setup, and run `bash launch-vulkanmod-server.sh` when ready; the wrapper requires Java 17 and delegates to the provided Forge run.sh. Linux session-lock checking does not implement a Windows migration workflow.

Before adopting separate hosting, verify a real client join, registry/recipe/datapack equivalence, dimensions, representative Create machines and equal simulation settings. Compare integrated versus remote server tick/frame tails and network latency on the same route. Client animations, meshing and rendering remain local. Existing worlds stay available for rollback.

## Prepare a bounded pregeneration job

```bash
python3 scripts/performance/deployment.py pregen \
  --dimension minecraft:overworld --center 0 0 \
  --radius 2048 --shape circle \
  --out /path/to/new-pregen-plan
```

The output contains console commands, in-game slash commands, an explicit dimension/center/radius/shape plan and a conservative bounding-square chunk-count upper bound. Center and radius are supplied deliberately; the tool accepts radius 1–100000 blocks and regions within ordinary world-coordinate limits. Dimension IDs and shapes are validated to prevent extra commands. It emits selection inspection before start, plus progress/pause/continue controls. It never emits trim/delete commands or executes generation.

Install a qualified **Forge 1.20.1** Chunky release in the chosen matching deployment, verify its commands, inspect `chunky selection`, and execute `chunky start` only for the reviewed region on a backed-up world or closed-world copy. Every dimension is a separate explicit plan; do not implicitly scale overworld coordinates for the Nether. Commands follow [Chunky's official guide](https://github.com/pop4959/Chunky/wiki/Commands) and Forge uses dimension resource IDs in its command registration. Preparation CPU time and disk usage depend on the actual generator/modpack and must be measured. Compare the same traversal before/after generation with equal client/server settings; pregeneration removes future generation work, not client chunk meshing or recurring texture ticks.

## Verification

`python3 scripts/ci/deployment-contract.py` uses synthetic Forge JAR/config fixtures and a separate process holding a real POSIX record lock. It checks shared-byte mismatch, config mismatch, wrong Forge selection, duplicate IDs, symlinks, live-world rejection, source preservation, copied world data, no overwrite, JVM option retention, Java-version branches and bounded command generation. These are tooling contracts; no real Create Chronicles server or pregeneration job has run here.
