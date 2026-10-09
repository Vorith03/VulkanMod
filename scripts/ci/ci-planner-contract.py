#!/usr/bin/env python3
"""Adversarial CI selection, baseline, process cleanup and publication contracts."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import ci_scope as scope

spec = importlib.util.spec_from_file_location('contract_runner', Path(__file__).with_name('run-contracts.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class PlannerContract(unittest.TestCase):
    def assert_terminated(self, pid):
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            try:
                state = Path(f'/proc/{pid}/stat').read_text().split()[2]
            except FileNotFoundError:
                return
            if state == 'Z':
                return
            time.sleep(0.02)
        self.fail(f'Child process {pid} survived termination')

    def test_scope_and_deferred_runtime(self):
        self.assertEqual(scope.select_scope(['README.md'], baseline=False)[0], 'full')
        self.assertEqual(scope.select_scope([], baseline=True)[0], 'none')
        self.assertEqual(scope.select_scope(['docs/design.md'], baseline=True)[0], 'none')
        self.assertEqual(scope.select_scope(['scripts/ci/deployment-contract.py'], baseline=True)[0], 'contracts')
        self.assertEqual(scope.select_scope(['src/test/java/Regression.java'], baseline=True)[0], 'build')
        for path in ['src/main/java/Renderer.java', 'src/main/resources/shader.frag', 'gradle.properties',
                     'gradlew', 'new-unknown-file', 'scripts/ci/vulkan-smoke.sh',
                     'scripts/ci/create-chronicles-compat-smoke.sh', 'scripts/ci/resource-pack-retention.py']:
            self.assertEqual(scope.select_scope([path], baseline=True)[0], 'full', path)
        self.assertEqual(scope.select_scope(['src/main/java/Renderer.java'], baseline=True, quick=True)[0], 'build')
        for path in scope.CONTROL_PATHS | {'.github/workflows/build.yml'}:
            self.assertEqual(scope.select_scope([path], baseline=True, quick=True)[0], 'full', path)
        # The prior push was a deferred/failed renderer edit. Comparing the whole
        # range from actual native evidence retains it, even if THIS push is tooling.
        self.assertEqual(scope.select_scope(['src/main/java/Renderer.java',
                        'scripts/ci/deployment-contract.py'], baseline=True)[0], 'full')

    def test_full_baseline_rejects_partial_and_missing_gates(self):
        run = dict(conclusion='success', status='completed', event='push',
                   head_branch='forge-1.20.1', path='.github/workflows/build.yml')
        steps = [dict(name=name, status='completed', conclusion='success')
                 for name in sorted(scope.NATIVE_STEPS | {next(iter(scope.COMPAT_STEPS))})]
        job = dict(conclusion='success', steps=steps)
        self.assertTrue(scope.fully_validated(run, [job]))
        for step in steps:
            for conclusion in ('failed', 'skipped', 'cancelled'):
                original = step['conclusion']; step['conclusion'] = conclusion
                self.assertFalse(scope.fully_validated(run, [job]), step['name'])
                step['conclusion'] = original
        self.assertFalse(scope.fully_validated(run, [dict(conclusion='success', steps=[])]))
        for key, value in [('conclusion', 'failure'), ('event', 'pull_request'),
                           ('head_branch', 'dev'), ('path', '.github/workflows/other.yml')]:
            self.assertFalse(scope.fully_validated(dict(run, **{key: value}), [job]))

    def test_git_range_handles_renames_and_nonancestor(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', '-C', directory, *args]).decode().strip()
            git('init', '-q'); git('config', 'user.email', 'ci@example.invalid'); git('config', 'user.name', 'CI')
            source = root/'src/main/java/Renderer.java'; source.parent.mkdir(parents=True)
            source.write_text('class Renderer {}\n')
            git('add', '.'); git('commit', '-qm', 'baseline'); base = git('rev-parse', 'HEAD')
            (root/'docs').mkdir(); git('mv', 'src/main/java/Renderer.java', 'docs/renderer.md')
            git('commit', '-qm', 'rename'); head = git('rev-parse', 'HEAD')
            old = Path.cwd()
            try:
                os.chdir(root)
                paths = scope.changed_paths(base, head)
                self.assertIn('src/main/java/Renderer.java', paths)
                self.assertEqual(scope.select_scope(paths, baseline=True)[0], 'full')
                self.assertEqual(scope.changed_paths(head, head), [])
                with self.assertRaises(subprocess.CalledProcessError): scope.changed_paths(head, base)
                with self.assertRaises(ValueError): scope.changed_paths('--help', head)
            finally:
                os.chdir(old)

    def test_bounded_lookup_and_partial_run_rejection(self):
        full = dict(conclusion='success', status='completed', event='push', head_branch='forge-1.20.1',
                    path='.github/workflows/build.yml', head_sha='a'*40, id=2)
        partial = dict(full, id=3, head_sha='b'*40)
        steps = [dict(name=name, status='completed', conclusion='success')
                 for name in scope.NATIVE_STEPS | {next(iter(scope.COMPAT_STEPS))}]
        def api(path):
            if '/workflows/' in path: return {'workflow_runs': [partial, full]}
            return {'jobs': [dict(conclusion='success', steps=steps if '/runs/2/' in path else [])]}
        with patch.object(scope, 'api', side_effect=api), patch.object(scope, 'changed_paths', return_value=[]):
            self.assertEqual(scope.find_baseline('owner/repo', 'c'*40), ('a'*40, 2))
        with patch.object(scope, 'api', return_value={'workflow_runs': []}):
            self.assertEqual(scope.find_baseline('owner/repo', 'c'*40), ('', None))

    def test_api_failure_and_draft_scope_are_explicit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); event = root/'event.json'
            event.write_text('{}')
            env = dict(GITHUB_EVENT_PATH=str(event), GITHUB_EVENT_NAME='push',
                       GITHUB_REPOSITORY='owner/repo', GITHUB_SHA='c'*40,
                       GITHUB_OUTPUT=str(root/'outputs'), GITHUB_STEP_SUMMARY=str(root/'summary'),
                       CI_SETTLE_SECONDS='0')
            with patch.dict(os.environ, env), patch.object(scope, 'find_baseline', side_effect=OSError), \
                    patch.object(scope.time, 'sleep'):
                scope.main()
            self.assertIn('scope=full', (root/'outputs').read_text())
            self.assertIn('native=true', (root/'outputs').read_text())
            event.write_text('{"pull_request":{"draft":true}}')
            env['GITHUB_EVENT_NAME'] = 'pull_request'
            (root/'outputs').write_text('')
            with patch.dict(os.environ, env), patch.object(scope.time, 'sleep'):
                scope.main()
            self.assertIn('scope=build', (root/'outputs').read_text())
            self.assertIn('native=false', (root/'outputs').read_text())

    def test_workflow_retains_all_gates_and_gates_publication(self):
        text = (runner.ROOT/'.github/workflows/build.yml').read_text()
        blocks = {}
        for block in text.split('      - name: ')[1:]:
            blocks[block.splitlines()[0]] = block
        for name in scope.NATIVE_STEPS | scope.COMPAT_STEPS:
            self.assertIn(name, blocks)
            if name != 'Build and verify distributable':
                self.assertIn("needs.plan.outputs.native == 'true'", blocks[name], name)
        self.assertIn("needs.plan.outputs.gradle == 'true'", blocks['Build and verify distributable'])
        publication = blocks['Upload distributable JAR']
        self.assertIn("success() && needs.plan.outputs.native == 'true'", publication)
        self.assertIn("steps.package.outcome == 'success'", publication)
        self.assertNotIn('always()', publication)
        self.assertIn('if-no-files-found: error', publication)
        self.assertIn('ready_for_review', text)
        self.assertIn('branches: [forge-1.20.1]', text.split('pull_request:')[1])
        for name in scope.CONTRACTS:
            self.assertTrue((runner.ROOT/'scripts/ci'/name).is_file(), name)
        self.assertEqual(len(scope.CONTRACTS), len(set(scope.CONTRACTS)))

    def test_contract_runner_surfaces_failures(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root/'scripts/ci').mkdir(parents=True)
            (root/'scripts/ci/pass.py').write_text('print("test passed")\n')
            (root/'scripts/ci/fail.py').write_text('raise SystemExit(7)\n')
            with patch.object(runner, 'ROOT', root):
                self.assertEqual(runner.run(('pass.py',), workers=1), 0)
                self.assertEqual(runner.run(('fail.py', 'pass.py'), workers=1), 1)
                report = json.loads((root/'.ci-results/contracts/results.json').read_text())
                self.assertEqual(report['fail.py']['exit_code'], 7)
                self.assertEqual(runner.run(('missing.py',), workers=1), 1)

    def test_timeout_terminates_child_process_group(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory)/'timeout.log'
            code = ('import subprocess,sys,time; '
                    'p=subprocess.Popen([sys.executable,"-c","import time;time.sleep(60)"]); '
                    'print(p.pid,flush=True);time.sleep(60)')
            result = runner.execute([sys.executable, '-c', code], log, timeout=0.5)
            self.assertEqual(result['exit_code'], 124)
            pid = int(log.read_text().splitlines()[0])
            self.assert_terminated(pid)

    def test_cancel_terminates_child_process_group(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root/'scripts/ci').mkdir(parents=True)
            pidfile = root/'pid'
            (root/'scripts/ci/hang.py').write_text(
                'import subprocess,sys,time\nfrom pathlib import Path\n'
                'p=subprocess.Popen([sys.executable,"-c","import time;time.sleep(60)"])\n'
                f'Path({str(pidfile)!r}).write_text(str(p.pid))\ntime.sleep(60)\n')
            code = ('import importlib.util,sys,signal\n'
                    f'sys.path.insert(0,{str(Path(__file__).resolve().parent)!r})\n'
                    f's=importlib.util.spec_from_file_location("runner",{str(Path(runner.__file__).resolve())!r})\n'
                    'm=importlib.util.module_from_spec(s);s.loader.exec_module(m)\n'
                    f'm.ROOT=m.Path({str(root)!r})\n'
                    'signal.signal(signal.SIGTERM,m.interrupt)\n'
                    'sys.exit(m.run(("hang.py",),workers=1))\n')
            process = subprocess.Popen([sys.executable, '-c', code], stdout=subprocess.DEVNULL)
            try:
                deadline = time.monotonic() + 5
                while not pidfile.exists() and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue(pidfile.exists(), 'Runner fixture did not start')
                process.terminate()
                self.assertEqual(process.wait(timeout=5), 143)
                self.assert_terminated(int(pidfile.read_text()))
            finally:
                if process.poll() is None:
                    process.kill(); process.wait()


if __name__ == '__main__':
    unittest.main()
