#!/usr/bin/env python3
"""Test production initial-population ownership and the live-world maintenance regression."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
package = Path('net/vulkanmod/render/chunk/build')
harness = '''package net.vulkanmod.render.chunk.build;
public class PopulationContract {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var disabled = new TerrainPopulationTracker(false);
        check(disabled.begin(true) == null, "disabled tracker allocated a ticket");
        var tracker = new TerrainPopulationTracker(true);
        var empty = tracker.snapshot();
        var first = tracker.begin(true);
        check(tracker.snapshot().pending() == 1, "queued initial task was not owned");
        check(!TerrainPopulationTracker.stable(tracker.snapshot(), empty, 6559, 6559), "queued population accepted");
        // Building/worker completion does not retire ownership: publication must finish.
        check(!tracker.unchanged(empty), "initial worker work entered capture");
        first.finish(true);
        var populated = tracker.snapshot();
        check(populated.pending() == 0 && populated.published() == 1, "publication ownership leaked");
        first.finish(false); first.finish(true);
        check(tracker.snapshot().equals(populated), "duplicate cancellation/publication changed counters");
        check(!TerrainPopulationTracker.stable(populated, empty, 6559, 6559), "newly published population accepted immediately");
        check(TerrainPopulationTracker.stable(populated, populated, 6559, 6559), "stable population rejected");
        check(!TerrainPopulationTracker.stable(populated, populated, 6560, 6559), "growing render graph accepted");
        // Owner #1003 trace: constant 6559 sections, recurrent existing-section rebuilds.
        // All maintenance remains executable; none may reset the population window.
        int stableSeconds=0;
        for(int second=0; second<15; second++) {
            for(int rebuild=0; rebuild<11; rebuild++)
                check(tracker.begin(false) == null, "maintenance created initial ownership");
            var current=tracker.snapshot();
            if(TerrainPopulationTracker.stable(current, populated, 6559, 6559)) stableSeconds++;
            else stableSeconds=0;
            populated=current;
        }
        check(stableSeconds>=10, "live-world rebuild churn prevented convergence");
        var cancelled=tracker.begin(true);
        // Cancellation request alone cannot admit capture while a worker still owns a build.
        check(tracker.snapshot().pending()==1 && !tracker.unchanged(populated), "cancelled running build disappeared");
        cancelled.finish(false);
        check(tracker.snapshot().pending()==0 && tracker.snapshot().published()==1, "cancel counted as publication");
        var stale=tracker.begin(true); tracker.reset();
        var afterReset=tracker.snapshot(); stale.finish(true);
        check(tracker.snapshot().equals(afterReset), "old-generation result changed recreated dispatcher");
        check(!TerrainPopulationTracker.stable(afterReset, populated, 6559, 6559), "dispatcher recreation accepted");
        var capture=tracker.snapshot();
        check(tracker.unchanged(capture), "stable capture rejected");
        var resumed=tracker.begin(true); resumed.finish(true);
        check(!tracker.unchanged(capture), "resumed population was allowed into capture");
        // Completion/cancellation races cannot underflow pending or count twice.
        for(int i=0;i<100;i++) {
            var ticket=tracker.begin(true);
            var a=new Thread(() -> ticket.finish(false));
            var b=new Thread(() -> ticket.finish(true));
            a.start(); b.start(); a.join(); b.join();
            check(tracker.snapshot().pending()==0, "race leaked/underflowed initial task");
        }
    }
}
'''
# Keep the lifecycle bindings in the production task/dispatcher, including synchronous
# builds, cancellation, unsuccessful worker exit, publication and teardown.
task = (root / 'src/main/java' / package / 'ChunkTask.java').read_text()
dispatcher = (root / 'src/main/java' / package / 'TaskDispatcher.java').read_text()
controller = (root / 'src/main/java/net/vulkanmod/render/profiling/AutomatedBenchmark.java').read_text()
assert 'enabled() && !renderSection.isCompiled()' in task
assert 'preparePopulation();\n            try {' in task
assert 'chunkTask.preparePopulation();' in dispatcher
assert 'if (!populationHandedOff) completePopulation(false)' in task
assert 'this.populationHandedOff = true;' in task
assert 'public void cancel() { this.cancelled.set(true); }' in task
assert dispatcher.count('chunkTask.completePopulation(false);') == 2
assert 'task.completePopulation(populationPublished);' in dispatcher
assert 'this.terrainPopulation.reset();' in dispatcher
assert 'TerrainPopulationTracker.stable(' in controller
assert 'capturePopulationTracker.unchanged(capturePopulation)' in controller
assert '.terrainPopulation() != capturePopulationTracker' in controller
# Production publication must invalidate traversal only on graph-topology
# changes: mesh bytes, UVs and existing draw parameters update in place.
render = (root / 'src/main/java/net/vulkanmod/render/chunk/WorldRenderer.java').read_text()
section = (root / 'src/main/java/net/vulkanmod/render/chunk/RenderSection.java').read_text()
frustum = (root / 'src/main/java/net/vulkanmod/render/chunk/VFrustum.java').read_text()
assert 'sameCullingVolume(VFrustum prior)' in frustum
assert 'this.matrix.equals(prior.matrix)' in frustum
assert 'cachedAreaFrustum = null;' in render
assert 'this.frustum.sameCullingVolume(this.cachedAreaFrustum)' in render
assert 'this.sectionGrid.updateFrustumVisibility(this.frustum);' in render
assert 'this.taskDispatcher.uploadAllPendingUploads();' in render
assert 'pendingDirtySections' in render
assert 'cachedGraphCoversPendingDirty()' in render
assert 'schedulePendingDirtyOnCachedGraph()' in render
assert 'section.getLastFrame() != this.lastFrame' in render
assert '!area.isGraphVisible(section)' in render
assert 'this.pendingDirtySections.remove(section)' in render
assert render.count('!area.isGraphVisible(section)') >= 2, 'must recheck membership when consuming notices'
assert 'this.pendingDirtySections.clear();' in render
assert 'if(!section.hasXYNeighbours())' in render
assert 'this.cachedGraphRebuildSchedules++;' in render
assert 'this.pendingDirtySections.size() > MAX_CACHED_DIRTY_SECTIONS' in render
assert 'this.worldRenderer.requestSectionRebuild(this);' in section
assert 'this.worldRenderer.setNeedsUpdate();' not in section.split('public void setDirty(', 1)[1].split('\n    }', 1)[0]
assert 'if(this.taskDispatcher.uploadAllPendingUploads())' not in render
for signature, predicate in (('setVisibility(long visibility)', 'this.visibility != visibility'),
                             ('setCompletelyEmpty(boolean b)', 'this.completelyEmpty != b')):
    method_body = section.split('public void ' + signature, 1)[1].split('\n    }', 1)[0]
    assert predicate in method_body and 'this.worldRenderer.setNeedsUpdate();' in method_body, signature
assert 'this.worldRenderer.requestSectionRebuild(this);' in section.split('public void setDirty(', 1)[1].split('\n    }', 1)[0]

with tempfile.TemporaryDirectory(prefix='vulkanmod-population-') as folder:
    path = Path(folder)
    sources = path / package
    sources.mkdir(parents=True)
    (sources / 'TerrainPopulationTracker.java').write_text((root / 'src/main/java' / package / 'TerrainPopulationTracker.java').read_text())
    (sources / 'PopulationContract.java').write_text(harness)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '--release', '17', '-d', str(path / 'classes'), *map(str, sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', str(path / 'classes'), 'net.vulkanmod.render.chunk.build.PopulationContract'], check=True, timeout=30)
print('Terrain population contract passed: queued/publication ownership, maintenance, cancellation race, reset, capture guard')


# Compile and execute the real cached-graph scheduler methods in a minimal
# Java fixture. This checks behavior, not merely the presence of source anchors.
def method_body(source, signature, visibility='private'):
    start = source.index('    ' + visibility + ' ' + signature)
    opening = source.index('{', start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == '{':
            depth += 1
        elif source[index] == '}':
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    raise AssertionError('Unbalanced production Java method: ' + signature)


scheduler = r'''import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CachedGraphContract {
    static void check(boolean ok, String message) {
        if(!ok) throw new AssertionError(message);
    }

    static class RenderSection {
        boolean dirty = true;
        boolean neighbors = true;
        boolean graphVisible = true;
        short frame = 9;
        final ChunkArea area = new ChunkArea();

        boolean isDirty() { return dirty; }
        boolean hasXYNeighbours() { return neighbors; }
        ChunkArea getChunkArea() { return area; }
        short getLastFrame() { return frame; }
    }

    static class ChunkArea {
        boolean isGraphVisible(RenderSection section) { return section.graphVisible; }
    }

    static class RenderRegionCache {}

    static class TaskDispatcher {
        int capacity = 5;
        int getBuildSchedulingCapacity() { return capacity; }
    }

    static class Renderer {
        final Set<RenderSection> pendingDirtySections = ConcurrentHashMap.newKeySet();
        final List<RenderSection> chunkQueue = new ArrayList<>(List.of(new RenderSection()));
        final TaskDispatcher taskDispatcher = new TaskDispatcher();
        static final int MAX_CACHED_DIRTY_SECTIONS = 512;
        short lastFrame = 9;
        boolean needsUpdate;
        long cachedGraphRebuildSchedules;
        int scheduled;
        Runnable onSchedule;

        boolean scheduleUpdate(RenderSection section, int capacity, RenderRegionCache cache) {
            if(capacity <= 0 || !section.dirty) return false;
            scheduled++;
            section.dirty = false;
            if(onSchedule != null) onSchedule.run();
            return true;
        }

__METHODS__
    }

    public static void main(String[] args) {
        Renderer r = new Renderer();
        RenderSection a = new RenderSection();
        r.pendingDirtySections.add(a);
        check(r.cachedGraphCoversPendingDirty(), "known visible dirty section rejected");
        r.schedulePendingDirtyOnCachedGraph();
        check(r.scheduled == 1 && r.cachedGraphRebuildSchedules == 1,
                "first dirty section not admitted exactly once");
        check(r.pendingDirtySections.isEmpty() && !a.dirty,
                "successful publication left stale dirty notice");

        r.taskDispatcher.capacity = 0;
        a.dirty = true;
        r.pendingDirtySections.add(a);
        r.schedulePendingDirtyOnCachedGraph();
        check(r.pendingDirtySections.contains(a) && r.scheduled == 1,
                "zero capacity dropped pending section");

        r.taskDispatcher.capacity = 5;
        a.neighbors = false;
        r.schedulePendingDirtyOnCachedGraph();
        check(r.pendingDirtySections.contains(a) && r.scheduled == 1,
                "unavailable neighbors dropped or scheduled the section");
        a.neighbors = true;
        r.schedulePendingDirtyOnCachedGraph();
        check(!r.pendingDirtySections.contains(a) && r.scheduled == 2,
                "ready neighbor retry failed");

        a.dirty = true;
        a.frame = 8;
        r.pendingDirtySections.add(a);
        check(!r.cachedGraphCoversPendingDirty(), "stale traversal stamp passed");
        r.schedulePendingDirtyOnCachedGraph();
        check(r.needsUpdate && r.pendingDirtySections.contains(a) && r.scheduled == 2,
                "off-graph section was scheduled or lost");

        r.needsUpdate = false;
        a.frame = 9;
        a.graphVisible = false;
        check(!r.cachedGraphCoversPendingDirty(), "area membership mismatch passed");
        a.graphVisible = true;
        r.pendingDirtySections.clear();

        RenderSection b = new RenderSection(), c = new RenderSection();
        r.pendingDirtySections.add(b);
        r.pendingDirtySections.add(c);
        r.taskDispatcher.capacity = 1;
        r.schedulePendingDirtyOnCachedGraph();
        check(r.scheduled == 3 && r.pendingDirtySections.size() == 1,
                "backpressure did not preserve exactly one pending section");
        r.taskDispatcher.capacity = 5;
        r.schedulePendingDirtyOnCachedGraph();
        check(r.scheduled == 4 && r.pendingDirtySections.isEmpty(),
                "backpressure retry lost pending section");

        // A re-dirty while submitting must survive removal-before-admission.
        RenderSection d = new RenderSection();
        r.onSchedule = () -> { d.dirty = true; r.pendingDirtySections.add(d); };
        r.pendingDirtySections.add(d);
        r.schedulePendingDirtyOnCachedGraph();
        r.onSchedule = null;
        check(r.pendingDirtySections.contains(d), "concurrent redirty lost");
        r.pendingDirtySections.clear();

        for(int i = 0; i <= Renderer.MAX_CACHED_DIRTY_SECTIONS; i++)
            r.pendingDirtySections.add(new RenderSection());
        check(!r.cachedGraphCoversPendingDirty(), "over-capacity queue accepted");
        r.pendingDirtySections.clear();
        r.chunkQueue.clear();
        r.pendingDirtySections.add(new RenderSection());
        check(!r.cachedGraphCoversPendingDirty(), "empty graph reused");
        System.out.println("Cached graph scheduler functional contract passed");
    }
}
'''
scheduler = scheduler.replace('__METHODS__', '\n\n'.join((
    method_body(render, 'boolean cachedGraphCoversPendingDirty()'),
    method_body(render, 'void schedulePendingDirtyOnCachedGraph()'),
)))
with tempfile.TemporaryDirectory(prefix='vulkanmod-cached-graph-') as directory:
    java_source = Path(directory) / 'CachedGraphContract.java'
    java_source.write_text(scheduler)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '--release', '17',
                    '-d', directory, str(java_source)], check=True, timeout=30)
    subprocess.run(['java', '-cp', directory, 'CachedGraphContract'], check=True, timeout=30)


# Compile production ring-slot coordinate validation as Java 17, then exercise
# aliasing after camera movement, negative Y, and integer overflow. No Forge
# runtime, Vulkan driver, Gradle build, or distributable JAR is needed.
grid_source = (root / 'src/main/java/net/vulkanmod/render/chunk/SectionGrid.java').read_text()
assert 'int j = sectionY - this.level.getMinSection();' in grid_source
assert 'if(j < 0 || j >= this.gridHeight)' in grid_source
assert 'SectionRingOwnership.matches(sectionX, sectionY, sectionZ,' in grid_source
assert 'renderSection.setDirty(playerChanged);' in grid_source
ring_class = root / 'src/main/java/net/vulkanmod/render/chunk/SectionRingOwnership.java'
ring_harness = r'''package net.vulkanmod.render.chunk;
public final class SectionRingOwnershipContract {
    private static void check(boolean condition, String reason) {
        if(!condition) throw new AssertionError(reason);
    }

    public static void main(String[] ignored) {
        check(SectionRingOwnership.matches(0, -4, 0, 0, -64, 0), "negative Y origin");
        check(SectionRingOwnership.matches(-20, -4, 47, -320, -64, 752), "negative X and high Z");
        check(!SectionRingOwnership.matches(-3, -4, 47, -320, -64, 752), "ring X alias");
        check(!SectionRingOwnership.matches(-20, -4, 30, -320, -64, 752), "ring Z alias");
        check(!SectionRingOwnership.matches(-20, 20, 47, -320, -64, 752), "vertical modulo alias");
        check(!SectionRingOwnership.matches(-20, -4, 47, -319, -64, 752), "unaligned X origin");
        check(!SectionRingOwnership.matches(-20, -4, 47, -320, -65, 752), "wrong Y section");
        check(SectionRingOwnership.matches(12, 19, -8, 192, 304, -128), "positive ring origin");

        // The new owner is valid; old aliases pointing at the recycled slot aren't.
        check(!SectionRingOwnership.matches(-3, -4, 47, -320, -64, 752), "old ring owner");
        check(SectionRingOwnership.matches(-20, -4, 47, -320, -64, 752), "new ring owner");
        check(!SectionRingOwnership.matches(Integer.MIN_VALUE, -4, 47, 0, -64, 752),
                "integer-overflow X alias");
        check(!SectionRingOwnership.matches(-20, Integer.MAX_VALUE, 47, -320, -16, 752),
                "integer-overflow Y alias");

        // Execute SectionGrid's production setDirty path, including height and
        // ring indexing, rather than testing only its coordinate predicate.
        SectionGrid grid = new SectionGrid();
        RenderSection resident = new RenderSection(-320, -64, 752);
        grid.chunks[grid.getChunkIndex(0, 0, 2)] = resident;
        grid.setDirty(-20, -4, 47, true);
        check(resident.dirtyCalls == 1 && resident.playerChanged,
                "resident update was dropped or player flag was lost");
        grid.setDirty(-15, -4, 47, false);
        grid.setDirty(-20, -4, 42, false);
        grid.setDirty(-20, -28, 47, false);
        grid.setDirty(-20, 20, 47, false);
        grid.setDirty(Integer.MIN_VALUE, -4, 47, false);
        check(resident.dirtyCalls == 1, "remote/height alias dirtied the resident");

        resident.xOffset = -240; // recycle the same X ring slot to section -15
        grid.setDirty(-20, -4, 47, true);
        check(resident.dirtyCalls == 1, "old owner dirtied the recycled resident");
        grid.setDirty(-15, -4, 47, false);
        check(resident.dirtyCalls == 2 && !resident.playerChanged,
                "new owner was rejected or notification flag changed");

        // Every legitimate section throughout the negative-height grid must
        // still be admitted, independent of X/Z sign or ring index.
        for(int x = -7; x < -2; x++) {
            for(int y = -4; y < 20; y++) {
                for(int z = -2; z < 3; z++) {
                    RenderSection slot = new RenderSection(x * 16, y * 16, z * 16);
                    grid.chunks[grid.getChunkIndex(Math.floorMod(x, grid.gridWidth),
                            y + 4, Math.floorMod(z, grid.gridWidth))] = slot;
                    grid.setDirty(x, y, z, false);
                    check(slot.dirtyCalls == 1, "valid grid owner lost notification");
                }
            }
        }
        System.out.println("Section ring ownership contract passed");
    }

    static final class Level { int getMinSection() { return -4; } }
    static final class RenderSection {
        int xOffset, yOffset, zOffset, dirtyCalls;
        boolean playerChanged;
        RenderSection(int x, int y, int z) { xOffset=x; yOffset=y; zOffset=z; }
        void setDirty(boolean player) { dirtyCalls++; playerChanged=player; }
    }
    static final class SectionGrid {
        final Level level = new Level();
        final int gridWidth = 5, gridHeight = 24;
        final RenderSection[] chunks = new RenderSection[gridWidth * gridHeight * gridWidth];
        SectionGrid() {
            for(int x=0; x<gridWidth; x++)
                for(int y=0; y<gridHeight; y++)
                    for(int z=0; z<gridWidth; z++)
                        chunks[getChunkIndex(x, y, z)] = new RenderSection(x*16, (y-4)*16, z*16);
        }
        private int getChunkIndex(int x, int y, int z) {
            return (z * gridHeight + y) * gridWidth + x;
        }
__SET_DIRTY__
    }
}
'''
ring_harness = ring_harness.replace('__SET_DIRTY__', method_body(
    grid_source, 'void setDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged)',
    visibility='public'))
with tempfile.TemporaryDirectory(prefix='vulkanmod-ring-ownership-') as directory:
    source = Path(directory) / 'SectionRingOwnershipContract.java'
    source.write_text(ring_harness)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '--release', '17',
                    '-d', directory, str(ring_class), str(source)], check=True, timeout=30)
    subprocess.run(['java', '-cp', directory,
                    'net.vulkanmod.render.chunk.SectionRingOwnershipContract'], check=True, timeout=30)
