"""Prepare or run one disposable native consumer. Coordinate GUI access before --run."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import os
import signal
import time

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ('fabric-1_21_1', 'neoforge-1_21_1', 'fabric-26_3', 'neoforge-26_3')


def digest(path, kind='sha256'):
    return hashlib.new(kind, path.read_bytes()).hexdigest()


def prepare(target, mode, fetch=False, scene=False, local_profile=None):
    game = '26.3' if target.endswith('26_3') else '1.21.1'
    loader = 'fabric' if target.startswith('fabric') else 'neoforge'
    version = next(line.split('=', 1)[1].strip() for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    product = ROOT / f'build/distributions/lootr-styled-{game}-{loader}-{version}.jar'
    if not product.is_file():
        raise ValueError('Build the four product distributions first')
    fixture = ROOT / f'verification/{target}/runs/{mode}{"-profile" if local_profile else ""}{"-scene" if scene else ""}'
    marker = fixture / '.lootr-styled-fixture'
    if fixture.exists() and not marker.is_file():
        raise ValueError('Refusing a pre-existing directory without this fixture owner marker')
    fixture.mkdir(parents=True, exist_ok=True)
    marker.write_text('lootr-styled-native-verification-v1\n')
    configuration = fixture / 'config/lootr_styled'
    configuration.mkdir(parents=True, exist_ok=True)
    selected = 'lootr_styled_local:fixture' if local_profile else None
    (configuration / 'settings.json').write_text(json.dumps({'format': 1, 'enabled': True, 'models': selected or 'lootr_styled:default', 'artwork': selected or 'lootr_styled:vanilla', 'freshObjects': 'OFF' if mode == 'off' else 'AUTO'}, indent=2) + '\n')
    if local_profile:
        files = [p for p in local_profile.rglob('*') if p.is_file()]
        if len(files) > 256 or any(p.is_symlink() or p.stat().st_size > 2097152 for p in files) or sum(p.stat().st_size for p in files) > 16777216:
            raise ValueError('Local fixture profile exceeds file/byte policy')
        destination = configuration / 'fixture'
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(local_profile, destination)
        (fixture / '.lootr-styled-local-profile').write_text('Bounded private profile fixture\n')
    (fixture / '.lootr-styled-expected-sha256').write_text(digest(product))
    packs = []
    if mode != 'native':
        manifest = json.loads((ROOT / 'verification/inputs.json').read_text())
        for entry in manifest['inputs']:
            if entry['target'] not in ('all', f'{game}-{loader}'):
                continue
            path = ROOT / 'build/inputs' / entry['file']
            if not path.is_file() and fetch:
                path.parent.mkdir(parents=True, exist_ok=True)
                request = urllib.request.Request(entry['url'], headers={'User-Agent': 'LootrStyled-native-verification/0.1'})
                with urllib.request.urlopen(request, timeout=30) as response:
                    data = response.read(entry['bytes'] + 1)
                if len(data) != entry['bytes'] or hashlib.sha512(data).hexdigest() != entry['sha512']:
                    raise ValueError('Verification download differs from its pinned identity')
                path.write_bytes(data)
            if not path.is_file() or path.stat().st_size != entry['bytes'] or digest(path, 'sha512') != entry['sha512']:
                raise ValueError(f'Missing or changed verification input: {entry["file"]}; use --fetch')
            if entry['file'].endswith('.zip'):
                destination = fixture / 'resourcepacks' / entry['file']
                destination.parent.mkdir(exist_ok=True)
                shutil.copyfile(path, destination)
                packs.append(f'file/{entry["file"]}')
    options = {'resourcePacks': json.dumps(packs), 'incompatibleResourcePacks': '[]', 'onboardAccessibility': 'false', 'renderDistance': '4', 'simulationDistance': '5', 'maxFps': '30', 'guiScale': '2', 'fullscreen': 'false', 'pauseOnLostFocus': 'false'}
    (fixture / 'options.txt').write_text(''.join(f'{key}:{value}\n' for key, value in options.items()))
    (fixture / 'lootr-styled-probe-mode.txt').write_text(mode + '\n')
    scene_marker = fixture / '.lootr-styled-scene'
    if scene:
        scene_marker.write_text('Owned native visual scene, not a personal world\n')
    else:
        scene_marker.unlink(missing_ok=True)
    report = fixture / 'lootr-styled-probe-report.json'
    report.unlink(missing_ok=True)
    (fixture / '.lootr-styled-probe-pid').unlink(missing_ok=True)
    command = [str(ROOT / 'gradlew'), '--no-daemon', '-p', str(ROOT / 'verification'), f':{target}:runClient', f'-PstyledVersion={version}', f'-PstyledSha256={digest(product)}', f'-PprobeMode={mode}']
    if scene:
        command.append('-Pscene=true')
    if local_profile:
        command.append('-PprofileProbe=true')
    return fixture, product, command


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=TARGETS, required=True)
    parser.add_argument('--mode', choices=('native', 'fresh', 'off'), default='native')
    parser.add_argument('--fetch', action='store_true', help='Fetch missing pinned verification inputs, never product runtime content')
    parser.add_argument('--run', action='store_true', help='Launch a GUI after external desktop coordination')
    parser.add_argument('--scene', action='store_true', help='Create and render a disposable native container world')
    parser.add_argument('--local-profile', type=Path, help='Copy a bounded test-owned profile into the disposable client')
    args = parser.parse_args()
    fixture, product, command = prepare(args.target, args.mode, args.fetch, args.scene, args.local_profile)
    if not args.run:
        print(json.dumps({'prepared': str(fixture.relative_to(ROOT)), 'artifact': product.name, 'sha256': digest(product)}))
        return
    with (fixture / 'launcher.log').open('w') as output:
        process = subprocess.Popen(command, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        try:
            result = process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            pid_file = fixture / '.lootr-styled-probe-pid'
            if pid_file.is_file():
                pid = int(pid_file.read_text().strip())
                command_name = subprocess.run(['ps', '-p', str(pid), '-o', 'comm='], text=True, capture_output=True).stdout.strip()
                custody = subprocess.run(['lsof', '-a', '-p', str(pid), '-d', 'cwd', '-Fn'], text=True, capture_output=True).stdout.splitlines()
                if command_name.endswith('/java') and f'n{fixture}' in custody:
                    os.kill(pid, signal.SIGTERM)
                    for _ in range(100):
                        if subprocess.run(['ps', '-p', str(pid)], stdout=subprocess.DEVNULL).returncode != 0:
                            break
                        time.sleep(.1)
            process.terminate()
            process.wait(timeout=15)
            raise RuntimeError('Native fixture timed out; inspect its owned JVM before retrying')
    report = fixture / 'lootr-styled-probe-report.json'
    data = json.loads(report.read_text()) if report.is_file() else None
    if result != 0 or data is None or data.get('passed') is not True:
        raise RuntimeError(f'Native fixture failed; inspect {fixture.relative_to(ROOT)}/launcher.log and report')
    print(json.dumps({'target': args.target, 'mode': args.mode, 'artifactSha256': digest(product), 'snapshots': len(data['snapshots']), 'report': str(report.relative_to(ROOT))}))


if __name__ == '__main__':
    main()
