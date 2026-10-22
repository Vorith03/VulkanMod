#!/usr/bin/env python3
"""Behavioral evidence safeguards and production geometry provenance matching."""
import copy
import importlib.util
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('appearance', Path(__file__).with_name('terrain-appearance.py'))
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)


def rejects(fn):
    try:
        fn()
    except ValueError:
        return
    raise AssertionError('invalid evidence was accepted')


view = '0,192,0,-90,30,2552,1374,32,true,true,' + ','.join(['3f800000'] * 16)
conditions = {'save': 'immutable'}
reference = {'schema': 1, 'metric': 'command coverage', 'conditions': conditions, 'view': view, 'intent': 'pose',
             'sections_layers': [(0, 0, 0, 0), (16, 0, 0, 0), (16, 0, 0, 1), (128, 0, 0, 0)],
             'limits': []}
trace = {'origin': 1_000_000_000, 'views': {view}, 'intents': {'pose'}, 'final_view': view, 'view_locked_since': 1_000_000_000, 'draws': [], 'stages': {},
         'sha256': 'fixture', 'reference': 55_000_000_000}


def draw(key, seconds, generation=1, current=1, producer='cpu'):
    return {'key': key, 'recorded_ns': trace['origin'] + int(seconds * 1e9),
            'confirmed_ns': trace['origin'] + int(seconds * 1e9) + 10_000_000,
            'generation': generation, 'input_generation': current, 'producer': producer}


trace['draws'] = [draw((0, 0, 0, 0), 1), draw((16, 0, 0, 0), 2),
                  draw((16, 0, 0, 1), 3), draw((128, 0, 0, 0), 4, 0, 1)]
report = a.measure(trace, reference, conditions)
assert report['intended_view']['denominator'] == 3
assert report['intended_view']['observed'] == 2
assert report['intended_view']['fence_upper_bound_seconds']['50'] == 3.01
assert report['intended_view']['fence_upper_bound_seconds']['90'] is None
assert report['nearby']['denominator'] == 2
assert report['intended_view']['holes'] == [(128, 0, 0)]
assert report['all_required_inputs_to_draw'] == 'unknown'
# Unknown generation and an unresolved unsupported/reference layer remain holes.
trace['draws'].append(draw((128, 0, 0, 0), 5, a.UNKNOWN, 1))
assert a.measure(trace, reference, conditions)['intended_view']['observed'] == 2
# A GPU APPEND half is insufficient; pair completion uses the later first half.
trace['draws'].append(draw((128, 0, 0, 0), 5, producer='gpu_append'))
assert a.measure(trace, reference, conditions)['intended_view']['observed'] == 2
trace['draws'].append(draw((128, 0, 0, 0), 6, producer='cpu_append'))
assert a.measure(trace, reference, conditions)['intended_view']['fence_upper_bound_seconds']['100'] == 6.01
# An input generation advancing after a retained mesh invalidates its progress.
trace['stages'][(128, 0, 0, 2, 'REGION_SNAPSHOT_READY')] = 8_000_000_000
assert a.measure(trace, reference, conditions)['intended_view']['observed'] == 2
rejects(lambda: a.measure(trace, reference, {'save': 'different'}))
bad = copy.deepcopy(trace); bad['final_view'] = 'changed'
rejects(lambda: a.measure(bad, reference, conditions))
empty = {**reference, 'sections_layers': []}
rejects(lambda: a.measure(trace, empty, conditions))
rejects(lambda: a.freeze(trace, conditions, False, {}))
rejects(lambda: a.freeze(trace, conditions, True, {}))  # No late CPU census.
# A frozen reference comes only from verified late CPU commands, never empty tasks.
ref_trace = copy.deepcopy(trace)
ref_trace['draws'] = [draw((0, 0, 0, 0), 56)]
frozen = a.freeze(ref_trace, conditions, True, {'empty': [[0, 16, 0]], 'occluded': [], 'unsupported': [], 'unavailable': []})
assert frozen['sections_layers'] == [(0, 0, 0, 0)]
ref_trace['draws'].append(draw((0, 0, 0, 0), 57, 2, 2))
rejects(lambda: a.freeze(ref_trace, conditions, True, {}))
with tempfile.TemporaryDirectory() as tmp:
    log = Path(tmp) / 'latest.log'
    lines = f'''VULKANMOD_TERRAIN_WORLD_RENDER_ENTER: epoch=1 origin_ns=100 epoch_ms=0;
VULKANMOD_TERRAIN_VIEW: epoch=1 observed_ns=100 signature={view} intent=pose
VULKANMOD_TERRAIN_WORLD_RENDER_ENTER: epoch=3 origin_ns=200 epoch_ms=0;
VULKANMOD_TERRAIN_VIEW: epoch=3 observed_ns=200 signature={view} intent=pose
'''
    log.write_text(lines)
    assert a.load(log, 1)['origin'] == 100
    assert a.load(log, 3)['origin'] == 200
    # Entry eye/FOV interpolation is an explicit conservative delay, never a new origin.
    transition = lines.replace('signature=' + view, 'signature=' + view.replace('192,', '190,', 1), 1)
    transition += f'VULKANMOD_TERRAIN_VIEW: epoch=1 observed_ns=150 signature={view} intent=pose\n'
    log.write_text(transition)
    entry = a.load(log, 1)
    assert entry['origin'] == 100 and entry['view_locked_since'] == 150
    invalid_projection = lines.replace('3f800000', 'ffc00000')
    log.write_text(invalid_projection)
    rejects(lambda: a.load(log, 1))
    log.write_text(lines + 'VULKANMOD_TERRAIN_DRAW_READBACK_INCOMPLETE: exhausted\n')
    rejects(lambda: a.load(log, 1))
    log.write_text(lines + 'VULKANMOD_TERRAIN_VIEW: epoch=1 observed_ns=200 signature=changed intent=moved\n')
    rejects(lambda: a.load(log, 1))
    test = Path(tmp) / 'ProvenanceTest.java'
    test.write_text('''import java.util.List;
import net.vulkanmod.render.chunk.TerrainDrawProvenance;
public class ProvenanceTest {
 public static void main(String[] args) {
  var cpu = new TerrainDrawProvenance.Geometry(0, 6, 0, 32, 10, 11, "cpu");
  var gpu = new TerrainDrawProvenance.Geometry(0, 12, 0, 64, 9, 11, "gpu_append");
  int[] command = {12,1,0,64,0};
  var result = TerrainDrawProvenance.match(List.of(cpu,gpu), command, 0);
  if(result != gpu || result.generation()!=9 || result.inputGeneration()!=11) throw new AssertionError();
  if(TerrainDrawProvenance.match(List.of(gpu,gpu), command, 0)!=null) throw new AssertionError("ambiguous");
  command[3]++;
  if(TerrainDrawProvenance.match(List.of(cpu,gpu), command, 0)!=null) throw new AssertionError("stale offsets");
  if(TerrainDrawProvenance.match(List.of(cpu), new int[]{6,1,0,32,0}, 0)!=cpu) throw new AssertionError("cpu");
  if(TerrainDrawProvenance.match(List.of(cpu), new int[]{6,0,0,32,0}, 0)!=null) throw new AssertionError("zero");
 }
}''')
    subprocess.run(['javac', '--release', '17', '-d', tmp,
                    str(ROOT / 'src/main/java/net/vulkanmod/render/chunk/TerrainDrawProvenance.java'), str(test)], check=True)
    subprocess.run(['java', '-cp', tmp, 'ProvenanceTest'], check=True)
print('Terrain appearance evidence/provenance safeguards passed')
