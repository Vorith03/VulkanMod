# VulkanMod 1.20.1 Renderer Performance Audit

## Scope

This audit looks for code-level performance bugs in the 1.20.1 renderer base used by the Forge port. The emphasis is on accidental serialization, synchronization mistakes, memory-selection problems, unnecessary queue submissions, native leaks, and hot-path allocation behavior—not generic optimization ideas.

The RX 6900 XT / RADV minimal-Forge gameplay baseline and water fix are now user-confirmed. On 2026-09-06 the user explicitly requested Nvidium-like terrain work as the next priority, ahead of full Create Chronicles compatibility. The older findings below remain audit hypotheses until individually tested; this session targets terrain submission first.

## Severity summary

### P0 — likely major performance/correctness impact

1. Render thread waits all pending auxiliary fences before every graphics submit.
2. Swapchain image is acquired after command recording and the renderer records against `currentFrame` rather than the acquired image index.
3. Texture/image transitions can submit tiny standalone command buffers which are then synchronously drained by the frame path.

### P1 — significant performance/correctness hazards

4. Dedicated transfer queue usage lacks explicit queue-family ownership transfers for exclusive resources.
5. Memory type selection uses exact flag equality and can choose a worse fallback memory type.
6. Mappable-buffer creation leaks native `PointerBuffer` allocations.
7. Synchronization waits fences one at a time instead of using one multi-fence wait.

### P2 — hot-path CPU/GC/native pressure

8. Pipeline state creation allocates several Java objects on every pipeline bind.
9. Descriptor update path allocates Java arrays on texture/UBO state changes.
10. Descriptor-set pool growth replaces native set buffers without freeing the old native allocation.
11. Buffer growth policy can over-allocate aggressively and cause transient memory spikes.

## P0 findings

### 1. Main frame submit performs a CPU-side global fence drain

Current `Renderer.submitFrame()` calls:

```java
Synchronization.INSTANCE.waitFences();
```

immediately before `vkQueueSubmit(...)` for the frame.

Current `Synchronization.waitFences()` loops over every queued fence and calls `vkWaitForFences(..., UINT64_MAX)` separately for each one.

Consequences:

- asynchronous texture/buffer uploads become render-thread blocking work,
- tiny auxiliary submissions can serialize the entire frame,
- CPU cannot get ahead while the GPU completes transfer/transition work,
- chunk/texture-heavy scenes can suffer frame-time spikes,
- a modpack with dynamic textures or frequent chunk uploads is especially exposed.

This is a high-confidence performance bug, not merely a profiling hypothesis.

#### Evidence from newer upstream

Current upstream VulkanMod no longer relies solely on this pre-submit CPU drain. It tracks wait semaphores and wires them directly into the main graphics `VkSubmitInfo`, allowing the GPU to order auxiliary work against the main frame without forcing the render thread to wait first.

Backport direction: selectively port the semaphore-based dependency path rather than inventing a new synchronization model.

### 2. Swapchain image acquisition happens too late

The 1.20.1 renderer records the main render pass before `vkAcquireNextImageKHR`.

The sequence is effectively:

```text
beginFrame
  record render pass against framebuffer[currentFrame]
endFrame
  finish command buffer
  vkAcquireNextImageKHR -> imageIndex
  submit recorded command buffer
  present imageIndex
```

`DefaultMainPass`, `SwapChain.beginRenderPass`, `colorAttachmentLayout`, and `presentLayout` use `Renderer.getCurrentFrame()` to choose the swapchain image/framebuffer.

But `vkAcquireNextImageKHR` is allowed to return any available swapchain image; swapchain image index and frame-in-flight index are not the same concept.

Consequences:

- command buffers can target a different swapchain image than the image later presented,
- image layout tracking can apply to the wrong image,
- semaphore/fence reuse assumptions become fragile,
- behavior may appear fine on a driver that happens to return images in round-robin order but is not guaranteed,
- drivers may be forced into conservative synchronization or undefined behavior may surface as stutter/artifacts/crashes.

#### Evidence from newer upstream

Current upstream explicitly separates `currentFrame` and `imageIndex`, acquires the swapchain image in `beginFrame()` before the main render pass is recorded, and indexes present semaphores by the acquired image.

Backport direction: acquire first, store `currentImage`, record layouts/framebuffer/render pass against the acquired image, retain `currentFrame` only for frame-in-flight resources.

### 3. Image layout transitions create many tiny submits

`VulkanImage.readOnlyLayout()` can:

1. obtain a command buffer,
2. record one image barrier,
3. submit that command buffer immediately,
4. add its fence/command buffer to global synchronization.

`uploadSubTextureAsync()` similarly may end and submit a standalone command buffer when there is no currently batched graphics upload command buffer.

Because the main frame currently drains all these fences before frame submission, bursts of texture activity can degrade into:

```text
submit tiny barrier/upload
submit tiny barrier/upload
submit tiny barrier/upload
...
render thread waits each fence
submit actual frame
```

This is likely to be particularly harmful during resource loading, atlas updates, animated/dynamic textures, map-like mods, and modded GUIs.

Backport direction:

- batch image transitions/uploads into a per-frame upload command buffer where practical,
- use GPU-side semaphore dependencies into the main graphics submit,
- avoid one queue submission per simple layout transition.

## P1 findings

### 4. Dedicated transfer queue lacks queue-family ownership transfers

Queue discovery explicitly prefers a transfer-only queue family when available.

Buffers are created with exclusive sharing by default, and the device-local upload path copies to them on the transfer queue. The audited copy paths do not show explicit queue-family release/acquire ownership barriers around those exclusive resources before graphics use.

If transfer and graphics families differ, this violates the intended ownership model for exclusive resources.

Possible outcomes include:

- undefined behavior,
- driver-specific implicit/conservative handling,
- extra stalls,
- corruption that only appears on certain GPUs/drivers.

Backport direction, in order of simplicity:

1. If performance gain from a dedicated transfer family is unproven, initially use the graphics queue for uploads.
2. Otherwise use concurrent sharing for resources genuinely shared between transfer and graphics queues.
3. Or implement correct queue-family ownership release/acquire barriers plus semaphore dependencies.

Do not keep a dedicated transfer queue merely because Vulkan exposes one.

### 5. Memory type selection uses equality rather than bit containment

`MemoryTypes.createMemoryTypes()` contains checks like:

```java
memoryType.propertyFlags() == VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
```

and:

```java
memoryType.propertyFlags() ==
    (HOST_VISIBLE | HOST_COHERENT | HOST_CACHED)
```

This rejects a memory type when it contains all required capabilities plus additional valid flags.

That can make the renderer skip a better memory type and fall back to a less appropriate one.

For resources updated every frame (vertex buffers, UBOs, staging), memory-type choice directly affects CPU write bandwidth and cache behavior.

Backport direction:

```java
(flags & requiredFlags) == requiredFlags
```

Then score/select candidates intentionally rather than relying on exact bit patterns.

Also review `DeviceLocalMemory.createBuffer()`: it passes `VK_MEMORY_HEAP_DEVICE_LOCAL_BIT` into a parameter interpreted as memory property flags. The two constants currently share the same numeric bit in Vulkan, so this works accidentally, but the semantic mismatch should be corrected.

### 6. Persistent mapped-buffer helper leaks native pointer storage

`MemoryManager.Map()` does:

```java
PointerBuffer data = MemoryUtil.memAllocPointer(1);
vmaMapMemory(..., data);
return data;
```

Each mappable `Buffer.createBuffer()` stores that native `PointerBuffer` in `buffer.data`.

When buffers are resized or retired, the native pointer-buffer allocation itself is not freed.

This is a small leak per creation, but dynamic buffer growth and resource churn make it cumulative.

Backport direction:

- store the mapped address as a primitive `long`, or
- free the old `PointerBuffer` when the backing buffer is retired,
- define ownership clearly for VMA map/unmap lifetime.

### 7. Fence draining is N separate native waits

Current `Synchronization.waitFences()` loops:

```java
for (...) {
    vkWaitForFences(device, fences.get(i), true, UINT64_MAX);
}
```

Even when a CPU wait is genuinely necessary, this multiplies JNI/native transitions and prevents Vulkan from waiting on a set of fences in one call.

Newer upstream already changed this to one `vkWaitForFences(device, fences, true, ...)` call for fence-only cases.

Backport direction: batch unavoidable fence waits, but prioritize eliminating the per-frame CPU wait from the main submit path first.

## P2 findings

### 8. Pipeline bind allocates several Java objects

`Renderer.bindGraphicsPipeline()` calls `PipelineState.getCurrentPipelineState(...)`.

The current implementation creates fresh instances of:

- `BlendState`,
- `DepthState`,
- `ColorMask`,
- `PipelineState`.

Then `GraphicsPipeline` performs a `HashMap.computeIfAbsent` using the new state object as a key.

In a draw-call-heavy scene this creates continuous short-lived allocation pressure and hash work on the render thread.

Newer upstream converted most state to packed integers and reuses the current `PipelineState` when nothing changed, strongly suggesting this hot path was worth fixing.

Backport direction:

- encode immutable render-state dimensions into primitive fields/bitsets,
- cache/reuse the last state when unchanged,
- avoid constructing four objects for every bind.

### 9. Descriptor updates allocate Java arrays

On descriptor changes, `Pipeline.DescriptorSets.updateDescriptorSet()` creates Java arrays such as:

```java
new VkDescriptorBufferInfo.Buffer[buffers.size()]
new VkDescriptorImageInfo.Buffer[images.size()]
```

while also allocating the Vulkan structs from `MemoryStack`.

The native structs are stack-backed and cheap; the Java arrays are heap objects and can contribute to GC pressure when texture bindings change frequently.

Backport direction: use contiguous LWJGL Buffer objects directly where possible and avoid Java wrapper arrays in the hot descriptor-update path.

### 10. Descriptor set growth leaks old native `LongBuffer`

`createDescriptorSets()` assigns:

```java
this.sets = MemoryUtil.memAllocLong(this.poolSize);
```

When the descriptor pool grows, `sets` is replaced with a new native allocation. The old `LongBuffer` is not explicitly freed before replacement.

Pool growth is infrequent, so this is not a frame-time disaster, but it is a real native leak.

Backport direction: `memFree` the previous native buffer after it is no longer needed, or use stack/native ownership patterns that do not require long-lived manual allocation.

### 11. Dynamic buffers grow aggressively

Several buffers resize using formulas like:

```java
(newCurrentSize + requiredSize) * 2
```

rather than selecting the next sensible capacity above the required total.

Examples include staging, vertex, and uniform buffers.

This can over-allocate substantially after a one-frame spike. Because old resources are deferred for safe destruction, resizing can temporarily keep both old and much larger new buffers alive.

The staging pool starts at roughly 30 MiB per swapchain image, so large texture/chunk bursts can amplify native-memory pressure quickly.

Backport direction:

- grow to `max(old * 2, requiredTotal)` with an optional cap/rounding policy,
- record high-water marks,
- consider shrinking only on explicit reload/reinit, not dynamically per frame.

## Things that look expensive but are probably intentional

### Frame-in-flight fence wait

Waiting the fence associated with `currentFrame` at frame reuse is normal. That is how CPU reuse of per-frame resources is bounded.

The problem is not this wait by itself; the problem is globally draining unrelated upload/transition fences immediately before the main submit.

### `vkDeviceWaitIdle()` during swapchain recreation

This is expensive but only runs when recreating the swapchain (resize/fullscreen/out-of-date path). It can produce visible resize hitches but is not a steady-state FPS problem.

Optimize later if necessary; correctness first.

### MemoryStack allocations

Most `calloc(stack)` / `malloc(stack)` calls are stack-backed LWJGL native allocations and are not Java-heap GC churn. Focus optimization on heap allocations, native persistent allocations, and synchronization first.

## Recommended backport order

### Step A — synchronization architecture

Backport/adapt from newer upstream:

1. semaphore-backed auxiliary command-buffer completion,
2. main graphics submit waits on those semaphores,
3. command-buffer recycling after the owning frame fence completes,
4. batch unavoidable fence waits.

Expected benefit: largest reduction in render-thread stalls and upload-related hitching.

### Step B — correct swapchain image model

1. add `currentImage` separate from `currentFrame`,
2. acquire image before recording render pass,
3. index framebuffer/layout state by `currentImage`,
4. keep frame resources/fences/UBOs/staging indexed by `currentFrame`,
5. make render-finished semaphore lifetime image-safe.

Expected benefit: correctness plus removal of hidden driver-side synchronization assumptions.

### Step C — simplify queue ownership

Before trying to exploit a transfer-only queue:

1. log graphics/present/transfer/compute family IDs,
2. if transfer differs from graphics, temporarily route uploads through graphics queue,
3. compare performance,
4. only restore dedicated transfer queue with correct ownership/semaphore handling.

### Step D — memory selection and leak fixes

1. bitmask-based memory property matching,
2. explicit preference/scoring of memory types,
3. replace mapped `PointerBuffer` ownership with primitive mapped addresses or free them correctly,
4. free descriptor-set native buffers on replacement,
5. tame buffer growth policy.

### Step E — CPU hot-path cleanup

1. cache/pack pipeline state,
2. reduce descriptor-update heap allocation,
3. instrument pipeline binds, pipeline creations, descriptor writes, and UBO bytes/frame,
4. optimize only paths proven hot by counters/profiling.

## Runtime instrumentation to add

Before and during performance work, log or expose counters for:

- frame fence wait time,
- number of auxiliary queue submits per frame,
- number of auxiliary fences waited per frame,
- total CPU time inside synchronization waits,
- upload bytes per frame,
- staging-buffer high-water mark,
- number of staging/vertex/UBO resizes,
- queue-family IDs,
- pipeline binds per frame,
- new graphics pipelines created,
- descriptor updates per frame,
- swapchain acquired image vs current frame index,
- currentFrame fence latency.

These should be debug/profiling counters, not noisy unconditional production logs.

## Expected impact ranking

### Very likely large

- remove pre-submit global CPU fence drain,
- batch/chain texture uploads and transitions,
- correct acquire-before-record swapchain flow.

### Potentially large on some drivers/hardware

- correct transfer-queue ownership,
- fix memory-type selection.

### Moderate CPU/frame-time improvement in modded scenes

- reduce pipeline-state allocation/hash churn,
- reduce descriptor update allocation/churn.

### Mostly memory robustness

- mapped pointer leak,
- descriptor native-buffer leak,
- buffer over-growth.

## Important constraint

Do not apply all these changes before Vulkan activation is proven. The first performance patch should be made against a verified Vulkan-running baseline so that benchmark results cannot accidentally compare OpenGL against a partially active Vulkan path.
