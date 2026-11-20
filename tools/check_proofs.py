"""Check explicit model laws through Bend and its separate Lean-backed kernel."""

import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    pin = json.loads((ROOT / 'proofs/toolchain.json').read_text())
    host = platform.system().lower() + ('_aarch64' if platform.machine() in ('arm64', 'aarch64') else '')
    lean = ROOT / f'.tools/lean-{pin["lean"]["version"]}-{host}/bin'
    bend = ROOT / '.tools/bend/bin/bend'
    env = dict(os.environ, BEND_NO_TELEMETRY='1', PATH=str(lean) + os.pathsep + os.environ.get('PATH', ''))
    version = subprocess.check_output([str(bend), 'version'], env=env, text=True).strip()
    if version != f'bend {pin["bend"]}':
        raise ValueError('Run tools/setup_proofs.py for the pinned toolchain')
    lean_version = subprocess.check_output([str(lean / 'lean'), '--version'], env=env, text=True).strip()
    if not lean_version.startswith(f'Lean (version {pin["lean"]["version"]},'):
        raise ValueError('Unexpected Lean version')
    checked = subprocess.check_output([str(bend), 'proofs/PROOF.bend', '--verdict'], cwd=ROOT, env=env, text=True)
    if 'ALL PROOFS CHECK' not in checked or 'SOME PROOFS FAIL' in checked:
        raise ValueError('The separate kernel did not accept every stated model law')
    subprocess.run([str(lean / 'lean'), 'proofs/Bounds.lean'], cwd=ROOT, env=env, check=True)
    destination = ROOT / 'build/proofs'
    destination.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(bend), 'proofs/MODEL.bend', '-o', str(destination / 'model.mjs')], cwd=ROOT, env=env, check=True)
    # Same laws, one deliberately broken branch. This must be a real refusal.
    negative = destination / 'refusal'
    negative.mkdir(exist_ok=True)
    for name in ('MODEL.bend', 'LAWS.bend', 'PROOF.bend'):
        shutil.copyfile(ROOT / 'proofs' / name, negative / name)
    model = (negative / 'MODEL.bend').read_text()
    branch = 'case True{}:\n      2n'
    if model.count(branch) != 1:
        raise ValueError('Mutation no longer identifies the explicit-resource branch')
    (negative / 'MODEL.bend').write_text(model.replace(branch, 'case True{}:\n      4n'))
    refused = subprocess.run([str(bend), str(negative / 'PROOF.bend'), '--verdict'], cwd=ROOT, env=env, text=True, capture_output=True)
    if refused.returncode == 0 or 'SOME PROOFS FAIL' not in refused.stdout + refused.stderr:
        raise ValueError('Separate-kernel mutation control did not refuse the broken precedence')
    files = [ROOT / 'proofs' / name for name in ('MODEL.bend', 'LAWS.bend', 'PROOF.bend', 'Bounds.lean', 'toolchain.json')]
    receipt = {'bend': version, 'lean': lean_version, 'verdict': checked.strip(),
               'sources': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
               'precedenceMutationRefused': True,
               'scope': 'Decision model and natural-number bounds, not translated Java or native rendering'}
    (destination / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(checked.strip())


if __name__ == '__main__':
    main()
