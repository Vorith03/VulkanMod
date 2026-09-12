# Completed terrain result publication audit — 2026-09-12

Inspected live branch `forge-1.20.1` at `f8cd62c`, runtime source `387afc0`,
latest CI #353 green. This is a separate localized publication change, not a
continuation of GPU meshing/terrain format work.

## Path and findings

1. `ChunkTask.BuildTask.doTask` executes compile directly on a TaskDispatcher worker.
   It creates compact native UploadBuffers, prepares a CompiledSection, and directly
   enqueues a Runnable through `scheduleSectionUpdate`. Its returned CompletableFuture
   is already completed; there is no executor/mailbox/future continuation hop.
2. `TaskDispatcher.toUpload` is a LinkedBlockingDeque used as a FIFO queue. Workers
   stop taking new work when completed backlog reaches two results per worker.
   Active workers may finish after that threshold; this existing bound is unchanged.
3. `LevelRendererMixin.compileChunks` calls `WorldRenderer.compileSections`, which
   calls `uploadAllPendingUploads` at the normal compile/upload point in rendering.
   This is the sole normal production drain call site, so results arriving after it
   ordinarily wait for the next frame's call. The supplied ~30 ms handoff sample
   alone cannot distinguish frame cadence from time spent publishing.
4. `uploadAllPendingUploads` already polls **until empty**, including results that
   arrive while it is draining. There is no fixed per-frame result count, no enforced
   one-result-per-frame rule, and no existing drain time budget.
5. Each Runnable rejects cancellation before touching section state, uploads all
   terrain layers or resets absent ones, then invokes the section publication callback.
   This updates compiled metadata/global block entities. Finally, native handoff
   buffers are released and removed from pending ownership.
6. Layer/section uploads record into AreaUploadManager's shared current-frame helper.
   The dispatcher submits once after the drain, rather than one submit per result.
   Buffer growth retains its existing exceptional synchronous path. No new Vulkan
   queue, wait, flush point, memory barrier or resource lifetime rule is introduced.
7. Queue polling and size checks use the deque's normal lock; expensive result work
   runs outside the dispatcher monitor. Region updates retain their required locking.
   No measured lock-contention evidence justifies replacing these mechanisms.

## Small source change

Previously workers parked by completed-result backpressure were notified only after
**all** publication callbacks finished. Once a result has finished (including its
native-buffer cleanup) and queue depth is below the unchanged threshold, that worker
sleep is unnecessary while later callbacks are processed.

The drain now checks for this condition and notifies waiters **at most once early
per drain**, under the same monitor used by their wait loop. The existing final
notification is retained to cover refills/later waiters. FIFO order, cancellation,
result cleanup, priority scheduling, worker count, queue capacity policy and one
normal GPU submission after draining are preserved.

This is an opportunity to overlap resumed worker builds with the remainder of the
publication pass. It is relevant only when publication backpressure has actually
parked workers. It is not evidence that the user's ~30 ms handoff latency will fall,
especially in a capture with all workers active and no publication waiters.

A new time budget or extra drain location was deliberately not added: the live code
already drains multiple results and batches uploads, and there is no evidence of
long drain passes requiring deferral. Adding a budget would postpone existing ready
results; adding another rendering-phase flush would require a wider ordering audit.
Likewise, bulk-deque extraction would hide retained native results from the existing
queue-size backpressure unless ownership accounting changed. Those are outside this
low-risk slice. Early wakeups can allow more arrivals during a drain; retain the new
telemetry and investigate a time budget only if measured drain/frame work warrants it.

## Telemetry and RX comparison

The existing successful-build `queue/build/handoff` counters keep their meaning.
Handoff measures from scheduleSectionUpdate entry through completion of upload
recording and the publication callback; it does not measure GPU completion.

New `Terrain publish: ms wait/work ... | early wakes N` splits that same successful
build sample into:

- wait: scheduleSectionUpdate entry -> beginning of accepted publication work;
- work: accepted publication work -> callback completion;
- early wakes: number of drain passes issuing the new early notification.

Rejected/cancelled results do not enter successful-build latency averages. Reset
uses the existing batch/world diagnostic reset path. These are cumulative averages,
not instantaneous timings or percentiles. Translucent-sort results share the drain
but remain outside the build-latency denominator, as before.

Compare build #353 against the new artifact on the same RX 6900 XT world/route,
settings, mod/resource packs and capture flag. Record queue/build/handoff, wait/work,
publication waiters, early wakes and frame times. If early wakes stays zero, this
optimization has not been exercised; do not attribute a speedup to it.

## Regression coverage

The startup smoke uses the actual TaskDispatcher worker and completed-result queue.
It fills the backlog before starting workers, proves a queued sentinel task remains
blocked, then drains cancelled results and a final accepted publication callback.
That final callback requires the worker to resume **before the drain ends**. The old
end-only notification cannot satisfy that condition. The probe also checks cancelled
callbacks do not run, one early wake is counted, and a later empty drain returns false.

Both startup modes require the marker `Terrain publication drain smoke passed`.
Existing voxel lifecycle, region uploads, post-effects, readback and mod compatibility
smokes remain in place. Full build and CI results are recorded after verification.
