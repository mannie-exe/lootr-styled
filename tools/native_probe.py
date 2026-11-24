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
PREVIEW_SUBJECTS = tuple((c, looted, opened) for c in ('CHEST', 'TRAPPED_CHEST', 'BARREL')
                         for looted in (False, True) for opened in ((False, True) if c == 'BARREL' else (False,)))
CAPTURES = (
    'production-close-ready', 'production-close-decision',
    'production-viewport-narrow', 'production-viewport-returned',
    'reform-review',
    'reform-home', 'reform-initial-appearance', 'reform-navigation', 'reform-unmounted',
    'reform-profile-browser', 'reform-appearance', 'reform-document-actions', 'reform-manual-id',
    'reform-production-preview-idle', 'reform-production-preview-drawn',
    'scene-closed', 'scene-ordinary-open', 'scene-looted', 'barrel-closed-again', 'barrel-reopened',
    'barrel-third-person-item', 'barrel-framed-and-dropped-items', 'barrel-inventory-item',
    'barrel-neighbor-cases',
    *(f'isolated-preview-{i}' for i in range(4)),
    *(f'shared-host-{i}' for i in range(4)),
)


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


def prepare(target, mode, fetch=False, scene=False, local_profile=None, expected_private_sprites=None, reform_repository=None, gui_scale=2, graphics_only=False, preview_host=False, production_shutdown=False, production_viewport=False, production_close=False):
    if sum((graphics_only, preview_host, scene, production_shutdown, production_viewport, production_close)) > 1:
        raise ValueError('Choose one check: --graphics-only, --preview-host, --production-shutdown, --production-viewport, --production-close, or --scene')
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
    for previous in ('lootr-styled-probe-report.json', 'lootr-styled-probe-state.txt',
                     'lootr-styled-shutdown-report.json', 'lootr-styled-combinations-report.json',
                     'lootr-styled-selection-states-report.json',
                     'lootr-styled-cleanup-failure-report.json',
                     'lootr-styled-cleanup-failure-synchronous-report.json',
                     'lootr-styled-cleanup-failure-replaced-report.json',
                     '.lootr-styled-probe-pid'):
        (fixture / previous).unlink(missing_ok=True)
    for previous in ('lootr-styled-entry-controls.json', 'lootr-styled-ui-observations.json',
                     *(f'{name}.png' for name in CAPTURES)):
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
    production_marker = fixture / '.lootr-styled-production-shutdown'
    if production_shutdown:
        production_marker.write_text('Production task remains live through normal renderer shutdown\n')
    else:
        production_marker.unlink(missing_ok=True)
    viewport_marker = fixture / '.lootr-styled-production-viewport'
    if production_viewport:
        viewport_marker.write_text('Real production camera controls and native OS window resize only\n')
    else:
        viewport_marker.unlink(missing_ok=True)
    close_marker = fixture / '.lootr-styled-production-close'
    if production_close:
        close_marker.write_text('Actual dirty production preview close/keep/discard only\n')
    else:
        close_marker.unlink(missing_ok=True)
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


def require_renderer_shutdown(data, fixture, product, target):
    path = fixture / 'lootr-styled-shutdown-report.json'
    record = json.loads(path.read_text()) if path.is_file() else None
    boundary = 'RenderSystem.shutdownRenderer' if target.endswith('26_3') else 'Minecraft.close'

    def released(value):
        return (isinstance(value, dict) and value.get('passed') is True
                and value.get('releaseCompleted') is True and value.get('released') is True
                and all(type(value.get(key)) is int and value[key] == 0
                        for key in ('outstandingUses', 'nativeBytes', 'nativeResources', 'sceneBytes', 'sceneResources'))
                and 'releaseError' not in value)

    if (not isinstance(record, dict) or record != data.get('rendererShutdown')
            or data.get('shutdownPending', False) or record.get('passed') is not True
            or record.get('artifactSha256') != digest(product)
            or not data.get('libraryInputSha256')
            or record.get('libraryInputSha256') != data.get('libraryInputSha256')
            or record.get('rendererReturned') is not True
            or record.get('settingsUnchanged') is not True
            or record.get('workflowOpenAtStop') is not True
            or record.get('editorOpenAtStop') is not True
            or type(record.get('pendingUsesAtStop')) is not int or record['pendingUsesAtStop'] < 1
            or type(record.get('nativeBytesAtStop')) is not int or record['nativeBytesAtStop'] < 1
            or type(record.get('sceneBytesAtStop')) is not int or record['sceneBytesAtStop'] < 1
            or not released(record.get('atRendererReturn')) or not released(record.get('afterShutdown'))
            or record['afterShutdown'].get('workflowClosed') is not True
            or record['afterShutdown'].get('editorClosed') is not True
            or record['atRendererReturn'].get('boundary') != boundary):
        raise RuntimeError('Renderer shutdown did not pass; inspect the post-shutdown receipt and native report')


def require_production_discard(data):
    value = data.get('rendererShutdown', {}).get('productionDiscard')
    if (not isinstance(value, dict)
            or any(value.get(key) is not True for key in ('releaseCompleted', 'released',
                      'oldOwnerClosed', 'workflowRetained', 'settingsUnchanged', 'freshOwner'))
            or any(type(value.get(key)) is not int or value[key] != 0
                   for key in ('outstandingUses', 'nativeBytes', 'sceneBytes'))
            or any(type(value.get(key)) is not int or value[key] < 1
                   for key in ('pendingUsesAtDiscard', 'nativeBytesAtDiscard', 'sceneBytesAtDiscard'))):
        raise RuntimeError('Production discard did not retire its submitted preview and reopen a fresh owner')


def require_production_close(data):
    value = data.get('productionClose')
    releases = value.get('releases') if isinstance(value, dict) else None
    if (not isinstance(value, dict)
            or any(value.get(key) is not True for key in ('sameRootDecision',
                'reviewChooserKeepsProfilesContext', 'closeReviewCapturesSameDraft', 'reviewReturnRetainsOwner',
                'keepEditingRetainsDraft', 'noAutomaticPreparation', 'discardClosesOwner',
                'freshOwnerUsesSavedChoice', 'settingsUnchanged', 'resourceGenerationUnchanged'))
            or type(value.get('explicitPreparations')) is not int
            or value['explicitPreparations'] != 2
            or any(type(value.get(key)) is not int or value[key] != 0
                   for key in ('releasedNativeBytes', 'releasedSceneBytes'))
            or not isinstance(releases, list) or len(releases) != 2
            or any(not isinstance(row, dict) or row.get('released') is not True
                   or row.get('releaseFailed') is not False
                   or any(type(row.get(key)) is not int or row[key] != 0
                          for key in ('outstandingUses', 'nativeBytes', 'sceneBytes'))
                   for row in releases)):
        raise RuntimeError('Production close/review/keep/discard did not preserve its draft and release graphics')


def require_production_viewport(data):
    value = data.get('productionViewport')
    if (not isinstance(value, dict)
            or any(value.get(key) is not True for key in ('cameraControlsReuseGraphic',
                'realWindowReflowRetainsSceneAndInput', 'settingsUnchanged',
                'resourceGenerationUnchanged', 'releaseCompleted'))
            or any(type(value.get(key)) is not int or value[key] != 0
                   for key in ('releasedNativeBytes', 'releasedSceneBytes'))
            or [row.get('phase') for row in value.get('frames', [])]
                   != ['prepared', 'rotated', 'zoomed', 'reset', 'narrow', 'returned']):
        raise RuntimeError('Production camera/window checks did not complete with actual release')


def require_preview_combinations(data, fixture):
    path = fixture / 'lootr-styled-combinations-report.json'
    result = data.get('previewCombinations')
    if (not isinstance(result, dict) or result.get('complete') is not True
            or result.get('failures') != 0 or result.get('settingsUnchanged') is not True
            or not path.is_file() or json.loads(path.read_text()) != result):
        raise RuntimeError('Preview combinations did not complete; inspect the retained reports')
    ids = ('lootr_styled:default', 'lootr_styled:vanilla')
    expected = {(m, a, c, l, o) for m in ids for a in ids for c, l, o in PREVIEW_SUBJECTS}
    rows = result.get('rows', [])
    actual = {(r.get('models'), r.get('artwork'), r.get('container'), r.get('looted'), r.get('open')) for r in rows}
    if len(rows) != len(expected) or actual != expected:
        raise RuntimeError('Preview combinations are incomplete or duplicated')
    for row in rows:
        if (row.get('state') != 'READY' or row.get('drawn') is not True
                or row.get('retired') is not True or row.get('prepared', {}).get('aoTriangles') != 0):
            raise RuntimeError('Preview combinations did not draw and retire every selection')


def require_preview_authority(data):
    result = data.get('previewAuthority')
    positive = ('actualWriteDenied', 'readOnlyDomainDraw', 'permissionLossRetiresReadyLease',
                'revokedReadRequestRefused', 'permissionLossWaitsForEnteredWork',
                'revokedEnteredOutputDiscarded', 'retiredEditorExecutorCannotBlockGpuRelease',
                'testAndRuntimeSettingsUnchanged', 'oldExecutorActuallyRejects')
    if (not isinstance(result, dict) or any(result.get(key) is not True for key in positive)
            or type(result.get('pendingUsesAtEditorRetirement')) is not int
            or result['pendingUsesAtEditorRetirement'] < 1
            or result.get('unexpectedStoppedExecutorSubmissions') != 0
            or result.get('preparations') != 3
            or result.get('revokedTerminal') not in ('CANCELLED', 'REFUSED')):
        raise RuntimeError('Preview authority control did not pass; inspect the native report')


def require_preview_cleanup_failure(data):
    _require_preview_cleanup_result(data.get('previewCleanupFailure'), 1)


def _require_preview_cleanup_result(result, pending_uses, replacement=False):
    if (not isinstance(result, dict) or result.get('actualInjectedCause') is not True
            or result.get('releaseAttempted') is not True
            or result.get('releaseCompleted') is not False or result.get('released') is not False
            or result.get('injected') != 1 or result.get('outstandingUses') != 0
            or type(result.get('pendingUsesAtClose')) is not int or result['pendingUsesAtClose'] != pending_uses
            or (not replacement and result.get('nativeBytes') != 0)
            or (replacement and (type(result.get('nativeBytes')) is not int or result['nativeBytes'] <= 0))
            or type(result.get('sceneBytes')) is not int or result['sceneBytes'] <= 0
            or result.get('hostFailureCount') != 1 or result.get('hostHasCause') is not True
            or 'preview.release_failed' not in result.get('viewCodes', [])
            or result.get('settingsUnchanged') is not True):
        raise RuntimeError('Deferred preview cleanup failure was not reported truthfully')


def require_preview_cleanup_modes(data):
    for name, pending in (('previewCleanupSynchronous', 0), ('previewCleanupReplacement', 1)):
        result = data.get(name)
        if not isinstance(result, dict) or result.get('pendingUsesAtClose') != pending:
            raise RuntimeError('Preview cleanup mode is missing its actual native-use boundary')
        # The same truthful failure/cause/accounting checks apply to both close paths.
        _require_preview_cleanup_result(result, pending, name == 'previewCleanupReplacement')
        if name == 'previewCleanupSynchronous':
            if result.get('completedDuringClose') is not True or result.get('viewState') != 'CLOSED':
                raise RuntimeError('Synchronous preview cleanup did not settle exactly once while closed')
        elif (result.get('viewState') != 'READY' or result.get('finalNativeBytes') != 0
              or result.get('finalHostFailureCount') != 1
              or type(result.get('finalSceneBytes')) is not int or result['finalSceneBytes'] <= 0
              or any(result.get(key) is not True for key in
                     ('newerReady', 'newerCauseAbsent', 'newerDisplayed', 'replacementReleased'))):
            raise RuntimeError('Old preview cleanup failure replaced the newer outcome')


def require_preview_review(data):
    result = data.get('previewReviewRequired')
    facts = ('writeActuallyAllowed', 'reviewBlocksSubmission', 'dirtyPreviewDraw',
             'reviewGuardAndDraftPreserved', 'testAndRuntimeSettingsUnchanged')
    if (not isinstance(result, dict) or any(result.get(key) is not True for key in facts)
            or result.get('preparations') != 1):
        raise RuntimeError('Required-review preview changed its guard, draft, or source')


def require_preview_selection_states(data, fixture):
    report = data.get('previewSelectionStates')
    side = fixture / 'lootr-styled-selection-states-report.json'
    if (not isinstance(report, dict) or not side.is_file() or json.loads(side.read_text()) != report
            or report.get('complete') is not True or report.get('failures') != 0
            or report.get('settingsUnchanged') is not True):
        raise RuntimeError('Preview selection-state report is incomplete or mismatched')
    rows = report.get('rows')
    expected = {(i, c, l, o) for i in range(6) for c, l, o in PREVIEW_SUBJECTS}
    def visual_checked(row):
        prepared = row.get('prepared')
        if not isinstance(prepared, dict):
            return False
        fingerprint = prepared.get('visualFingerprint')
        comparison = ('Disabled reference captured' if row['selectionCase'] == 0 else
                      'Disabled reference matched' if row['selectionCase'] == 1 else
                      'Built-in reference matched')
        return (isinstance(fingerprint, str) and len(fingerprint) == 64
                and all(c in '0123456789abcdef' for c in fingerprint)
                and (row['selectionCase'] == 0 or prepared.get('expectedVisualFingerprint') == fingerprint)
                and prepared.get('visualComparison') == comparison)
    if (not isinstance(rows, list) or len(rows) != 48
            or any(not isinstance(r, dict) or type(r.get('selectionCase')) is not int
                   or type(r.get('enabled')) is not bool or type(r.get('open')) is not bool
                   or type(r.get('looted')) is not bool
                   for r in rows)
            or {(r.get('selectionCase'), r.get('container'), r.get('looted'), r.get('open')) for r in rows} != expected
            or any(r.get('state') != 'READY' or r.get('drawn') is not True
                   or r.get('retired') is not True or r.get('enabled') is not (r.get('selectionCase') >= 2)
                   or not visual_checked(r)
                   for r in rows)):
        raise RuntimeError('Disabled, missing, or recovered preview coverage is incomplete')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target', choices=TARGETS, required=True)
    parser.add_argument('--mode', choices=('native', 'fresh', 'off'), default='native')
    parser.add_argument('--fetch', action='store_true', help='Fetch missing pinned verification inputs, never product runtime content')
    parser.add_argument('--run', action='store_true', help='Launch a GUI after external desktop coordination')
    parser.add_argument('--preview-host', action='store_true', help='Qualify the actual retained provider/worker host with a test-mounted shared widget')
    parser.add_argument('--production-shutdown', action='store_true', help='Check preview discard, recovery, and normal shutdown after settings checks')
    parser.add_argument('--production-close', action='store_true', help='Only check dirty production preview close, Keep editing, discard, and actual retirement')
    parser.add_argument('--production-viewport', action='store_true', help='Only check production camera controls, real window resizing, and retirement')
    parser.add_argument('--graphics-only', action='store_true', help='Only draw frozen domain meshes in the shared viewport; excludes editor and world checks')
    parser.add_argument('--scene', action='store_true', help='Create and render a disposable native container world')
    parser.add_argument('--gui-scale', type=int, choices=range(5), default=2, help='Native GUI scale for this disposable fixture (0 is Auto)')
    parser.add_argument('--local-profile', type=Path, help='Copy a bounded test-owned profile into the disposable client')
    parser.add_argument('--expected-private-sprites', type=int, help='Require an exact local-fixture private sprite count (0..128); default retains the existing minimum-six control')
    parser.add_argument('--reform-repository', type=Path, help='Staged Maven repository for the pinned unpublished re:form candidate')
    args = parser.parse_args()
    fixture, product, command = prepare(args.target, args.mode, args.fetch, args.scene, args.local_profile, args.expected_private_sprites, args.reform_repository, args.gui_scale, args.graphics_only, args.preview_host, args.production_shutdown, args.production_viewport, args.production_close)
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
            or ('sharedGraphicHost' in data) != args.preview_host
            or ('productionViewport' in data) != args.production_viewport
            or ('productionClose' in data) != args.production_close):
        raise RuntimeError(f'Client did not pass; inspect {fixture.relative_to(ROOT)}/launcher.log and report')
    if args.preview_host or args.production_shutdown:
        require_renderer_shutdown(data, fixture, product, args.target)
    if args.production_shutdown and data['rendererShutdown'].get('productionTask') is not True:
        raise RuntimeError('Shutdown did not observe the registered production preview')
    if args.production_shutdown:
        require_production_discard(data)
    if args.production_close:
        require_production_close(data)
    if args.production_viewport:
        require_production_viewport(data)
    if args.preview_host:
        require_preview_combinations(data, fixture)
        require_preview_authority(data)
        require_preview_cleanup_failure(data)
        require_preview_cleanup_modes(data)
        require_preview_review(data)
        require_preview_selection_states(data, fixture)
    print(json.dumps({'target': args.target, 'mode': args.mode, 'scope': 'production-close' if args.production_close else 'production-viewport' if args.production_viewport else 'production-shutdown' if args.production_shutdown else 'preview-host' if args.preview_host else 'graphics-only' if args.graphics_only else 'settings-and-resources', 'artifactSha256': digest(product), 'snapshots': len(data['snapshots']), 'report': str(report.relative_to(ROOT))}))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(f'Native fixture failed: {error}', file=sys.stderr)
        sys.exit(1)
