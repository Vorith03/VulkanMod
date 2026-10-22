# VulkanMod Forge1.20.1 — initial terrain appearance mission OPEN

## October22 — page repair qualified; bounded memory trace final qualification pending

Follow AGENTS.md §3A. The page-repair milestone returns native input pages
when their last allocation has crossed the existing frame fence. The previous
store reproduced one-page cross-region starvation after all slices had retired:
budget524288/second region rejected. Same production-store harness after correction:
budget0/second region admitted/one destruction. No new allocator, foreign scavenger,
quota increase, gameplay wait or output/visibility/default changes. Pending/live
inputs retain pages, stable live indices survive holes, close skips retired holes.
MemoryManager's existing full BufferInfo cleanup/accounting primitive is called
only from the already completed fence callback. Isolated later rebuilds may allocate
pages again; owner pacing/churn must be measured, not presumed improved.

Java17 actual Forge/LWJGL focused compilation and all26 offline contracts passed.
The new production-store contract exercises128 cross-region pairs at ONE page,
pre-fence pressure, pending/stale protection, interior index reuse, close races and
exact260 destructions. Native sparse fixture now requires different-region actual
bytes/compute, last-slice capacity return and pending/live protection. Full1101 /39830419833 at7027aeeabf61a429a902f41773b0aabbbe84bc21 SUCCESS, including
native/world/portal pixel/bypass/compatibility gates. The follow-up bounded page
budget event/entry-snapshot trace still needs exact final executable qualification.
Do not promote an intermediate JAR or ask for its owner benchmark.

## Qualified prior components

Full1100 /39827354426 at e82d00ae2be357b3530a35862cbaa8127a062508 SUCCESS.
That run qualifies generation-aware production geometry consumption, direct/
translucent observation and late census. Previous full1098 /39825029683 also passed;
its logs12446991949 independently CRC/SHA checked,18 exact main/portal pixel pairs
recomputed,0 validation/sync errors and0 unresolved geometry generations. 1099
superseded/CANCELLED (plain glass is cutout; actual translucent fixture uses stained
glass). Full1094/1097 qualified the first one-shot CPU snapshot + input slice repair.
All original texture/particle repairs, REPLACE/APPEND, portals, complete CPU fallback
and old rendering oracles remain. PR12 is untouched. Experiments stay OFF by default.

The observer records numeric geometry owners at command recording, verifies actual
indirect words after existing frame fences, and confirms CPU direct/legacy commands
through that same fence domain. Stale geometry is attributed to its OWN generation.
At55s it re-observes current production sources for a late reference census;60s
capture bounds,128 requests/4MiB/frame,32768 requests/4096 source keys remain.
Logical camera movement rejects a trial. Entry eye/FOV interpolation (including an
observed non-finite first projection) is separately reported; command progress is
credited only through the exact finite reference view, WITHOUT resetting entry.
The paired fixture holds its initial scene through61s before all original9 phases.

scripts/ci/terrain-appearance.py freezes a separately checked CPU nonempty reference,
keeps unsupported CPU geometry and missing members, requires APPEND halves, rejects
unknown/stale inputs and reports50/90/99/100 recording/fence bounds plus holes.
Offline analysis now groups references linearly:32768 synthetic members processed
in0.44s locally. Tool IDs/conditions are explicit; never fabricate save/mod digests.

## Remaining gates and next action

Finish the bounded budget-trace follow-up's full integration, inspect exact JAR/log
bytes and native/loaded-world census output, then finish one owner package in
TERRAIN_INITIAL_OWNER_TEST.md and update final evidence/rolling prompt. The owner
check keeps2552x1374,D32/S12,VSyncOFF,cap260 and the existing saved camera; fresh
process,65s fixed view, NEW latest/performance logs and visual entry evidence.
Separate matched speed comparisons use OWNER_TERRAIN_COMPARE.jvmargs (tracing FALSE
in BOTH), same immutable saves/settings and known cache strata. Actual pre-staging
1092 JAR12400315181 was downloaded/CRC/SHA verified; exact identity is in the package.
No private owner logs or packs are committed.

The complete mission remains OPEN: no matched RX/D32 appearance/pacing/memory result,
true all-required-input arrival oracle or first usable/presented-frame witness.
REGION_SNAPSHOT_READY is a later CPU-resolved milestone; lightOnInSection plus an
empty current light queue cannot prove later network packets have arrived or cover
arbitrary Forge callbacks. Keep those unknowns explicit. A command fence is not
presentation. Do not invent target success,50/90/99 pixel coverage or matched benefit.
All3 workstreams were inspected; new schedulers/cullers, budget increases and model
expansion lack new owner critical-path evidence. The second page-retention repair
has a direct bounded-capacity reproduction and is not a speculative quota increase.
