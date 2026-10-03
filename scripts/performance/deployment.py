#!/usr/bin/env python3
"""Audit/stage an installed Forge server and prepare bounded Chunky commands offline."""
import argparse
import contextlib
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import shlex
import tempfile
import tomllib
import zipfile

DATA_DIRS = ('config', 'defaultconfigs', 'kubejs', 'scripts', 'global_packs')
SERVER_FILES = (*DATA_DIRS, 'mods', 'libraries', 'run.sh', 'run.bat',
                'server.properties', 'user_jvm_args.txt')
FORGE_ARGS = Path('libraries/net/minecraftforge/forge/1.20.1-47.3.0/unix_args.txt')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def safe_tree(root):
    if root.is_symlink():
        raise ValueError(f'Symlink is not a deployment input: {root}')
    if root.is_dir():
        for entry in root.rglob('*'):
            if entry.is_symlink() or (not entry.is_dir() and not entry.is_file()):
                raise ValueError(f'Unsupported deployment entry: {entry}')


def inventory(root):
    mods = root / 'mods'
    if not mods.is_dir():
        raise ValueError(f'Missing mods directory: {mods}')
    safe_tree(mods)
    result = {}
    for jar in sorted(mods.glob('*.jar')):
        ids = [f'file:{jar.name}']
        versions = {}
        with zipfile.ZipFile(jar) as archive:
            if 'META-INF/mods.toml' in archive.namelist():
                info = archive.getinfo('META-INF/mods.toml')
                if info.file_size > 1024 * 1024:
                    raise ValueError(f'Oversized Forge metadata: {jar.name}')
                metadata = tomllib.loads(archive.read(info).decode('utf-8'))
                declarations = metadata.get('mods', [])
                if declarations:
                    ids = [mod['modId'] for mod in declarations]
                    versions = {mod['modId']: mod.get('version', '') for mod in declarations}
        sha = digest(jar)
        for mod_id in ids:
            if mod_id in result:
                raise ValueError(f'Duplicate mod ID {mod_id} in {root}')
            result[mod_id] = {'jar': jar.name, 'sha256': sha, 'version': versions.get(mod_id, '')}
    return result


def policy(path):
    result = json.loads(path.read_text()) if path else {}
    allowed = {'client_only_mods', 'server_only_mods', 'client_only_paths', 'server_only_paths'}
    if set(result) - allowed:
        raise ValueError('Unknown parity policy key')
    for key in allowed:
        values = result.setdefault(key, [])
        if not isinstance(values, list) or any(not isinstance(v, str) or not v for v in values):
            raise ValueError(f'{key} must contain nonempty strings')
        if key.endswith('_paths'):
            for value in values:
                p = Path(value)
                if p.is_absolute() or '..' in p.parts or '\\' in value:
                    raise ValueError(f'Unsafe parity path: {value}')
    if set(result['client_only_mods']) & set(result['server_only_mods']):
        raise ValueError('A mod cannot be both client-only and server-only')
    return result


def files(root):
    result = {}
    for name in DATA_DIRS:
        directory = root / name
        safe_tree(directory)
        if directory.is_dir():
            for path in sorted(directory.rglob('*')):
                if path.is_file():
                    result[path.relative_to(root).as_posix()] = digest(path)
    return result


def excluded(path, prefixes):
    return any(path == prefix.rstrip('/') or path.startswith(prefix.rstrip('/') + '/') for prefix in prefixes)


def audit(client, server, rules):
    client_mods, server_mods = inventory(client), inventory(server)
    errors = []
    for mod_id in sorted(client_mods.keys() | server_mods.keys()):
        c, s = client_mods.get(mod_id), server_mods.get(mod_id)
        if c and s and c['sha256'] != s['sha256']:
            errors.append(f'Shared mod bytes differ: {mod_id}')
        elif c and not s and mod_id not in rules['client_only_mods']:
            errors.append(f'Unclassified client-only mod: {mod_id}')
        elif s and not c and mod_id not in rules['server_only_mods']:
            errors.append(f'Unclassified server-only mod: {mod_id}')
        if s and mod_id in rules['client_only_mods']:
            errors.append(f'Declared client-only mod is installed on server: {mod_id}')
    cfiles, sfiles = files(client), files(server)
    for path in sorted(cfiles.keys() | sfiles.keys()):
        c, s = cfiles.get(path), sfiles.get(path)
        if excluded(path, rules['client_only_paths']):
            if s: errors.append(f'Declared client-only config is installed on server: {path}')
            continue
        if excluded(path, rules['server_only_paths']):
            continue
        if c != s:
            errors.append(f'Shared config/script parity differs: {path}')
    args_file, run_file = server / FORGE_ARGS, server / 'run.sh'
    if not args_file.is_file() or not run_file.is_file():
        errors.append('Server must already have Minecraft 1.20.1 / Forge 47.3.0 installed')
    else:
        safe_tree(args_file)
        safe_tree(run_file)
        arguments = shlex.split(args_file.read_text())
        for key, value in (('--fml.forgeVersion', '47.3.0'), ('--fml.mcVersion', '1.20.1')):
            if key not in arguments or arguments.index(key) + 1 >= len(arguments) or arguments[arguments.index(key)+1] != value:
                errors.append(f'Server launch arguments do not target {key} {value}')
        if FORGE_ARGS.as_posix() not in run_file.read_text():
            errors.append('run.sh does not select the qualified Forge arguments')
    return {'schema': 1, 'minecraft': '1.20.1', 'forge': '47.3.0',
            'status': 'blocked' if errors else 'ready_to_stage', 'errors': errors,
            'client_mods': client_mods, 'server_mods': server_mods,
            'client_data_sha256': cfiles, 'server_data_sha256': sfiles,
            'policy': rules, 'limits': ['Metadata does not establish mod sidedness.',
                                      'Hashes do not replace a real server join and gameplay test.',
                                      'World serverconfig, datapacks, gamerules and simulation settings require review.']}


@contextlib.contextmanager
def offline_world(world):
    # Minecraft's Linux Java FileLock uses POSIX record locks, not flock locks.
    import fcntl
    safe_tree(world)
    if not (world / 'level.dat').is_file() or not (world / 'session.lock').is_file():
        raise ValueError('World needs level.dat and its existing session.lock')
    with (world / 'session.lock').open('r+b') as lock:
        try:
            fcntl.lockf(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('World is open; close its client/server before staging') from error
        try:
            yield
        finally:
            fcntl.lockf(lock, fcntl.LOCK_UN)


def empty_output(path):
    if path.exists() or path.is_symlink():
        raise ValueError(f'Output already exists: {path}')
    path.parent.mkdir(parents=True, exist_ok=True)


def write_report(report, out):
    empty_output(out)
    with out.open('x') as stream:
        json.dump(report, stream, indent=2)
        stream.write('\n')


def prepare(client, server, out, rules, world, heap):
    for source in (client, server, world):
        if source and (out == source or out.is_relative_to(source) or source.is_relative_to(out)):
            raise ValueError('Output and input trees must be separate')
    empty_output(out)
    report = audit(client, server, rules)
    if report['errors']:
        raise ValueError('Parity audit blocked staging:\n' + '\n'.join(report['errors']))
    if not 1 <= heap <= 128:
        raise ValueError('Heap must be 1–128 GiB')
    context = offline_world(world) if world else contextlib.nullcontext()
    with context, tempfile.TemporaryDirectory(prefix='.vulkanmod-server-', dir=out.parent) as temp:
        stage = Path(temp) / 'server'
        stage.mkdir()
        for name in SERVER_FILES:
            source = server / name
            safe_tree(source)
            if source.is_dir(): shutil.copytree(source, stage / name)
            elif source.is_file(): shutil.copy2(source, stage / name)
        if world:
            shutil.copytree(world, stage / 'world', ignore=shutil.ignore_patterns('session.lock'))
            report['world_level_dat_sha256'] = digest(world / 'level.dat')
            # Forge must use the staged world name, including its original serverconfig/datapacks.
            properties = stage / 'server.properties'
            lines = properties.read_text().splitlines() if properties.exists() else []
            lines = [line for line in lines if not re.match(r'^\s*level-name\s*[=:]', line)]
            properties.write_text('\n'.join([*lines, 'level-name=world']) + '\n')
        source_args = stage / 'user_jvm_args.txt'
        previous_args = source_args.read_text() if source_args.exists() else ''
        retained_args = re.sub(r'(?<!\S)-Xm[sx][^\s#]+', '', previous_args)
        source_args.write_text(retained_args + f'\n-Xms1G\n-Xmx{heap}G\n')
        (stage / 'launch-vulkanmod-server.sh').write_text('''#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
java_version="$(java -version 2>&1 | head -n 1)"
if [[ ! "$java_version" =~ \\"17[\\.\\"] ]]; then
  echo "Minecraft 1.20.1 / Forge 47.3.0 requires Java 17 for this deployment" >&2
  exit 1
fi
exec bash ./run.sh nogui "$@"
''')
        report['heap_gib'] = heap
        report['source_user_jvm_args_sha256'] = digest(server / 'user_jvm_args.txt') if (server / 'user_jvm_args.txt').is_file() else None
        (stage / 'deployment-audit.json').write_text(json.dumps(report, indent=2) + '\n')
        # No EULA acceptance, server launch, network configuration or source-world move.
        stage.rename(out)


def pregenerate(dimension, x, z, radius, shape, out):
    if not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+', dimension):
        raise ValueError('Dimension must be a resource ID')
    if not 1 <= radius <= 100000 or max(abs(x), abs(z)) + radius > 29999984:
        raise ValueError('Region must fit world coordinates and use radius 1–100000 blocks')
    if shape not in ('circle', 'square'):
        raise ValueError('Shape must be circle or square')
    commands = [f'chunky world {dimension}', f'chunky shape {shape}', f'chunky center {x} {z}',
                f'chunky radius {radius}', 'chunky selection', 'chunky start']
    upper = (math.floor((x+radius)/16)-math.floor((x-radius)/16)+1) * (math.floor((z+radius)/16)-math.floor((z-radius)/16)+1)
    empty_output(out)
    out.mkdir()
    (out / 'pregen-console.txt').write_text('\n'.join(commands) + '\n')
    (out / 'pregen-ingame.txt').write_text('\n'.join('/'+c for c in commands) + '\n')
    (out / 'pregen-plan.json').write_text(json.dumps({'dimension': dimension, 'center': [x,z],
        'radius_blocks': radius, 'shape': shape, 'bounding_square_chunk_upper_bound': upper,
        'console_commands': commands, 'monitor': ['chunky progress', 'chunky pause', 'chunky continue'],
        'requirements': ['Install a qualified Forge 1.20.1 Chunky release.',
                         'Use a closed-world copy or backed-up server world with unchanged mods/config/datapacks.',
                         'Review the selection before executing chunky start.'],
        'executed': False}, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    for name in ('audit', 'prepare'):
        p = commands.add_parser(name)
        p.add_argument('--client', type=Path, required=True)
        p.add_argument('--server', type=Path, required=True)
        p.add_argument('--policy', type=Path)
        p.add_argument('--out', type=Path, required=True)
        if name == 'prepare':
            p.add_argument('--world', type=Path)
            p.add_argument('--heap-gib', type=int, required=True)
    p = commands.add_parser('pregen')
    p.add_argument('--dimension', required=True)
    p.add_argument('--center', type=int, nargs=2, required=True, metavar=('X','Z'))
    p.add_argument('--radius', type=int, required=True)
    p.add_argument('--shape', choices=('circle','square'), default='circle')
    p.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    try:
        # Check the requested leaf before resolve can follow an existing symlink.
        if args.out.exists() or args.out.is_symlink():
            raise ValueError(f'Output already exists: {args.out}')
        if args.command == 'pregen':
            pregenerate(args.dimension, *args.center, args.radius, args.shape, args.out.resolve())
        else:
            client, server = args.client.resolve(), args.server.resolve()
            rules = policy(args.policy)
            if args.command == 'audit':
                report = audit(client, server, rules)
                write_report(report, args.out.resolve())
                return int(bool(report['errors']))
            prepare(client, server, args.out.resolve(), rules,
                    args.world.resolve() if args.world else None, args.heap_gib)
        return 0
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(2, f'{error}\n')


if __name__ == '__main__':
    raise SystemExit(main())
