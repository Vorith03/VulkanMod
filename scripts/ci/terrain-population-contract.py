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
assert 'this.taskDispatcher.uploadAllPendingUploads();' in render
assert 'if(this.taskDispatcher.uploadAllPendingUploads())' not in render
for signature, predicate in (('setVisibility(long visibility)', 'this.visibility != visibility'),
                             ('setCompletelyEmpty(boolean b)', 'this.completelyEmpty != b')):
    method_body = section.split('public void ' + signature, 1)[1].split('\n    }', 1)[0]
    assert predicate in method_body and 'this.worldRenderer.setNeedsUpdate();' in method_body, signature
assert 'this.worldRenderer.setNeedsUpdate();' in section.split('public void setDirty(', 1)[1].split('\n    }', 1)[0]

with tempfile.TemporaryDirectory(prefix='vulkanmod-population-') as folder:
    path = Path(folder)
    sources = path / package
    sources.mkdir(parents=True)
    (sources / 'TerrainPopulationTracker.java').write_text((root / 'src/main/java' / package / 'TerrainPopulationTracker.java').read_text())
    (sources / 'PopulationContract.java').write_text(harness)
    subprocess.run([os.environ.get('JAVAC', 'javac'), '--release', '17', '-d', str(path / 'classes'), *map(str, sources.glob('*.java'))], check=True, timeout=30)
    subprocess.run(['java', '-cp', str(path / 'classes'), 'net.vulkanmod.render.chunk.build.PopulationContract'], check=True, timeout=30)
print('Terrain population contract passed: queued/publication ownership, maintenance, cancellation race, reset, capture guard')
