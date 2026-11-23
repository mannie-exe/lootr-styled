"""Prepare or run one disposable native consumer. Coordinate GUI access before --run."""

import argparse
import hashlib
from itertools import islice
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request
import os
import signal
import time
from reform import dependency as reform_dependency

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ('fabric-1_21_1', 'neoforge-1_21_1', 'fabric-26_3', 'neoforge-26_3')


def digest(path, kind='sha256'):
    return hashlib.new(kind, path.read_bytes()).hexdigest()


def require_stopped(fixture):
    pid_file = fixture / '.lootr-styled-probe-pid'
    if not pid_file.exists():
        return
    try:
        pid = int(pid_file.read_text().strip())
    except ValueError as error:
        raise ValueError(f'Invalid fixture PID marker: {pid_file}; inspect it before preparing again') from error
    if pid <= 0:
        raise ValueError(f'Invalid fixture PID marker: {pid_file}')
    if os.name != 'posix':
        raise ValueError(f'Confirm the previous fixture JVM stopped and remove {pid_file} before preparing again')
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return
    except PermissionError:
        pass
    raise ValueError(f'Fixture process {pid} may still be running; stop it before preparing {fixture}')


def prepare(target, mode, fetch=False, scene=False, local_profile=None, expected_private_sprites=None, reform_repository=None, gui_scale=2, graphics_only=False, preview_host=False):
    if sum((graphics_only, preview_host, scene)) > 1:
        raise ValueError('Choose one check: --graphics-only, --preview-host, or --scene')
    if expected_private_sprites is not None and (local_profile is None or not 0 <= expected_private_sprites <= 128):
        raise ValueError('An explicit private-sprite count requires a local profile and range 0..128')
    library = reform_dependency(target, reform_repository)
    game = '26.3' if target.endswith('26_3') else '1.21.1'
    loader = 'fabric' if target.startswith('fabric') else 'neoforge'
    version = next(line.split('=', 1)[1].strip() for line in (ROOT / 'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
    product = ROOT / f'build/distributions/lootr-styled-{game}-{loader}-{version}.jar'
    if not product.is_file():
        raise ValueError('Build the four product distributions first')
    fixture = ROOT / f'verification/{target}/runs/{mode}{"-profile" if local_profile else ""}{"-scene" if scene else ""}'
    require_stopped(fixture)
    if fixture.is_symlink() or any(parent.is_symlink() for parent in fixture.parents if parent != ROOT.parent):
        raise ValueError('Fixture paths must not contain symbolic links')
    if local_profile:
        if local_profile.is_symlink() or not local_profile.is_dir():
            raise ValueError('Local fixture profile must be a regular directory')
        if fixture in local_profile.resolve().parents:
            raise ValueError('Local profile source must be outside the destination fixture')
        paths = list(islice(local_profile.rglob('*'), 513))
        files = [p for p in paths if p.is_file()]
        if len(paths) > 512 or len(files) > 256 or any(p.is_symlink() for p in paths) or any(p.stat().st_size > 2097152 for p in files) or sum(p.stat().st_size for p in files) > 16777216:
            raise ValueError('Local fixture profile exceeds file/byte policy or contains a symbolic link')
    marker = fixture / '.lootr-styled-fixture'
    if fixture.exists() and not marker.is_file():
        raise ValueError('Refusing a pre-existing directory without this fixture owner marker')
    fixture.mkdir(parents=True, exist_ok=True)
    marker.write_text('lootr-styled-native-verification-v1\n')
    for previous in ('lootr-styled-probe-report.json', 'lootr-styled-probe-state.txt', '.lootr-styled-probe-pid'):
        (fixture / previous).unlink(missing_ok=True)
    configuration = fixture / 'config/lootr_styled'
    configuration.mkdir(parents=True, exist_ok=True)
    selected = 'lootr_styled_local:fixture' if local_profile else None
    (configuration / 'settings.json').write_text(json.dumps({'format': 1, 'enabled': True, 'models': selected or 'lootr_styled:default', 'artwork': selected or 'lootr_styled:default', 'freshObjects': 'OFF' if mode == 'off' else 'AUTO'}, indent=2) + '\n')
    if local_profile:
        destination = configuration / 'fixture'
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(local_profile, destination)
        (fixture / '.lootr-styled-local-profile').write_text('Bounded private profile fixture\n')
    sprite_expectation = fixture / '.lootr-styled-expected-private-sprites'
    if expected_private_sprites is None:
        sprite_expectation.unlink(missing_ok=True)
    else:
        sprite_expectation.write_text(str(expected_private_sprites))
    (fixture / '.lootr-styled-expected-sha256').write_text(digest(product))
    (fixture / '.lootr-styled-expected-reform-sha256').write_text(library['sha256'])
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
    options = {'resourcePacks': json.dumps(packs), 'incompatibleResourcePacks': '[]', 'onboardAccessibility': 'false', 'renderDistance': '4', 'simulationDistance': '5', 'maxFps': '30', 'guiScale': str(gui_scale), 'fullscreen': 'false', 'pauseOnLostFocus': 'false'}
    (fixture / 'options.txt').write_text(''.join(f'{key}:{value}\n' for key, value in options.items()))
    (fixture / 'lootr-styled-probe-mode.txt').write_text(mode + '\n')
    host_marker = fixture / '.lootr-styled-host-probe'
    if preview_host:
        host_marker.write_text('Actual retained provider/worker host; test-mounted widget, not final production layout\n')
    else:
        host_marker.unlink(missing_ok=True)
    graphics_marker = fixture / '.lootr-styled-graphic-probe'
    if graphics_only:
        graphics_marker.write_text('Isolated domain draw only; excludes editor and integrated scene qualification\n')
    else:
        graphics_marker.unlink(missing_ok=True)
    scene_marker = fixture / '.lootr-styled-scene'
    if scene:
        scene_marker.write_text('Owned native visual scene, not a personal world\n')
    else:
        scene_marker.unlink(missing_ok=True)
    command = [str(ROOT / 'gradlew'), '--no-daemon', '-p', str(ROOT / 'verification'), f':{target}:runClient', f'-PprobeTarget={target}', f'-PstyledVersion={version}', f'-PstyledSha256={digest(product)}', f'-PprobeMode={mode}']
    if 'repository' in library:
        command.append(f'-Preform_repository={library["repository"]}')
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
    parser.add_argument('--preview-host', action='store_true', help='Qualify the actual retained provider/worker host with a test-mounted shared widget')
    parser.add_argument('--graphics-only', action='store_true', help='Only draw frozen domain meshes in the shared viewport; excludes editor and world checks')
    parser.add_argument('--scene', action='store_true', help='Create and render a disposable native container world')
    parser.add_argument('--gui-scale', type=int, choices=range(5), default=2, help='Native GUI scale for this disposable fixture (0 is Auto)')
    parser.add_argument('--local-profile', type=Path, help='Copy a bounded test-owned profile into the disposable client')
    parser.add_argument('--expected-private-sprites', type=int, help='Require an exact local-fixture private sprite count (0..128); default retains the existing minimum-six control')
    parser.add_argument('--reform-repository', type=Path, help='Staged Maven repository for the pinned unpublished re:form candidate')
    args = parser.parse_args()
    fixture, product, command = prepare(args.target, args.mode, args.fetch, args.scene, args.local_profile, args.expected_private_sprites, args.reform_repository, args.gui_scale, args.graphics_only, args.preview_host)
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
    if (result != 0 or data is None or data.get('passed') is not True
            or data.get('artifactSha256') != digest(product)
            or ('isolatedGraphic' in data) != args.graphics_only
            or ('sharedGraphicHost' in data) != args.preview_host):
        raise RuntimeError(f'Client did not pass; inspect {fixture.relative_to(ROOT)}/launcher.log and report')
    print(json.dumps({'target': args.target, 'mode': args.mode, 'scope': 'preview-host' if args.preview_host else 'graphics-only' if args.graphics_only else 'settings-and-resources', 'artifactSha256': digest(product), 'snapshots': len(data['snapshots']), 'report': str(report.relative_to(ROOT))}))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(f'Native fixture failed: {error}', file=sys.stderr)
        sys.exit(1)
