#!/usr/bin/env python3
"""Select CI against actual full-suite evidence; missing evidence means full CI."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.parse
import urllib.request

CONTRACTS = (
    'vulkan-smoke-fixture-contract.sh', 'prebenchmark-audit-contract.py',
    'legacy-framebuffer-contract.py', 'terrain-population-contract.py',
    'benchmark-hud-contract.py', 'gpu-profiler-contract.py',
    'texture-upload-gpu-contract.py', 'attribution-capture-contract.py',
    'particle-attribution-contract.py', 'compilation-cache-contract.py',
    'pipeline-variant-contract.py', 'animation-oracle-contract.py',
    'animation-visibility-contract.py', 'chunk-budget-contract.py',
    'deployment-contract.py', 'render-scale-contract.py',
    'instance-input-contract.py', 'flywheel-ownership-contract.py',
    'ci-planner-contract.py',
)
OFFLINE_PATHS = {'scripts/ci/' + name for name in CONTRACTS} | {
    'scripts/performance/deployment.py',
    'scripts/performance/parity-policy.example.json',
}
NATIVE_STEPS = {
    'Build and verify distributable',
    'Record pinned Forge animation source evidence',
    'Validate packaged Immersive Portals mixin anchors',
    'Upload smoke-test logs',
    'Upload distributable JAR',
    'Smoke-test Forge client Vulkan startup',
    'Smoke-test Forge client Vulkan startup without early splash',
    'Smoke-test persistent GPU indirect shadow commands',
    'Smoke-test vanilla Vulkan post-chain execution',
    'Smoke-test vanilla Vulkan depth post-chain execution',
    'Smoke-test Vulkan screenshot readback',
    'Smoke-test Crash Assistant 1.9.7 compatibility',
}
COMPAT_STEPS = {
    'Smoke-test Create Chronicles compatibility',
    'Smoke-test Create Chronicles compatibility with real resource packs',
}
CONTROL_PATHS = {
    'scripts/ci/ci_scope.py', 'scripts/ci/run-contracts.py',
    'scripts/ci/ci-planner-contract.py',
}


def documentation(path):
    return path.endswith('.md') or path.startswith('docs/')


def select_scope(paths, *, baseline=False, quick=False):
    """An allowlist, not inferred Java dependencies. Unknown paths require native CI."""
    paths = [p for p in paths if not documentation(p)]
    if not baseline:
        return 'full', 'No usable successful full-suite ancestor; full validation required.'
    if any(p.startswith('.github/') or p in CONTROL_PATHS for p in paths):
        return 'full', 'CI control changes require the full suite.'
    if quick:
        return 'build', 'Explicit CI-Scope: quick; native validation deferred, no qualified JAR.'
    if not paths:
        return 'none', 'Only documentation differs from the qualified ancestor.'
    if all(p in OFFLINE_PATHS for p in paths):
        return 'contracts', 'Only allowlisted offline tooling/contracts differ from the qualified ancestor.'
    if all(p in OFFLINE_PATHS or p.startswith('src/test/') or
           p == 'scripts/ci/agent-check.sh' for p in paths):
        return 'build', 'Only tests/local-check tooling differ; Forge packaging and CPU checks required.'
    return 'full', 'Runtime, build, native fixtures or unknown files differ from the qualified ancestor.'


def fully_validated(run, jobs):
    if (run.get('conclusion') != 'success' or run.get('status') != 'completed' or
            run.get('event') != 'push' or run.get('head_branch') != 'forge-1.20.1' or
            run.get('path') != '.github/workflows/build.yml'):
        return False
    for job in jobs:
        if job.get('conclusion') != 'success':
            continue
        passed = {s['name'] for s in job.get('steps', [])
                  if s.get('status') == 'completed' and s.get('conclusion') == 'success'}
        if NATIVE_STEPS <= passed and COMPAT_STEPS & passed:
            return True
    return False


def git(*args):
    return subprocess.check_output(['git', *args], timeout=30)


def changed_paths(base, head):
    # Include BOTH sides of a rename: deleting a runtime file must never look tool-only.
    if not re.fullmatch(r'[0-9a-f]{40}', base) or not re.fullmatch(r'[0-9a-f]{40}', head):
        raise ValueError('Expected full Git object IDs')
    subprocess.run(['git', 'merge-base', '--is-ancestor', base, head], check=True, timeout=30)
    return [p for p in git('diff', '--name-only', '--no-renames', '-z', base, head).decode().split('\0') if p]


def api(path):
    # Use GitHub's API, not a host/URL supplied by a PR or event payload.
    req = urllib.request.Request('https://api.github.com/' + path, headers={
        'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'VulkanMod-CI-planner',
    })
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)


def find_baseline(repo, head):
    if not re.fullmatch(r'[\w.-]+/[\w.-]+', repo):
        raise ValueError('Invalid repository name')
    # Accommodate extended sequences of successful quick/tooling pushes without
    # forcing a costly full suite solely because six partial runs accumulated.
    query = urllib.parse.urlencode({'branch': 'forge-1.20.1', 'event': 'push',
                                   'status': 'success', 'per_page': 30})
    runs = api(f'repos/{repo}/actions/workflows/build.yml/runs?{query}')['workflow_runs']
    for run in runs:
        # A green contracts-only or quick run is NOT a native baseline.
        jobs = api(f"repos/{repo}/actions/runs/{int(run['id'])}/jobs?per_page=100")['jobs']
        if fully_validated(run, jobs):
            changed_paths(run['head_sha'], head)  # Reject rewritten/unrelated history.
            return run['head_sha'], run['id']
    return '', None  # Bounded lookup exhausted: safely spend more CI instead of guessing.


def main():
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    kind, head = os.environ['GITHUB_EVENT_NAME'], os.environ['GITHUB_SHA']
    baseline, baseline_run, paths = '', None, []
    scope, reason = 'full', 'Pull requests require the complete merge-result suite.'
    if kind == 'push':
        try:
            baseline, baseline_run = find_baseline(os.environ['GITHUB_REPOSITORY'], head)
            if baseline:
                paths = [p for p in changed_paths(baseline, head) if p]
            # Inspect the actual commit, not a possibly truncated webhook commit list.
            message = git('show', '-s', '--format=%B', head).decode()
            quick = bool(re.search(r'^CI-Scope: quick\s*$', message, re.MULTILINE))
            scope, reason = select_scope(paths, baseline=bool(baseline), quick=quick)
        except (KeyError, TypeError, ValueError, OSError, subprocess.SubprocessError) as error:
            scope, reason = 'full', f'Baseline lookup unavailable ({type(error).__name__}); full validation required.'
    elif kind == 'pull_request' and event['pull_request'].get('draft'):
        scope, reason = 'build', 'Draft PR: compile/CPU checks only; ready_for_review runs the full suite.'

    # Coalesce bursts on a 1-CPU planner, before allocating the 4-CPU build runner.
    # Workflow concurrency cancels superseded plans. Documentation pushes do not
    # cancel required executable validation, and PR merge results are never reused.
    delay = 0
    if kind == 'push' and scope == 'full':
        try:
            delay = min(120, max(0, int(os.environ.get('CI_SETTLE_SECONDS', '45'))))
        except ValueError:
            delay = 45
    report = dict(sha=head, event=kind, scope=scope, reason=reason, baseline=baseline,
                  baseline_run=baseline_run, changed_paths=paths, settle_seconds=delay,
                  native=scope == 'full', gradle=scope in ('full', 'build'))
    print(json.dumps(report, indent=2), flush=True)
    summary = ('## CI validation scope\n\n' +
               f"- Scope: **{scope}** for `{head}`\n- {reason}\n" +
               f"- Full-suite baseline: `{baseline or 'unavailable'}` / run `{baseline_run}`\n" +
               f"- Burst coalescing: {delay}s; cancelled plans do not start the build runner.\n" +
               '- Only a passing full native suite can publish a qualified JAR.\n')
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
        out.write(summary)
    time.sleep(delay)
    with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
        for key in ('scope', 'baseline', 'native', 'gradle'):
            value = str(report[key]).lower() if isinstance(report[key], bool) else report[key]
            out.write(f'{key}={value}\n')


if __name__ == '__main__':
    main()
