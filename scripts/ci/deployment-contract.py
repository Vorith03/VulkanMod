#!/usr/bin/env python3
"""Offline deployment contracts: strict parity, world locks, staging and commands."""
from pathlib import Path
import importlib.util
import json
import subprocess
import sys
import tempfile
import zipfile

root = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('deployment', root/'scripts/performance/deployment.py')
module = importlib.util.module_from_spec(spec)
sys.dont_write_bytecode = True
spec.loader.exec_module(module)


def jar(directory, filename, mod_id, payload='same'):
    directory.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(directory/filename, 'w') as archive:
        archive.writestr('META-INF/mods.toml', f'[[mods]]\nmodId="{mod_id}"\nversion="1.0"\n')
        archive.writestr('payload', payload)


def rejected(operation, message):
    try:
        operation()
    except ValueError as error:
        assert message in str(error), str(error)
    else:
        raise AssertionError(f'Expected rejection: {message}')


with tempfile.TemporaryDirectory(prefix='vulkanmod-deploy-') as folder:
    base = Path(folder)
    client, server, world = base/'client', base/'server', base/'world'
    jar(client/'mods', 'common.jar', 'common')
    server.mkdir()
    (server/'mods').mkdir()
    # Copy the exact shared bytes; metadata/version equality alone is insufficient.
    (server/'mods/common.jar').write_bytes((client/'mods/common.jar').read_bytes())
    jar(client/'mods', 'client.jar', 'vulkanmod')
    jar(server/'mods', 'chunky.jar', 'chunky')
    for instance in (client, server):
        (instance/'config').mkdir()
        (instance/'config/common.toml').write_text('same=true\n')
    args = server/module.FORGE_ARGS
    args.parent.mkdir(parents=True)
    args.write_text('--fml.forgeVersion 47.3.0 --fml.mcVersion 1.20.1')
    (server/'run.sh').write_text(f'java @user_jvm_args.txt @{module.FORGE_ARGS.as_posix()} "$@"\n')
    (server/'user_jvm_args.txt').write_text('-Dcustom.flag=retained\n-Xms2G -Xmx3G\n')
    rules = module.policy(root/'scripts/performance/parity-policy.example.json')
    assert module.audit(client, server, rules)['status'] == 'ready_to_stage'
    assert module.audit(client, server, module.policy(None))['status'] == 'blocked'
    jar(server/'mods', 'common.jar', 'common', 'changed')
    assert 'Shared mod bytes differ: common' in module.audit(client, server, rules)['errors']
    (server/'mods/common.jar').write_bytes((client/'mods/common.jar').read_bytes())
    (server/'config/common.toml').write_text('same=false\n')
    assert module.audit(client, server, rules)['status'] == 'blocked'
    (server/'config/common.toml').write_text('same=true\n')
    args.write_text('--fml.forgeVersion 47.2.0 --fml.mcVersion 1.20.1')
    assert module.audit(client, server, rules)['status'] == 'blocked'
    args.write_text('--fml.forgeVersion 47.3.0 --fml.mcVersion 1.20.1')
    jar(client/'mods', 'duplicate.jar', 'common')
    rejected(lambda: module.inventory(client), 'Duplicate mod ID')
    (client/'mods/duplicate.jar').unlink()
    (server/'config/link').symlink_to(client/'config/common.toml')
    rejected(lambda: module.audit(client, server, rules), 'Unsupported deployment entry')
    (server/'config/link').unlink()

    world.mkdir()
    (world/'level.dat').write_bytes(b'closed-world-fixture')
    (world/'session.lock').write_bytes(b'lock')
    (world/'serverconfig').mkdir()
    (world/'serverconfig/common.toml').write_text('world_specific=true\n')
    before = module.digest(world/'level.dat')
    # Use a different process: POSIX record locks are process-scoped.
    lock_holder = subprocess.Popen([sys.executable, '-c',
        'import fcntl,sys; f=open(sys.argv[1],"r+b"); fcntl.lockf(f,fcntl.LOCK_EX); '
        'print("locked",flush=True); sys.stdin.read()', str(world/'session.lock')],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    try:
        assert lock_holder.stdout.readline().strip() == 'locked'
        rejected(lambda: module.prepare(client, server, base/'locked-stage', rules, world, 4), 'World is open')
        assert not (base/'locked-stage').exists()
    finally:
        lock_holder.communicate('', timeout=5)
    out = base/'staged'
    module.prepare(client, server, out, rules, world, 4)
    assert module.digest(out/'world/level.dat') == before == module.digest(world/'level.dat')
    assert (out/'world/serverconfig/common.toml').read_text() == 'world_specific=true\n'
    assert not (out/'world/session.lock').exists()
    assert not (out/'eula.txt').exists()
    assert 'level-name=world' in (out/'server.properties').read_text()
    jvm = (out/'user_jvm_args.txt').read_text()
    assert '-Dcustom.flag=retained' in jvm and '-Xmx4G' in jvm and '-Xmx3G' not in jvm
    subprocess.run(['bash','-n',str(out/'launch-vulkanmod-server.sh')], check=True, timeout=5)
    # Exercise the launcher's real Java-version branch without starting a game.
    fake_bin = base/'bin'
    fake_bin.mkdir()
    fake_java = fake_bin/'java'
    fake_java.write_text('#!/bin/sh\nprintf \'openjdk version "17.0.20"\\n\' >&2\n')
    fake_java.chmod(0o755)
    (out/'run.sh').write_text('#!/bin/sh\nprintf \'fixture-launch-ok\\n\'\n')
    import os
    env = dict(os.environ, PATH=str(fake_bin)+os.pathsep+os.environ['PATH'])
    result = subprocess.run(['bash',str(out/'launch-vulkanmod-server.sh')], env=env, capture_output=True, text=True, timeout=5)
    assert result.returncode == 0 and 'fixture-launch-ok' in result.stdout, result.stderr
    fake_java.write_text('#!/bin/sh\nprintf \'openjdk version "21.0.1"\\n\' >&2\n')
    result = subprocess.run(['bash',str(out/'launch-vulkanmod-server.sh')], env=env, capture_output=True, text=True, timeout=5)
    assert result.returncode == 1 and 'fixture-launch-ok' not in result.stdout
    rejected(lambda: module.prepare(client, server, out, rules, world, 4), 'Output already exists')
    rejected(lambda: module.prepare(client, server, client/'nested/out', rules, None, 4), 'must be separate')
    assert not (client/'nested').exists()

    plan = base/'pregen'
    module.pregenerate('minecraft:overworld', -16, 32, 128, 'circle', plan)
    commands = (plan/'pregen-console.txt').read_text().splitlines()
    assert commands == ['chunky world minecraft:overworld', 'chunky shape circle',
                        'chunky center -16 32', 'chunky radius 128', 'chunky selection', 'chunky start']
    assert json.loads((plan/'pregen-plan.json').read_text())['bounding_square_chunk_upper_bound'] == 289
    assert all(line.startswith('/') for line in (plan/'pregen-ingame.txt').read_text().splitlines())
    rejected(lambda: module.pregenerate('minecraft:overworld\nstop',0,0,128,'circle',base/'bad'), 'resource ID')
    rejected(lambda: module.pregenerate('minecraft:overworld',0,0,-1,'circle',base/'bad'), 'Region must fit')
    rejected(lambda: module.pregenerate('minecraft:overworld',29999984,0,128,'circle',base/'bad'), 'Region must fit')
    rejected(lambda: module.pregenerate('minecraft:overworld',0,0,128,'circle',plan), 'Output already exists')

print('Deployment contract passed: parity, version, duplicates, links, world lock, staging, Java 17, bounded commands')
