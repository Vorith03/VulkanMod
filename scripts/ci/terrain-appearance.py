#!/usr/bin/env python3
"""Freeze and compare NONEMPTY PRODUCTION COMMAND coverage; never infer presented pixels.

No reference is synthesized from a candidate's delivered/eligible section count.
The reference is a separate, untimed, visually checked CPU run of the same immutable
save/view. Unavailable reference members remain holes in every measured trial.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re

PREFIX = 'VULKANMOD_TERRAIN_'
FIELDS = re.compile(r'(\w+)=([^ ;]+)')
UNKNOWN = -(1 << 63)


def load(path, epoch):
    trace = {'origin': None, 'views': set(), 'draws': [], 'stages': {}, 'reference': None,
             'incomplete': False, 'epoch': epoch, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    for line in path.read_text(errors='replace').splitlines():
        if PREFIX not in line:
            continue
        marker = line.split(PREFIX, 1)[1].split(':', 1)[0]
        fields = dict(FIELDS.findall(line))
        if 'INCOMPLETE' in marker:  # Legacy marker lacks epoch; conservatively invalidate whole file.
            trace['incomplete'] = True
        if fields.get('epoch') != str(epoch):
            continue
        if fields.get('incomplete') == 'true':
            trace['incomplete'] = True
        if marker == 'WORLD_RENDER_ENTER':
            value = int(fields['origin_ns'])
            if trace['origin'] is not None and trace['origin'] != value:
                raise ValueError('multiple origins for one epoch')
            trace['origin'] = value
        elif marker == 'VIEW':
            trace['views'].add(fields['signature'])
        elif marker == 'REFERENCE_BEGIN':
            trace['reference'] = int(fields['observed_ns'])
        elif marker == 'GEOMETRY_CONSUMED':
            xyz = tuple(map(int, fields['section'].strip('()').split(',')))
            if len(xyz) != 3 or any(x % 16 for x in xyz):
                raise ValueError('unaligned section identity')
            trace['draws'].append({**fields, 'key': (*xyz, int(fields['layer'])),
                                   **{name: int(fields[name]) for name in
                                      ('generation', 'input_generation', 'recorded_ns', 'confirmed_ns')}})
        elif marker == 'SECTION_STAGE':
            xyz = tuple(map(int, fields['section'].strip('()').split(',')))
            key = (*xyz, int(fields['generation']), fields['stage'])
            trace['stages'][key] = min(int(fields['observed_ns']), trace['stages'].get(key, 1 << 64))
    if trace['origin'] is None:
        raise ValueError('missing world-render entry origin')
    if trace['incomplete']:
        raise ValueError('incomplete capture; percentages forbidden')
    if len(trace['views']) != 1:
        raise ValueError('fixed view missing or changed; percentages forbidden')
    return trace


def freeze(trace, conditions, visual_reference, excluded):
    if not visual_reference:
        raise ValueError('reference requires independent final visual correctness/convergence check')
    if trace['reference'] is None:
        raise ValueError('missing late production reference census')
    draws = [d for d in trace['draws'] if d['recorded_ns'] >= trace['reference']]
    if not draws or any(d['producer'] != 'cpu' for d in draws):
        raise ValueError('reference requires nonempty CPU-owned production geometry')
    if any(d['generation'] == UNKNOWN or d['generation'] != d['input_generation'] for d in draws):
        raise ValueError('reference contains stale/unknown geometry')
    categories = {'empty', 'occluded', 'unsupported', 'unavailable'}
    if not isinstance(excluded, dict) or not categories <= excluded.keys():
        raise ValueError('explicit empty/occluded/unsupported/unavailable inventory required')
    if excluded['unavailable']:
        raise ValueError('reference inputs unavailable; freeze only after they are available')
    keys = sorted({d['key'] for d in draws})
    xyz = {key[:3] for key in keys}
    if any(tuple(item) in xyz for category in ('empty', 'occluded') for item in excluded[category]):
        raise ValueError('nonempty production reference overlaps excluded/empty inventory')
    # A changing generation during the census is not a static reference.
    for key in keys:
        if len({d['generation'] for d in draws if d['key'] == key}) != 1:
            raise ValueError('reference generation changed during census')
    return {'schema': 1, 'metric': 'frame-fence-confirmed nonempty production command coverage',
            'conditions': conditions, 'view': next(iter(trace['views'])),
            'source_log_sha256': trace['sha256'], 'sections_layers': keys,
            'exclusions': excluded, 'visual_reference_checked': True,
            'limits': ['not a pixel/presentation or first-usable-frame oracle',
                       'all-inputs availability unknown; snapshot latency reported separately',
                       'empty/excluded sections never count as progress']}


def milestone(times, denominator):
    ordered = sorted(times)
    return {str(percent): (ordered[math.ceil(denominator * percent / 100) - 1] / 1e9
                          if len(ordered) >= math.ceil(denominator * percent / 100) else None)
            for percent in (50, 90, 99, 100)} if denominator else {str(p): None for p in (50, 90, 99, 100)}


def measure(trace, reference, conditions):
    if reference.get('schema') != 1 or reference.get('conditions') != conditions:
        raise ValueError('reference schema/immutable workload conditions differ')
    if reference['view'] != next(iter(trace['views'])):
        raise ValueError('reference/candidate camera, projection or visibility settings differ')
    members = {tuple(key) for key in reference['sections_layers']}
    if not members or len(members) != len(reference['sections_layers']):
        raise ValueError('reference must be nonempty and unique')
    for key in members:
        if len(key) != 4 or any(not isinstance(v, int) for v in key) or any(v % 16 for v in key[:3]):
            raise ValueError('invalid reference identity')
    # Preserve the final known required generation. Never credit an older retained mesh.
    latest = {}
    for key in trace['stages']:
        xyz, generation = key[:3], key[3]
        latest[xyz] = max(generation, latest.get(xyz, UNKNOWN))
    for d in trace['draws']:
        xyz = d['key'][:3]
        latest[xyz] = max(d['input_generation'], latest.get(xyz, UNKNOWN))
    parts = {}
    for d in trace['draws']:
        if d['key'] not in members or d['generation'] == UNKNOWN or d['generation'] != latest[d['key'][:3]]:
            continue
        if d['confirmed_ns'] < d['recorded_ns'] or d['recorded_ns'] < trace['origin']:
            raise ValueError('impossible recording/fence chronology')
        part = (d['key'], d['producer'])
        previous = parts.get(part)
        if previous is None or d['recorded_ns'] < previous['recorded_ns']:
            parts[part] = d
    first = {}
    for key in members:
        complete = [parts[(key, producer)] for producer in ('cpu', 'gpu') if (key, producer) in parts]
        if (key, 'cpu_append') in parts and (key, 'gpu_append') in parts:
            complete.append(max((parts[(key, p)] for p in ('cpu_append', 'gpu_append')),
                                key=lambda d: d['confirmed_ns']))
        if complete:
            first[key] = min(complete, key=lambda d: d['confirmed_ns'])
    xyz_members = {key[:3] for key in members}
    section_times = {}
    recording_times = {}
    for xyz in xyz_members:
        layers = [key for key in members if key[:3] == xyz]
        if all(key in first for key in layers):
            section_times[xyz] = max(first[key]['confirmed_ns'] for key in layers) - trace['origin']
            recording_times[xyz] = max(first[key]['recorded_ns'] for key in layers) - trace['origin']
    cam = list(map(float, reference['view'].split(',')[:3]))
    nearby = {xyz for xyz in xyz_members if (xyz[0] + 8 - cam[0]) ** 2 + (xyz[2] + 8 - cam[2]) ** 2 <= 64 ** 2}
    snapshot_delays = []
    for key, d in first.items():
        start = trace['stages'].get((*key[:3], d['generation'], 'REGION_SNAPSHOT_READY'))
        if start is not None and d['recorded_ns'] >= start:
            snapshot_delays.append((d['confirmed_ns'] - start) / 1e6)
    return {'metric': reference['metric'], 'conditions': conditions, 'log_sha256': trace['sha256'],
            'intended_view': {'denominator': len(xyz_members), 'observed': len(section_times),
                              'fence_upper_bound_seconds': milestone(section_times.values(), len(xyz_members)),
                              'recording_lower_bound_seconds': milestone(recording_times.values(), len(xyz_members)),
                              'holes': sorted(xyz_members - section_times.keys())},
            'nearby': {'denominator': len(nearby), 'observed': len(nearby & section_times.keys()),
                       'fence_upper_bound_seconds': milestone([section_times[x] for x in nearby if x in section_times], len(nearby)),
                       'recording_lower_bound_seconds': milestone([recording_times[x] for x in nearby if x in recording_times], len(nearby)),
                       'holes': sorted(nearby - section_times.keys())},
            'snapshot_to_fence_ms': {'samples': len(snapshot_delays), 'max': max(snapshot_delays, default=None),
                                    'mean': sum(snapshot_delays) / len(snapshot_delays) if snapshot_delays else None},
            'all_required_inputs_to_draw': 'unknown', 'first_usable_presented_frame': 'unknown',
            'target_2_to_3s_and_5s': 'not established by command coverage', 'limits': reference['limits']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('reference', 'measure'))
    parser.add_argument('log', type=Path)
    parser.add_argument('--epoch', type=int, required=True)
    parser.add_argument('--conditions', type=Path, required=True, help='JSON: immutable save/mods/config IDs and cache/trial stratum')
    parser.add_argument('--reference', type=Path)
    parser.add_argument('--visual-reference-checked', action='store_true')
    parser.add_argument('--exclusions', type=Path, help='JSON inventory of empty, occluded and unavailable reference sections')
    args = parser.parse_args()
    try:
        trace = load(args.log, args.epoch)
        conditions = json.loads(args.conditions.read_text())
        required = {'save_sha256', 'mods_sha256', 'config_sha256', 'scene', 'cache', 'entry_kind', 'terrain_kind'}
        if not required <= conditions.keys():
            raise ValueError('missing immutable workload/cache/entry/terrain conditions')
        if args.operation == 'reference':
            if args.exclusions is None:
                raise ValueError('explicit excluded/empty/unavailable inventory required')
            result = freeze(trace, conditions, args.visual_reference_checked, json.loads(args.exclusions.read_text()))
        else:
            if args.reference is None:
                raise ValueError('--reference required; candidate cannot define its own denominator')
            result = measure(trace, json.loads(args.reference.read_text()), conditions)
        print(json.dumps(result, indent=2))
    except (ValueError, KeyError, OSError) as error:
        parser.exit(2, f'Appearance evidence rejected: {error}\n')


if __name__ == '__main__':
    main()
