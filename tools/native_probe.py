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
import zipfile
from reform import dependency as reform_dependency

ROOT = Path(__file__).resolve().parents[1]
TARGETS = ('fabric-1_21_1', 'neoforge-1_21_1', 'fabric-26_3', 'neoforge-26_3')
PREVIEW_SUBJECTS = tuple((c, looted, opened) for c in ('CHEST', 'TRAPPED_CHEST', 'BARREL')
                         for looted in (False, True) for opened in ((False, True) if c == 'BARREL' else (False,)))
EFFECT_CAPTURES = ('production-effects-failed', 'production-effects-retried',
                   'production-effects-failed-technical', 'production-effects-failed-hidden',
                   'production-effects-retried-technical', 'production-effects-resources',
                   'production-effects-cancel-pending', 'production-effects-cancelled',
                   'production-effects-cancelled-details')
CAPTURES = (
    'production-close-ready', 'production-close-decision',
    *EFFECT_CAPTURES,
    'production-document-conflict', 'production-document-import',
    'production-viewport-narrow', 'production-viewport-returned',
    'production-input-narrow', 'production-input-returned',
    'production-input-filtered', 'production-input-filtered-defaults',
    'production-chooser-narrow', 'production-chooser-returned',
    'production-failure-tint',
    'production-operation-narrow', 'production-operation-returned', 'production-operation-completed',
    'production-locale-changed', 'production-locale-restored',
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


def prepare(target, mode, fetch=False, scene=False, local_profile=None, expected_private_sprites=None, reform_repository=None, gui_scale=2, graphics_only=False, preview_host=False, production_shutdown=False, production_viewport=False, production_close=False, production_effects=False, production_document=False, production_input=False, production_chooser=False, production_operation=False, production_locale=False, production_preparation=False, production_failure=False):
    if sum((graphics_only, preview_host, scene, production_shutdown, production_viewport, production_close, production_effects, production_document, production_input, production_chooser, production_operation, production_locale, production_preparation, production_failure)) > 1:
        raise ValueError('Choose one check: --graphics-only, --preview-host, --production-shutdown, --production-viewport, --production-close, --production-effects, --production-document, --production-input, --production-chooser, --production-operation, --production-locale, --production-preparation, --production-failure, or --scene')
    if production_failure and not local_profile:
        raise ValueError('--production-failure requires a local tint profile')
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
    runtime_artifacts = fixture / 'runtime-artifacts'
    if runtime_artifacts.is_symlink() or runtime_artifacts.exists() and not runtime_artifacts.is_dir():
        raise ValueError('Runtime artifact output must be a regular fixture directory')
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
    if runtime_artifacts.exists():
        shutil.rmtree(runtime_artifacts)
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
    selected = 'lootr_styled_local:fixture' if local_profile and not (production_chooser or production_failure) else None
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
    preparation_marker = fixture / '.lootr-styled-production-preparation'
    if production_preparation:
        preparation_marker.write_text('Actual task entered-worker cancel/reload/hide/close checks\n')
    else:
        preparation_marker.unlink(missing_ok=True)
    close_marker = fixture / '.lootr-styled-production-close'
    if production_close:
        close_marker.write_text('Actual dirty production preview close/keep/discard only\n')
    else:
        close_marker.unlink(missing_ok=True)
    effects_marker = fixture / '.lootr-styled-production-effects'
    if production_effects:
        effects_marker.write_text('Actual save/failure/cancellation, pending close, no-write retries and restoration\n')
    else:
        effects_marker.unlink(missing_ok=True)
    document_marker = fixture / '.lootr-styled-production-document'
    if production_document:
        document_marker.write_text('Actual retained source conflict and imported whole draft/Stage/Undo only\n')
    else:
        document_marker.unlink(missing_ok=True)
    input_marker = fixture / '.lootr-styled-production-input'
    if production_input:
        input_marker.write_text('Actual invalid native input, valid sibling and real window reflow without persistence\n')
    else:
        input_marker.unlink(missing_ok=True)
    chooser_marker = fixture / '.lootr-styled-production-chooser'
    if production_chooser:
        chooser_marker.write_text('Actual reference search/inspection and window reflow without selection or persistence\n')
    else:
        chooser_marker.unlink(missing_ok=True)
    failure_marker = fixture / '.lootr-styled-production-failure'
    if production_failure:
        failure_marker.write_text('Actual unsupported tint feedback, retained draft and explicit healthy recovery\n')
    else:
        failure_marker.unlink(missing_ok=True)
    operation_marker = fixture / '.lootr-styled-production-operation'
    if production_operation:
        operation_marker.write_text('Actual running reload/disclosure and window reflow, then one native reload without persistence\n')
    else:
        operation_marker.unlink(missing_ok=True)
    locale_marker = fixture / '.lootr-styled-production-locale'
    if production_locale:
        locale_marker.write_text('Actual language/font reload and restoration beside invalid native input; no settings save\n')
    else:
        locale_marker.unlink(missing_ok=True)
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


def require_fresh_captions(data):
    value = data.get('nativeWidgets', {})
    rows = value.get('freshChoiceCaptions')
    modes = ['AUTO', 'ON', 'OFF']
    labels = dict(AUTO='Auto', ON='On', OFF='Off')
    if (not isinstance(rows, list) or len(rows) != 4
            or any(not isinstance(row, dict) for row in rows)
            or rows[0].get('canonical') not in modes):
        raise RuntimeError('Fresh Objects caption evidence is missing')
    start = modes.index(rows[0]['canonical'])
    if (value.get('freshKeyboardCyclePreservesSource') is not True
            or any(row.get('canonical') != modes[(start + i) % 3]
                   or row.get('caption') != labels[modes[(start + i) % 3]]
                   or labels[modes[(start + i) % 3]] not in row.get('narration', '')
                   for i,row in enumerate(rows))
            or value.get('reviewFreshCanonical') != rows[0]['canonical']
            or value.get('reviewFreshCaption') != labels[rows[0]['canonical']]):
        raise RuntimeError('Fresh Objects captions changed canonical edits, review or source state')


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


def require_production_document(data):
    value = data.get('productionDocument')
    if (not isinstance(value, dict)
            or any(value.get(key) is not True for key in ('conflictSameOwnerRoot',
                'comparedModelsAndArtwork', 'resolutionDoesNotSave', 'importNoImplicitPreview',
                'importValueFormatDistinctFromNativeFile', 'staleImportTargetRefused',
                'wholeDraftInvalidImportRetained', 'undoRestoresCompleteDraft',
                'originalSavedValuesRestored', 'resourceGenerationUnchanged', 'closedCleanly'))):
        raise RuntimeError('Production document conflict/import did not retain complete guarded draft/source')


def require_production_locale(data):
    value = data.get('productionLocale')
    if (not isinstance(value, dict) or any(value.get(k) is not True for k in (
            'localeRoundTripRetainsWholeDraft', 'caretSelectionFocusAndSaveGuardRetained',
            'originalLocaleRestored', 'settingsUnchanged', 'sameOwnerUntilClose', 'closedCleanly'))):
        raise RuntimeError('Production locale reload lost retained input or source facts')
    states, baseline, invalid = value.get('states'), value.get('baselineWholeDraft'), value.get('invalidWholeDraft')
    if (not isinstance(states, list) or len(states) != 3 or any(not isinstance(s, dict) for s in states)
            or [s.get('phase') for s in states] != ['original', 'changed', 'restored']
            or not isinstance(baseline, dict) or set(baseline) != {'enabled','models','artwork','freshObjects'}
            or not isinstance(invalid, dict) or set(invalid) != set(baseline)
            or baseline.get('enabled') not in ('true','false')
            or invalid.get('enabled') != ('false' if baseline['enabled'] == 'true' else 'true')
            or invalid.get('models') != 'bad id'
            or any(invalid[k] != baseline[k] for k in ('artwork','freshObjects'))
            or value.get('restoredWholeDraft') != baseline
            or any(s.get('draft') != invalid or s.get('canSubmit') is not False
                   or s.get('text') != 'bad id' or s.get('caret') != 3 or s.get('selection') != 'ad'
                   or s.get('modelFocus') is not True or not s.get('generation')
                   or not s.get('language') or type(s.get('unicode')) is not bool
                   or not s.get('doneLabel') or not isinstance(s.get('fontWidths'),list)
                   or len(s['fontWidths']) != 4
                   or any(type(w) is not int or w < 0 for w in s['fontWidths']) for s in states)
            or len({s['generation'] for s in states}) != 3
            or states[0]['language'] == states[1]['language'] or states[0]['language'] != states[2]['language']
            or states[0]['unicode'] == states[1]['unicode'] or states[0]['unicode'] != states[2]['unicode']
            or states[0]['doneLabel'] == states[1]['doneLabel'] or states[0]['doneLabel'] != states[2]['doneLabel']
            or states[0]['fontWidths'] == states[1]['fontWidths'] or states[0]['fontWidths'] != states[2]['fontWidths']):
        raise RuntimeError('Production locale reload lacks actual font/language/generation or complete-draft evidence')


def require_production_operation(data):
    value = data.get('productionOperation')
    if (not isinstance(value, dict) or any(value.get(k) is not True for k in (
            'runningHandleAndDisclosureRetained', 'oneActualReloadAfterRelease',
            'sourceFileUnchanged', 'sameDraftAndOwner', 'closedCleanly'))):
        raise RuntimeError('Production operation did not retain its running handle and unchanged source')
    states = value.get('states')
    completed = value.get('completedReload')
    if (not isinstance(states, list) or len(states) != 3
            or any(not isinstance(s, dict) for s in states)
            or [s.get('phase') for s in states] != ['running', 'narrow', 'returned']
            or not states[0].get('handle')
            or any(s.get('handle') != states[0]['handle'] or s.get('state') != 'RUNNING'
                   or s.get('operation') != 'lootr_styled:reload' or s.get('holding') is not True
                   or s.get('requests') != 1 or s.get('releases') != 0
                   or s.get('nativeEntryAttempts') != 0 or s.get('technicalExpanded') is not True for s in states)
            or states[1].get('window') != '320x240'
            or states[0].get('window') != states[2].get('window')
            or states[0].get('gui') != states[2].get('gui')
            or not isinstance(completed, dict) or completed.get('handle') != states[0]['handle']
            or completed.get('operation') != 'lootr_styled:reload'
            or completed.get('hasPersistenceReceipt') is not False
            or completed.get('application') != 'APPLIED'
            or not value.get('savedRevision') or completed.get('sourceRevision') != value['savedRevision']
            or not value.get('oldGeneration') or not completed.get('generation')
            or completed['generation'] == value['oldGeneration']):
        raise RuntimeError('Production operation lost running state or truthful native application evidence')


def require_production_chooser(data, expected_choice=None):
    choice = expected_choice or dict(id='lootr_styled:vanilla', title='Vanilla (Inherited)', query='Vanilla')
    value = data.get('productionChooser')
    if (not isinstance(value, dict) or any(value.get(k) is not True for k in (
            'searchRemovedDefaultChoice', 'queryAndInspectionSurviveWindowReflow',
            'inspectionDoesNotStage', 'sameDraftAndOwner', 'sourceFileAndGenerationUnchanged', 'closedCleanly'))):
        raise RuntimeError('Production chooser did not retain guarded search and inspection')
    states = value.get('states')
    if (not isinstance(states, list) or len(states) != 3
            or [s.get('phase') for s in states if isinstance(s, dict)] != ['inspected', 'narrow', 'returned']
            or not value.get('baselineModels') or not value.get('baselineArtwork')
            or value['baselineModels'] == 'lootr_styled:vanilla'
            or any(s.get('query') != choice['query'] or s.get('caret') != 3 or s.get('selection') != choice['query'][1:3]
                   or s.get('queryFocus') is not True or s.get('inspected') != choice['id']
                   or s.get('models') != value['baselineModels']
                   or s.get('artwork') != value['baselineArtwork'] for s in states)
            or states[1].get('window') != '320x240'
            or states[0].get('window') != states[2].get('window')
            or states[0].get('gui') != states[2].get('gui')):
        raise RuntimeError('Production chooser lost query, inspection or unchanged field evidence')
    if expected_choice and (value.get('inspectedId') != choice['id']
            or value.get('inspectedTitle') != choice['title'] or value.get('queryText') != choice['query']
            or choice['title'] not in value.get('inspectedChoiceNarration', '')):
        raise RuntimeError('Production chooser lost the declared local title or native narration')
    require_workspace_navigation(value.get('workspaceNavigation'))
    require_single_location_narration(value.get('workspaceNavigation'))


def require_workspace_navigation(value):
    if (not isinstance(value, dict) or value.get('query') != 'Models'
            or value.get('caret') != 3 or value.get('selectionIndex') != 1
            or value.get('initialFilter') != 'ALL'
            or not value.get('changedFilter') or value['changedFilter'] == 'ALL'
            or not isinstance(value.get('expandedCount'), int) or value['expandedCount'] < 0
            or not value.get('narrowGui')
            or any(value.get(k) is not True for k in (
                'bookmarkRestoresNativeQueryFilterAndFocus',
                'bookmarkStateSurvivesActualWindowRoundTrip', 'nativeCategoriesDrawerOpened',
                'queryExplicitlyRefocusedInDrawer',
                'sameOwnerDraftSourceAndGeneration'))):
        raise RuntimeError('Production chooser lost native workspace bookmark state')
    targets = value.get('upTargets')
    if (not isinstance(targets, list) or not 1 <= len(targets) <= 8
            or any(not isinstance(t, dict) or not t.get('attempt') or not t.get('target')
                   or t.get('outcome') != 'REVEALED' for t in targets)
            or targets[-1].get('kind') != 'Home'
            or len({t['attempt'] for t in targets}) != len(targets)):
        raise RuntimeError('Production chooser lost identified parent-navigation evidence')
    names = ['Home', 'Settings', 'Appearance', 'Profile selection']
    visits = value.get('ancestorKeyboardVisits')
    if (not isinstance(visits, list) or len(visits) != len(names) or len(targets) != len(names)
            or any(not isinstance(v, dict) or v.get('name') != name
                   or not isinstance(v.get('narration'), str)
                   or not isinstance(v.get('locationNarration'), str)
                   or name not in v.get('narration', '')
                   or any(label not in v.get('locationNarration', '') for label in [*names, 'Models'])
                   or v.get('sameRootOwnerAndDraft') is not True
                   or v.get('expectedTarget') != targets[-1-index]['target']
                   or v.get('actualTarget') != v.get('expectedTarget')
                   for index, (name, v) in enumerate(zip(names, visits)))):
        raise RuntimeError('Production chooser lost native ancestor-menu keyboard evidence')


def require_single_location_narration(value):
    # This fixture uses the English breadcrumb declared by the actual production hierarchy.
    # Keep writing acceptance separate from the functional keyboard/target checks above.
    path = 'Home / Settings / Appearance / Profile selection / Models'
    visits = value.get('ancestorKeyboardVisits') if isinstance(value, dict) else None
    if (not isinstance(visits, list) or len(visits) != 4
            or any(not isinstance(v, dict) or not isinstance(v.get('locationNarration'), str)
                   or v['locationNarration'].count(path) != 1 for v in visits)):
        raise RuntimeError('Production chooser must narrate the complete Location path exactly once')


def require_production_failure(data):
    value = data.get('productionFailure')
    if (not isinstance(value, dict) or any(value.get(k) is not True for k in (
            'specificDomainRefusal', 'failedPreparationLeavesNoGraphic', 'readableFailureCause',
            'failedDraftRetained', 'explicitRecoveryDrawAndRelease',
            'sourceFileAndGenerationUnchanged', 'closedCleanly'))
            or value.get('failureState') != 'REFUSED'
            or 'lootr_styled.preview.tint_unavailable' not in value.get('failureCodes', [])
            or value.get('requests') != 2
            or not value.get('expectedFailureMessage')
            or value['expectedFailureMessage'] not in value.get('failureFeedback', '')
            or any(type(value.get(k)) is not int or value[k] != 0 for k in (
                'nativeBytes', 'nativeResources', 'sceneBytes', 'sceneResources'))):
        raise RuntimeError('Production preview failure lost its domain cause, draft, recovery or release')


def require_filtered_actions(data):
    value = data.get('productionInput', {}).get('filteredActions')
    if (not isinstance(value, dict) or value.get('query') != 'Vanilla'
            or any(value.get(k) is not True for k in ('hiddenModelAndArtworkControls',
                'wholeDocumentDefaultsAndUndo', 'sameOwnerAndNoWrite', 'searchRemovedDefaultChoice'))):
        raise RuntimeError('Filtered document actions lack actual hidden-field evidence')
    original = data['productionInput'].get('baselineWholeDraft')
    staged = dict(enabled='false', models='lootr_styled:vanilla', artwork='lootr_styled:vanilla', freshObjects='OFF')
    defaults = dict(enabled='true', models='lootr_styled:default', artwork='lootr_styled:default', freshObjects='AUTO')
    if (value.get('stagedWholeDraft') != staged or value.get('undoneWholeDraft') != staged
            or value.get('defaultWholeDraft') != defaults
            or not isinstance(original, dict) or set(original) != set(defaults)
            or value.get('restoredWholeDraft') != original):
        raise RuntimeError('Filtered document actions omitted or changed hidden draft values')


def require_production_input(data):
    value = data.get('productionInput')
    if (not isinstance(value, dict) or any(value.get(key) is not True for key in (
            'invalidInputReflowKeepsWholeDraft', 'invalidInputReflowKeepsCaretSelectionFocus',
            'repairRestoresOriginalValuesWithoutSaving', 'settingsUnchanged',
            'resourceGenerationUnchanged', 'sameOwnerUntilClose', 'closedCleanly'))):
        raise RuntimeError('Production input did not retain and repair the actual guarded draft')
    states = value.get('states')
    draft = value.get('invalidWholeDraft')
    baseline = value.get('baselineWholeDraft')
    if (not isinstance(states, list) or len(states) != 3
            or not isinstance(draft, dict) or set(draft) != {'enabled', 'models', 'artwork', 'freshObjects'}
            or draft.get('models') != 'bad id'
            or not isinstance(baseline, dict) or set(baseline) != set(draft)
            or baseline.get('enabled') not in ('true', 'false')
            or draft.get('enabled') != ('false' if baseline['enabled'] == 'true' else 'true')
            or any(draft[k] != baseline[k] for k in ('artwork', 'freshObjects'))
            or value.get('restoredWholeDraft') != baseline
            or [s.get('phase') for s in states if isinstance(s, dict)] != ['invalid', 'narrow', 'returned']
            or any(s.get('draft') != draft or s.get('canSubmit') is not False
                   or s.get('text') != 'bad id' or s.get('caret') != 3
                   or s.get('selection') != 'ad' or s.get('modelFocus') is not True for s in states)
            or states[1].get('window') != '320x240'
            or states[0].get('window') != states[2].get('window')
            or states[0].get('gui') != states[2].get('gui')
            or type(value.get('editToInvalidGuardNanos')) is not int
            or value['editToInvalidGuardNanos'] < 0):
        raise RuntimeError('Production input lost actual window, draft, caret or selection evidence')


def require_effect_captures(fixture):
    for name in EFFECT_CAPTURES:
        path = fixture / (name + '.png')
        if not path.is_file() or not path.stat().st_size:
            raise RuntimeError(f'Production effects capture is missing or empty: {path}')


def require_runtime_artifacts(data, fixture):
    expected = {
        'lootr_styled': ('design/inherent/lootr_styled/ResourceId.class',
                         'design/inherent/lootr_styled/client/ClientRuntime.class',
                         'design/inherent/lootr_styled/client/StylePreferences.class'),
        'reform': ('design/inherent/reform/minecraft/client/ClientFileSettings.class',
                   'design/inherent/reform/minecraft/client/ClientEditingWorkflow.class',
                   'design/inherent/reform/minecraft/client/ClientGraphicHost.class'),
    }
    facts = data.get('runtimeArtifacts')
    if not isinstance(facts, dict) or set(facts) != set(expected):
        raise RuntimeError('Loaded runtime artifacts must identify exactly Styled and re:form')
    for mod, members in expected.items():
        row = facts[mod]
        relative = f'runtime-artifacts/{mod}.jar'
        if (not isinstance(row, dict) or row.get('copy') != relative
                or not isinstance(row.get('path'), str) or not row['path']
                or type(row.get('bytes')) is not int or not 0 < row['bytes'] <= 64 * 1024 * 1024):
            raise RuntimeError(f'Loaded runtime archive has invalid identity or size: {mod}')
        copied = fixture / relative
        if (copied.parent.is_symlink() or copied.is_symlink() or not copied.is_file()
                or copied.stat().st_size != row['bytes'] or digest(copied) != row.get('sha256')):
            raise RuntimeError(f'Loaded runtime archive copy/hash differs: {mod}')
        witnesses = row.get('witnesses')
        if (not isinstance(witnesses, list) or len(witnesses) != len(members)
                or any(not isinstance(w, dict) or w.get('member') != name
                       or w.get('resourceMatchesArchive') is not True
                       or not isinstance(w.get('resource'), str) or not w['resource']
                       for w, name in zip(witnesses, members))):
            raise RuntimeError(f'Loaded runtime class-resource witnesses differ: {mod}')
        try:
            with zipfile.ZipFile(copied) as archive:
                for witness in witnesses:
                    item = archive.getinfo(witness['member'])
                    if not 0 < item.file_size <= 4 * 1024 * 1024:
                        raise RuntimeError(f'Loaded runtime class exceeds fixture allowance: {mod}')
                    with archive.open(item) as source:
                        content = source.read(4 * 1024 * 1024 + 1)
                    if len(content) != item.file_size:
                        raise RuntimeError(f'Loaded runtime class size differs: {mod}/{item.filename}')
                    actual = hashlib.sha256(content).hexdigest()
                    if actual != witness.get('sha256'):
                        raise RuntimeError(f'Loaded runtime class hash differs: {mod}/{item.filename}')
        except (OSError, KeyError, zipfile.BadZipFile) as error:
            raise RuntimeError(f'Loaded runtime archive cannot supply its classes: {mod}') from error


def require_production_effects(data):
    value = data.get('productionEffects')
    if (not isinstance(value, dict)
            or any(value.get(key) is not True for key in ('saveOnlyDoesNotReload',
                'confirmedSaveSurvivesFailedReload', 'retryUsesSavedRevisionWithoutPersistence',
                'retryPreservesSavedFileIdentity', 'originalSavedValuesRestored',
                'failedDisclosurePreservesHandleFileAndEffects',
                'retryDisclosurePreservesHandleFileAndEffects',
                'routineTraceTechnical', 'resourceInspectionKeepsTraceVisible',
                'sameOwnerAndRoot', 'closedCleanly'))
            or type(value.get('injectedFailures')) is not int or value['injectedFailures'] != 1):
        raise RuntimeError('Production effects did not retain the save, retry and restored owner')
    failed, retry = value.get('failedSaveReload', {}), value.get('retryReload', {})
    if (not isinstance(failed, dict) or not isinstance(retry, dict)
            or failed.get('hasPersistenceReceipt') is not True or failed.get('persistence') != 'SAVED'
            or failed.get('application') != 'FAILED' or failed.get('code') != 'NATIVE_FAILURE'
            or not failed.get('savedRevision') or failed['savedRevision'] != failed.get('sourceRevision')
            or retry.get('hasPersistenceReceipt') is not False or retry.get('application') != 'APPLIED'
            or retry.get('sourceRevision') != failed['savedRevision']
            or not retry.get('generation') or not failed.get('handle') or not retry.get('handle')
            or failed.get('operation') != 'lootr_styled:save_reload'
            or retry.get('operation') != 'lootr_styled:retry'
            or retry.get('handle') == failed.get('handle')):
        raise RuntimeError('Production effects lost confirmed persistence or matching retry facts')
    inspection = value.get('resourceInspection')
    if (not isinstance(inspection, dict)
            or retry.get('technicalDetails') is not True
            or type(retry.get('detailCount')) is not int or retry['detailCount'] <= 0
            or retry.get('matchesCurrentResourceTrace') is not True
            or inspection.get('technicalDetails') is not False
            or type(inspection.get('detailCount')) is not int or inspection['detailCount'] <= 0
            or inspection.get('matchesCurrentResourceTrace') is not True
            or inspection.get('hasPersistenceReceipt') is not False
            or inspection.get('operation') != 'lootr_styled:details'
            or inspection.get('sourceRevision') != retry['sourceRevision']
            or inspection.get('generation') != retry['generation']):
        raise RuntimeError('Production effects changed trace visibility or resource inspection facts')
    require_production_cancellation(value.get('cancellation'))


def require_production_preparation(data):
    value = data.get('productionPreparation')
    if (not isinstance(value, dict)
            or any(value.get(k) is not True for k in ('settingsUnchanged', 'savedFileIdentityUnchanged',
                'draftRetainedUntilClose', 'closedWorkflowDiscardsLateResult', 'localProfileFilesUnchanged', 'resourceGenerationPreservedOutsideReload'))
            or type(value.get('actualResourceReloads')) is not int or value['actualResourceReloads'] != 1
            or type(value.get('requests')) is not int or value['requests'] != 7
            or any(type(value.get(k)) is not int or value[k] != 0 for k in ('nativeBytes', 'sceneBytes'))):
        raise RuntimeError('Production preparation lost actual source, request, or retirement facts')
    rows = value.get('cases')
    if not isinstance(rows, list) or [r.get('case') for r in rows if isinstance(r, dict)] != ['cancel', 'reload', 'hidden-return', 'close']:
        raise RuntimeError('Production preparation lost its four actual controls')
    for row in rows:
        if (any(row.get(k) is not True for k in ('workerEntered', 'heldWithoutObsoleteDraw', 'workerExited'))
                or not isinstance(row.get('workerThread'), str) or not row['workerThread']
                or not isinstance(row.get('operation'), str) or not row['operation']
                or row.get('terminalState') not in ('CANCELLED', 'REFUSED', 'CLOSED')):
            raise RuntimeError('Production preparation accepted obsolete or unentered work')
        if row['case'] != 'close' and (row.get('terminalBeforeWorkerExit') is not False
                or row.get('explicitRecoveryDrawn') is not True or row.get('recoveryReleased') is not True):
            raise RuntimeError('Production preparation skipped draining or explicit recovery')
    if (rows[0].get('nativeCancellationRequested') is not True
            or rows[1].get('generationBefore') == rows[1].get('generationAfter')
            or not rows[1].get('generationAfter')
            or rows[1].get('recoveryGeneration') != rows[1]['generationAfter']
            or rows[2].get('returnDoesNotPrepare') is not True):
        raise RuntimeError('Production preparation lost actual reload or retained-task recovery')


def require_production_cancellation(value):
    if (not isinstance(value, dict)
            or any(value.get(key) is not True for key in (
                'requestRetainsPendingWork', 'escapeReturnsWithoutCancellingOwner',
                'pendingWorkPreventsWorkspaceClose', 'confirmedSaveAndUnknownApplication',
                'cancellationRequested', 'terminalPendingNoticeCleared', 'pendingReloadActionsBlocked', 'retryPreservesSavedFileIdentity',
                'originalSavedValuesRestored'))
            or not isinstance(value.get('pendingCloseNarration'), str)
            or 'Wait for pending operations' not in value['pendingCloseNarration']
            or not isinstance(value.get('terminalNarration'), str)
            or 'Wait for pending operations' in value['terminalNarration']
            or 'Saved; reload not confirmed' not in value['terminalNarration']
            or type(value.get('nativeReloads')) is not int or value['nativeReloads'] != 3):
        raise RuntimeError('Production cancellation lost pending work, close guards, or restoration')
    cancelled = value.get('cancelledSaveReload')
    retry = value.get('retryAfterCancellation')
    restored = value.get('restoredSaveReload')
    if (any(not isinstance(row, dict) for row in (cancelled, retry, restored))
            or cancelled.get('operation') != 'lootr_styled:save_reload'
            or cancelled.get('hasPersistenceReceipt') is not True
            or cancelled.get('persistence') != 'SAVED'
            or cancelled.get('application') != 'UNKNOWN' or cancelled.get('code') != 'CANCELLED'
            or not cancelled.get('savedRevision')
            or cancelled['savedRevision'] != cancelled.get('sourceRevision')
            or retry.get('operation') != 'lootr_styled:retry'
            or retry.get('hasPersistenceReceipt') is not False
            or retry.get('application') != 'APPLIED'
            or retry.get('sourceRevision') != cancelled['savedRevision']
            or not value.get('publishedGenerationAfterCancellation') or not retry.get('generation')
            or retry['generation'] == value['publishedGenerationAfterCancellation']
            or not cancelled.get('handle') or not retry.get('handle')
            or cancelled['handle'] == retry['handle']
            or restored.get('operation') != 'lootr_styled:save_reload'
            or restored.get('hasPersistenceReceipt') is not True
            or restored.get('persistence') != 'SAVED' or restored.get('application') != 'APPLIED'
            or not restored.get('savedRevision')
            or restored['savedRevision'] != restored.get('sourceRevision')
            or not restored.get('generation') or restored['generation'] == retry['generation']):
        raise RuntimeError('Production cancellation lost confirmed persistence or uncertain/retried application')
    if value.get('detailsRetainSameOutcome') is not True:
        raise RuntimeError('Production cancellation lost its retained details outcome')
    require_capture_frames(value.get('captureFrames'))


def require_capture_frames(rows):
    names = ('production-effects-cancel-pending', 'production-effects-cancelled',
             'production-effects-cancelled-details')
    if (not isinstance(rows, list) or len(rows) != len(names)
            or any(not isinstance(row, dict) or row.get('name') != name
                   or row.get('expectedRootDrawn') is not True
                   or row.get('expectedScreen') != 'design.inherent.reform.minecraft.client.ClientEditingWorkflow$Overview'
                   or any(type(row.get(key)) is not int or row[key] < 1
                          for key in ('baselineRootDraw', 'requestedRootDraw', 'requestedLoop', 'completedLoop'))
                   or row['requestedRootDraw'] - row['baselineRootDraw'] < 4
                   or row['requestedLoop'] < row['requestedRootDraw']
                   or row['completedLoop'] < row['requestedLoop']
                   for row, name in zip(rows, names))):
        raise RuntimeError('Capture lacks expected-root draw and completed native-loop evidence')


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


def require_production_cost(data):
    value = data.get('productionViewport', {}).get('cost')
    if (not isinstance(value, dict) or value.get('acceptedInputCharacters') != 256
            or value.get('explicitPreparations') != 4
            or any(value.get(k) is not True for k in ('noImplicitPreparationDuringInputSearch',
                'noAutomaticPreparationOnReturn', 'noUnexplainedTrackedRetention',
                'retirementAfterEveryCycle', 'sameOwnerDraftAndSource'))
            or any(type(value.get(k)) is not int or value[k] < 0 for k in (
                'nativeExtremeInputCallNanos','nativeOrdinaryInputCallNanos','nativeQueryInputCallNanos',
                'nativeSearchActionCallNanos','searchToFilteredChoiceObservedNanos'))
            or any(type(value.get(k)) is not int or value[k] <= 0 for k in (
                'nativeBudgetBytes','outputBudgetBytes','resourceBudget'))):
        raise RuntimeError('Production costs lack actual bounded input/search/preparation facts')
    times, rows, paints = value.get('prepareToFirstDrawObservedNanos'), value.get('cycles'), value.get('uiCallbacks')
    phases = ['accepted-extreme-id','ordinary-id-restoration','profile-search','preview-cycle-1',
              'returned-profile-1','preview-cycle-2','returned-profile-2','preview-cycle-3',
              'returned-profile-3','preview-cycle-4']
    if (not isinstance(times,list) or len(times)!=4 or any(type(t) is not int or t<0 for t in times)
            or not isinstance(rows,list) or len(rows)!=4 or any(not isinstance(r,dict) for r in rows)
            or not isinstance(paints,list) or len(paints)!=len(phases) or any(not isinstance(p,dict) for p in paints)
            or [p.get('phase') for p in paints]!=phases):
        raise RuntimeError('Production costs have incomplete actual phases or repeated cycles')
    for i,row in enumerate(rows,1):
        if (row.get('cycle')!=i or row.get('providerEntries')!=i or row.get('released') is not True
                or any(type(row.get(k)) is not int or row[k]!=0 for k in ('releasedNativeBytes',
                    'releasedSceneBytes','releasedNativeResources','releasedSceneResources','outstandingUses'))
                or any(type(row.get(k)) is not int or row[k]<=0 for k in ('nativePeakBytes','scenePeakBytes',
                    'nativePeakResources','scenePeakResources','drawWidth','drawHeight'))
                or row['nativePeakBytes']>value['nativeBudgetBytes'] or row['scenePeakBytes']>value['outputBudgetBytes']
                or max(row['nativePeakResources'],row['scenePeakResources'])>value['resourceBudget']):
            raise RuntimeError('Production costs retained allocations or exceeded declared bounds')
    for paint in paints:
        metrics=[paint.get(k) for k in ('callbackWallMinNanos','callbackWallMedianNanos','callbackWallP95Nanos','callbackWallMaxNanos')]
        if (type(paint.get('samples')) is not int or not 8<=paint['samples']<=256
                or any(type(n) is not int or n<0 for n in metrics) or metrics!=sorted(metrics)
                or paint.get('providerEntriesInCallback')!=0 or paint.get('resourceContentLookupsInCallback')!=0
                or any(type(paint.get(k)) is not int or paint[k]<0 for k in (
                    'resourceManagerCallsInCallback','packMetadataReadsInCallback','resourceContentLookupsInCallback'))
                or paint['resourceManagerCallsInCallback'] != paint['packMetadataReadsInCallback'] + paint['resourceContentLookupsInCallback']):
            raise RuntimeError('Production costs lack bounded UI callback samples without preparation/lookups')


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
    parser.add_argument('--production-document', action='store_true', help='Check real source conflicts, native imported value JSON, Stage and Undo')
    parser.add_argument('--production-input', action='store_true', help='Check invalid native text and a valid sibling across actual window reflow')
    parser.add_argument('--production-locale', action='store_true', help='Check invalid native input across real language/font reload and restoration')
    parser.add_argument('--production-operation', action='store_true', help='Check running reload/disclosure through actual window reflow, then one real reload')
    parser.add_argument('--production-chooser', action='store_true', help='Check actual profile search/inspection across real window reflow without choosing')
    parser.add_argument('--production-failure', action='store_true', help='Check actual unsupported tint feedback and explicit healthy preview recovery with an owned local profile')
    parser.add_argument('--production-preparation', action='store_true', help='Check actual task entered-worker cancellation, native reload, hidden return, and late closure')
    parser.add_argument('--production-effects', action='store_true', help='Check saved receipts through failed/cancelled reloads, pending close, and no-write retry')
    parser.add_argument('--production-close', action='store_true', help='Only check dirty production preview close, Keep editing, discard, and actual retirement')
    parser.add_argument('--production-viewport', action='store_true', help='Only check production camera controls, real window resizing, and retirement')
    parser.add_argument('--graphics-only', action='store_true', help='Only draw frozen domain meshes in the shared viewport; excludes editor and world checks')
    parser.add_argument('--scene', action='store_true', help='Create and render a disposable native container world')
    parser.add_argument('--gui-scale', type=int, choices=range(5), default=2, help='Native GUI scale for this disposable fixture (0 is Auto)')
    parser.add_argument('--local-profile', type=Path, help='Copy a bounded test-owned profile into the disposable client')
    parser.add_argument('--expected-private-sprites', type=int, help='Require an exact local-fixture private sprite count (0..128); default retains the existing minimum-six control')
    parser.add_argument('--reform-repository', type=Path, help='Staged Maven repository for the pinned unpublished re:form candidate')
    args = parser.parse_args()
    fixture, product, command = prepare(args.target, args.mode, args.fetch, args.scene, args.local_profile, args.expected_private_sprites, args.reform_repository, args.gui_scale, args.graphics_only, args.preview_host, args.production_shutdown, args.production_viewport, args.production_close, args.production_effects, args.production_document, args.production_input, args.production_chooser, args.production_operation, args.production_locale, args.production_preparation, args.production_failure)
    chooser_choice = None
    if args.production_chooser and args.local_profile:
        title = json.loads((args.local_profile / 'profile.json').read_text())['title']
        if not isinstance(title, str) or not 3 <= len(title) <= 160:
            raise ValueError('The chooser fixture requires a local title of 3 to 160 characters')
        chooser_choice = dict(id='lootr_styled_local:fixture', title=title, query=title[:32])
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
            or ('productionClose' in data) != args.production_close
            or ('productionPreparation' in data) != args.production_preparation
            or ('productionEffects' in data) != args.production_effects
            or ('productionDocument' in data) != args.production_document
            or ('productionInput' in data) != args.production_input
            or ('productionChooser' in data) != args.production_chooser
            or ('productionFailure' in data) != args.production_failure
            or ('productionOperation' in data) != args.production_operation
            or ('productionLocale' in data) != args.production_locale):
        raise RuntimeError(f'Client did not pass; inspect {fixture.relative_to(ROOT)}/launcher.log and report')
    require_runtime_artifacts(data, fixture)
    if data.get('nativeWidgetsComplete') is True:
        require_fresh_captions(data)
    if args.preview_host or args.production_shutdown:
        require_renderer_shutdown(data, fixture, product, args.target)
    if args.production_shutdown and data['rendererShutdown'].get('productionTask') is not True:
        raise RuntimeError('Shutdown did not observe the registered production preview')
    if args.production_shutdown:
        require_production_discard(data)
    if args.production_document:
        require_production_document(data)
    if args.production_input:
        require_production_input(data)
        require_filtered_actions(data)
        for name in ('production-input-narrow', 'production-input-returned', 'production-input-filtered', 'production-input-filtered-defaults'):
            if not (fixture / (name + '.png')).is_file():
                raise RuntimeError(f'Production input capture missing: {name}')
    if args.production_locale:
        require_production_locale(data)
        for name in ('production-locale-changed', 'production-locale-restored'):
            path = fixture / (name + '.png')
            if not path.is_file() or not path.stat().st_size:
                raise RuntimeError(f'Production locale capture missing: {name}')
    if args.production_operation:
        require_production_operation(data)
        for name in ('production-operation-narrow', 'production-operation-returned', 'production-operation-completed'):
            path = fixture / (name + '.png')
            if not path.is_file() or not path.stat().st_size:
                raise RuntimeError(f'Production operation capture missing: {name}')
    if args.production_chooser:
        require_production_chooser(data, chooser_choice)
        for name in ('production-chooser-narrow', 'production-chooser-returned'):
            if not (fixture / (name + '.png')).is_file():
                raise RuntimeError(f'Production chooser capture missing: {name}')
    if args.production_failure:
        require_production_failure(data)
        if not (fixture / 'production-failure-tint.png').is_file():
            raise RuntimeError('Production preview failure capture missing')
    if args.production_preparation:
        require_production_preparation(data)
    if args.production_effects:
        require_production_effects(data)
        require_effect_captures(fixture)
    if args.production_close:
        require_production_close(data)
    if args.production_viewport:
        require_production_viewport(data)
        require_production_cost(data)
    if args.preview_host:
        require_preview_combinations(data, fixture)
        require_preview_authority(data)
        require_preview_cleanup_failure(data)
        require_preview_cleanup_modes(data)
        require_preview_review(data)
        require_preview_selection_states(data, fixture)
    print(json.dumps({'target': args.target, 'mode': args.mode, 'scope': 'production-failure' if args.production_failure else 'production-preparation' if args.production_preparation else 'production-locale' if args.production_locale else 'production-operation' if args.production_operation else 'production-chooser' if args.production_chooser else 'production-input' if args.production_input else 'production-document' if args.production_document else 'production-effects' if args.production_effects else 'production-close' if args.production_close else 'production-viewport' if args.production_viewport else 'production-shutdown' if args.production_shutdown else 'preview-host' if args.preview_host else 'graphics-only' if args.graphics_only else 'settings-and-resources', 'artifactSha256': digest(product), 'snapshots': len(data['snapshots']), 'report': str(report.relative_to(ROOT))}))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(f'Native fixture failed: {error}', file=sys.stderr)
        sys.exit(1)
