#!/usr/bin/env python3
"""Bounded parallel execution of independent, temporary-directory CPU contracts."""
from concurrent.futures import ThreadPoolExecutor, as_completed
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import threading
import time

from ci_scope import CONTRACTS

ROOT = Path(__file__).resolve().parents[2]
ACTIVE = set()
LOCK = threading.Lock()
STOP = threading.Event()


def terminate(process):
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass


def interrupt(signum, _frame):
    STOP.set()
    with LOCK:
        for process in ACTIVE:
            terminate(process)
    raise SystemExit(128 + signum)


def execute(command, log, timeout=120):
    start = time.monotonic()
    with log.open('w') as out:
        # Synchronize cancellation with spawn: no new javac/java process may escape
        # into a persistent runner after the workflow has been cancelled.
        with LOCK:
            if STOP.is_set():
                return {'exit_code': 130, 'seconds': 0}
            process = subprocess.Popen(command, cwd=ROOT, stdout=out, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            ACTIVE.add(process)
        try:
            try:
                code = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                terminate(process)
                process.wait()
                out.write(f'\nContract exceeded {timeout}s; terminated its process group.\n')
                code = 124
        finally:
            with LOCK:
                ACTIVE.discard(process)
    return {'exit_code': code, 'seconds': round(time.monotonic() - start, 3)}


def run(contracts=CONTRACTS, workers=4, directory=None):
    directory = directory or ROOT / '.ci-results/contracts'
    directory.mkdir(parents=True, exist_ok=True)
    results = {}
    with ThreadPoolExecutor(max_workers=max(1, min(4, workers))) as pool:
        pending = {}
        for name in contracts:
            path = ROOT / 'scripts/ci' / name
            command = ['bash' if name.endswith('.sh') else sys.executable, str(path)]
            pending[pool.submit(execute, command, directory / (name + '.log'))] = name
        failed = False
        for future in as_completed(pending):
            name = pending[future]
            if future.cancelled():
                results[name] = {'status': 'cancelled'}
                continue
            try:
                result = future.result()
            except Exception as error:
                result = {'exit_code': 1, 'error': f'{type(error).__name__}: {error}'}
            result['status'] = 'passed' if result['exit_code'] == 0 else 'failed'
            results[name] = result
            print(f"{result['status'].upper()}: {name} ({result.get('seconds', '?')}s)", flush=True)
            if result['status'] == 'failed':
                failed = True
                for queued in pending:
                    queued.cancel()  # Let at most four already-started contracts finish.
                log = directory / (name + '.log')
                if log.exists():
                    print(''.join(log.read_text(errors='replace').splitlines(keepends=True)[-100:]))
        ordered = {name: results[name] for name in contracts}
        (directory / 'results.json').write_text(json.dumps(ordered, indent=2) + '\n')
    return 1 if failed else 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workers', type=int, choices=range(1, 5),
                        default=min(4, os.cpu_count() or 1))
    args = parser.parse_args()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, interrupt)
    raise SystemExit(run(workers=args.workers))
