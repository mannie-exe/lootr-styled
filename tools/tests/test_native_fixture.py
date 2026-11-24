"""Fixture preparation must not rewrite a running client's input files."""

import os
import copy
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import native_probe


class NativeFixtureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        (self.root / 'gradle.properties').write_text('mod_version=fixture\n')
        product = self.root / 'build/distributions/lootr-styled-1.21.1-neoforge-fixture.jar'
        product.parent.mkdir(parents=True)
        product.write_bytes(b'Fixture product input')
        self.fixture = self.root / 'verification/neoforge-1_21_1/runs/native'
        self.fixture.mkdir(parents=True)
        (self.fixture / '.lootr-styled-fixture').write_text('lootr-styled-native-verification-v1\n')
        self.settings = self.fixture / 'config/lootr_styled/settings.json'
        self.settings.parent.mkdir(parents=True)
        self.settings.write_text('Preserve running client input')
        for name, replacement in [('ROOT', self.root), ('reform_dependency', lambda *_: {'sha256': 'fixture'})]:
            mocked = patch.object(native_probe, name, replacement)
            mocked.start()
            self.addCleanup(mocked.stop)

    def test_a_live_fixture_pid_refuses_before_inputs_or_marker_are_replaced(self):
        marker = self.fixture / '.lootr-styled-probe-pid'
        marker.write_text(str(os.getpid()))
        report = self.fixture / 'lootr-styled-probe-report.json'
        report.write_text('Preserve live report')
        capture = self.fixture / 'reform-home.png'
        capture.write_bytes(b'Preserve live capture')
        with self.assertRaisesRegex(ValueError, 'before preparing'):
            native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertEqual('Preserve running client input', self.settings.read_text())
        self.assertEqual(str(os.getpid()), marker.read_text())
        self.assertEqual('Preserve live report', report.read_text())
        self.assertEqual(b'Preserve live capture', capture.read_bytes())

    def test_a_stopped_fixture_cannot_reuse_a_previous_success_receipt(self):
        receipts = ('lootr-styled-probe-report.json', 'lootr-styled-probe-state.txt',
                    'lootr-styled-shutdown-report.json', 'lootr-styled-cleanup-failure-report.json',
                    'lootr-styled-selection-states-report.json',
                    'lootr-styled-cleanup-failure-synchronous-report.json',
                    'lootr-styled-cleanup-failure-replaced-report.json')
        for name in receipts:
            (self.fixture / name).write_text('Previous run')
        native_probe.prepare('neoforge-1_21_1', 'native')
        for name in receipts:
            self.assertFalse((self.fixture / name).exists(), name)

    def test_stopped_preparation_clears_owned_captures_but_keeps_other_files(self):
        for name in ('reform-home.png', 'reform-navigation.png', 'shared-host-3.png',
                     'lootr-styled-entry-controls.json', 'lootr-styled-ui-observations.json'):
            (self.fixture / name).write_bytes(b'Previous capture')
        (self.fixture / 'operator-reference.png').write_bytes(b'Keep reference')
        native_probe.prepare('neoforge-1_21_1', 'native')
        for name in ('reform-home.png', 'reform-navigation.png', 'shared-host-3.png',
                     'lootr-styled-entry-controls.json', 'lootr-styled-ui-observations.json'):
            self.assertFalse((self.fixture / name).exists(), name)
        self.assertEqual(b'Keep reference', (self.fixture / 'operator-reference.png').read_bytes())

    def test_graphics_scope_is_explicit_and_cleared_by_ordinary_preparation(self):
        marker = self.fixture / '.lootr-styled-graphic-probe'
        native_probe.prepare('neoforge-1_21_1', 'native', graphics_only=True)
        self.assertTrue(marker.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())
        host = self.fixture / '.lootr-styled-host-probe'
        native_probe.prepare('neoforge-1_21_1', 'native', preview_host=True)
        self.assertTrue(host.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(host.exists())
        production = self.fixture / '.lootr-styled-production-shutdown'
        native_probe.prepare('neoforge-1_21_1', 'native', production_shutdown=True)
        self.assertTrue(production.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(production.exists())
        viewport = self.fixture / '.lootr-styled-production-viewport'
        native_probe.prepare('neoforge-1_21_1', 'native', production_viewport=True)
        self.assertTrue(viewport.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(viewport.exists())

    def test_production_close_scope_preserves_other_selected_inputs(self):
        for scope in ('scene', 'graphics_only', 'preview_host', 'production_shutdown', 'production_viewport'):
            with self.subTest(scope=scope), self.assertRaisesRegex(ValueError, 'Choose one check'):
                native_probe.prepare('neoforge-1_21_1', 'native', production_close=True, **{scope: True})
        self.assertEqual('Preserve running client input', self.settings.read_text())
        marker = self.fixture / '.lootr-styled-production-close'
        native_probe.prepare('neoforge-1_21_1', 'native', production_close=True)
        self.assertTrue(marker.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())

    def test_production_effects_scope_preserves_inputs_and_clears_its_marker(self):
        for scope in ('scene', 'graphics_only', 'preview_host', 'production_shutdown', 'production_viewport', 'production_close'):
            with self.subTest(scope=scope), self.assertRaisesRegex(ValueError, 'Choose one check'):
                native_probe.prepare('neoforge-1_21_1', 'native', production_effects=True, **{scope: True})
        self.assertEqual('Preserve running client input', self.settings.read_text())
        marker = self.fixture / '.lootr-styled-production-effects'
        native_probe.prepare('neoforge-1_21_1', 'native', production_effects=True)
        self.assertTrue(marker.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())

    def test_production_effects_requires_persistence_then_a_distinct_no_write_retry(self):
        failed = dict(hasPersistenceReceipt=True, persistence='SAVED', application='FAILED',
                      code='NATIVE_FAILURE', savedRevision='saved', sourceRevision='saved', handle='first',
                      operation='lootr_styled:save_reload')
        retry = dict(hasPersistenceReceipt=False, application='APPLIED', sourceRevision='saved',
                     generation='new', handle='second', operation='lootr_styled:retry')
        value = dict(saveOnlyDoesNotReload=True, confirmedSaveSurvivesFailedReload=True,
                     retryUsesSavedRevisionWithoutPersistence=True, retryPreservesSavedFileIdentity=True,
                     originalSavedValuesRestored=True, sameOwnerAndRoot=True, closedCleanly=True,
                     injectedFailures=1, failedSaveReload=failed, retryReload=retry)
        native_probe.require_production_effects({'productionEffects': value})
        for key, bad in [('saveOnlyDoesNotReload', False), ('injectedFailures', True),
                         ('originalSavedValuesRestored', False),
                         ('failedSaveReload', []),
                         ('failedSaveReload', {**failed, 'persistence': 'UNKNOWN'}),
                         ('retryReload', {**retry, 'sourceRevision': 'different'}),
                         ('retryReload', {**retry, 'hasPersistenceReceipt': True}),
                         ('retryReload', {**retry, 'handle': 'first'}),
                         ('retryReload', {**retry, 'operation': 'lootr_styled:save_reload'})]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production effects'):
                native_probe.require_production_effects({'productionEffects': {**value, key: bad}})

    def test_production_close_requires_both_releases_and_unsaved_choice_recovery(self):
        release = dict(released=True, outstandingUses=0, releaseFailed=False, nativeBytes=0, sceneBytes=0)
        value = dict(sameRootDecision=True, reviewChooserKeepsProfilesContext=True,
                     closeReviewCapturesSameDraft=True, reviewReturnRetainsOwner=True,
                     keepEditingRetainsDraft=True, noAutomaticPreparation=True,
                     discardClosesOwner=True, freshOwnerUsesSavedChoice=True, settingsUnchanged=True,
                     resourceGenerationUnchanged=True, explicitPreparations=2,
                     releasedNativeBytes=0, releasedSceneBytes=0, releases=[release, release])
        native_probe.require_production_close({'productionClose': value})
        for key, bad in [('sameRootDecision', False), ('freshOwnerUsesSavedChoice', False),
                         ('reviewChooserKeepsProfilesContext', False), ('closeReviewCapturesSameDraft', False),
                         ('reviewReturnRetainsOwner', False),
                         ('releasedNativeBytes', 1), ('explicitPreparations', 3),
                         ('releases', [release]), ('releases', [release, {**release, 'releaseFailed': True}])]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production close'):
                native_probe.require_production_close({'productionClose': {**value, key: bad}})

    def test_production_shutdown_cannot_replace_another_selected_scope(self):
        for scope in ('scene', 'graphics_only', 'preview_host'):
            with self.subTest(scope=scope), self.assertRaisesRegex(ValueError, 'Choose one check'):
                native_probe.prepare('neoforge-1_21_1', 'native', production_shutdown=True, **{scope: True})
        self.assertEqual('Preserve running client input', self.settings.read_text())

    def test_production_viewport_cannot_replace_another_selected_scope(self):
        for scope in ('scene', 'graphics_only', 'preview_host', 'production_shutdown'):
            with self.subTest(scope=scope), self.assertRaisesRegex(ValueError, 'Choose one check'):
                native_probe.prepare('neoforge-1_21_1', 'native', production_viewport=True, **{scope: True})
        self.assertEqual('Preserve running client input', self.settings.read_text())

    def test_conflicting_graphics_and_world_scopes_preserve_existing_evidence(self):
        with self.assertRaisesRegex(ValueError, 'Choose one check'):
            native_probe.prepare('neoforge-1_21_1', 'native', scene=True, graphics_only=True)
        self.assertEqual('Preserve running client input', self.settings.read_text())

    def test_preview_host_cannot_be_selected_with_another_graphics_scope(self):
        with self.assertRaisesRegex(ValueError, 'Choose one check'):
            native_probe.prepare('neoforge-1_21_1', 'native', preview_host=True, graphics_only=True)
        self.assertEqual('Preserve running client input', self.settings.read_text())

    def test_graphics_only_success_cannot_substitute_for_an_ordinary_ui_check(self):
        product = self.root / 'build/distributions/lootr-styled-1.21.1-neoforge-fixture.jar'
        (self.fixture / 'lootr-styled-probe-report.json').write_text(json.dumps({
            'passed': True, 'artifactSha256': native_probe.digest(product),
            'snapshots': [{}, {}], 'isolatedGraphic': {},
        }))
        with patch.object(native_probe, 'prepare', return_value=(self.fixture, product, ['not-run'])):
            with patch.object(native_probe.subprocess, 'Popen') as launch:
                launch.return_value.wait.return_value = 0
                with patch.object(sys, 'argv', ['native_probe.py', '--target', 'neoforge-1_21_1', '--run']):
                    with self.assertRaisesRegex(RuntimeError, 'Client did not pass'):
                        native_probe.main()

    def test_preview_host_success_before_shutdown_is_not_a_shutdown_result(self):
        product = self.root / 'build/distributions/lootr-styled-1.21.1-neoforge-fixture.jar'
        (self.fixture / 'lootr-styled-probe-report.json').write_text(json.dumps({
            'passed': True, 'artifactSha256': native_probe.digest(product),
            'libraryInputSha256': 'fixture', 'snapshots': [{}, {}], 'sharedGraphicHost': {},
        }))
        with patch.object(native_probe, 'prepare', return_value=(self.fixture, product, ['not-run'])):
            with patch.object(native_probe.subprocess, 'Popen') as launch:
                launch.return_value.wait.return_value = 0
                with patch.object(sys, 'argv', ['native_probe.py', '--target', 'neoforge-1_21_1', '--run', '--preview-host']):
                    with self.assertRaisesRegex(RuntimeError, 'Renderer shutdown'):
                        native_probe.main()

    def test_ordinary_ui_success_cannot_substitute_for_production_shutdown(self):
        product = self.root / 'build/distributions/lootr-styled-1.21.1-neoforge-fixture.jar'
        (self.fixture / 'lootr-styled-probe-report.json').write_text(json.dumps({
            'passed': True, 'artifactSha256': native_probe.digest(product),
            'libraryInputSha256': 'fixture', 'snapshots': [{}, {}],
        }))
        with patch.object(native_probe, 'prepare', return_value=(self.fixture, product, ['not-run'])):
            with patch.object(native_probe.subprocess, 'Popen') as launch:
                launch.return_value.wait.return_value = 0
                with patch.object(sys, 'argv', ['native_probe.py', '--target', 'neoforge-1_21_1', '--run', '--production-shutdown']):
                    with self.assertRaisesRegex(RuntimeError, 'Renderer shutdown'):
                        native_probe.main()

    def test_production_discard_requires_actual_use_retirement_and_fresh_owner(self):
        value = dict(releaseCompleted=True, released=True, oldOwnerClosed=True,
                     workflowRetained=True, settingsUnchanged=True, freshOwner=True,
                     outstandingUses=0, nativeBytes=0, sceneBytes=0,
                     pendingUsesAtDiscard=1, nativeBytesAtDiscard=64, sceneBytesAtDiscard=64)
        native_probe.require_production_discard({'rendererShutdown': {'productionDiscard': value}})
        for key, bad in [('freshOwner', False), ('oldOwnerClosed', False), ('settingsUnchanged', False),
                         ('nativeBytes', 1), ('sceneBytes', 1), ('outstandingUses', 1),
                         ('pendingUsesAtDiscard', 0), ('releaseCompleted', False)]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production discard'):
                native_probe.require_production_discard({'rendererShutdown': {'productionDiscard': {**value, key: bad}}})

    def test_production_viewport_requires_complete_controls_and_release(self):
        value = dict(cameraControlsReuseGraphic=True, realWindowReflowRetainsSceneAndInput=True,
                     settingsUnchanged=True, resourceGenerationUnchanged=True, releaseCompleted=True,
                     releasedNativeBytes=0, releasedSceneBytes=0,
                     frames=[{'phase': phase} for phase in ('prepared', 'rotated', 'zoomed', 'reset', 'narrow', 'returned')])
        native_probe.require_production_viewport({'productionViewport': value})
        for key, bad in [('releaseCompleted', False), ('releasedNativeBytes', 1),
                         ('resourceGenerationUnchanged', False), ('frames', []),
                         ('cameraControlsReuseGraphic', False)]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production camera/window'):
                native_probe.require_production_viewport({'productionViewport': {**value, key: bad}})

    def test_preview_shutdown_receipt_is_read_after_the_process_exits(self):
        product = self.root / 'build/distributions/lootr-styled-1.21.1-neoforge-fixture.jar'
        released = {'passed': True, 'releaseCompleted': True, 'released': True,
                    'outstandingUses': 0, 'nativeBytes': 0, 'nativeResources': 0,
                    'sceneBytes': 0, 'sceneResources': 0, 'workflowClosed': True, 'editorClosed': True}
        receipt = {'passed': True, 'artifactSha256': native_probe.digest(product),
                   'libraryInputSha256': 'fixture', 'rendererReturned': True, 'settingsUnchanged': True,
                   'workflowOpenAtStop': True, 'editorOpenAtStop': True, 'pendingUsesAtStop': 1, 'nativeBytesAtStop': 64,
                   'sceneBytesAtStop': 64, 'atRendererReturn': {**released, 'boundary': 'Minecraft.close'},
                   'afterShutdown': released}
        report = {'passed': True, 'artifactSha256': native_probe.digest(product),
                  'libraryInputSha256': 'fixture', 'snapshots': [{}, {}], 'sharedGraphicHost': {},
                  'rendererShutdown': receipt, 'previewCombinations': self._combinations(),
                  'previewAuthority': self._authority(), 'previewCleanupFailure': self._cleanup_failure()}
        report.update(self._cleanup_modes())
        report['previewReviewRequired'] = self._review()
        report['previewSelectionStates'] = self._selection_states()

        def publish_after_wait(**_):
            (self.fixture / 'lootr-styled-probe-report.json').write_text(json.dumps(report))
            (self.fixture / 'lootr-styled-shutdown-report.json').write_text(json.dumps(receipt))
            (self.fixture / 'lootr-styled-combinations-report.json').write_text(json.dumps(report['previewCombinations']))
            (self.fixture / 'lootr-styled-selection-states-report.json').write_text(json.dumps(report['previewSelectionStates']))
            return 0

        with patch.object(native_probe, 'prepare', return_value=(self.fixture, product, ['not-run'])):
            with patch.object(native_probe.subprocess, 'Popen') as launch:
                launch.return_value.wait.side_effect = publish_after_wait
                with patch.object(sys, 'argv', ['native_probe.py', '--target', 'neoforge-1_21_1', '--run', '--preview-host']):
                    with contextlib.redirect_stdout(io.StringIO()) as stdout:
                        native_probe.main()
                    self.assertEqual('preview-host', json.loads(stdout.getvalue())['scope'])

        for key, value in [('artifactSha256', 'old'), ('libraryInputSha256', 'old'),
                           ('rendererReturned', False), ('settingsUnchanged', False),
                           ('pendingUsesAtStop', 0), ('workflowOpenAtStop', False), ('editorOpenAtStop', False)]:
            with self.subTest(key=key):
                bad = copy.deepcopy(receipt)
                bad[key] = value
                self._refuse_shutdown(report, bad, product)
        for section in ('atRendererReturn', 'afterShutdown'):
            with self.subTest(section=section):
                bad = copy.deepcopy(receipt)
                bad[section]['releaseCompleted'] = False
                self._refuse_shutdown(report, bad, product)
        bad = copy.deepcopy(receipt)
        bad['afterShutdown']['workflowClosed'] = False
        self._refuse_shutdown(report, bad, product)

    def _combinations(self):
        ids = ('lootr_styled:default', 'lootr_styled:vanilla')
        return {'complete': True, 'failures': 0, 'settingsUnchanged': True,
                'rows': [{'models': m, 'artwork': a, 'container': c, 'looted': l, 'open': o,
                          'state': 'READY', 'drawn': True, 'retired': True, 'prepared': {'aoTriangles': 0}}
                         for m in ids for a in ids
                         for c in ('CHEST', 'TRAPPED_CHEST', 'BARREL') for l in (False, True)
                         for o in ((False, True) if c == 'BARREL' else (False,))]}

    def _authority(self):
        result = dict.fromkeys(('actualWriteDenied', 'readOnlyDomainDraw', 'permissionLossRetiresReadyLease',
                                'revokedReadRequestRefused', 'permissionLossWaitsForEnteredWork',
                                'revokedEnteredOutputDiscarded', 'retiredEditorExecutorCannotBlockGpuRelease',
                                'testAndRuntimeSettingsUnchanged', 'oldExecutorActuallyRejects'), True)
        return {**result, 'pendingUsesAtEditorRetirement': 1, 'unexpectedStoppedExecutorSubmissions': 0,
                'preparations': 3, 'revokedTerminal': 'CANCELLED'}

    def _cleanup_failure(self):
        return {'actualInjectedCause': True, 'releaseAttempted': True,
                'releaseCompleted': False, 'released': False, 'injected': 1,
                'outstandingUses': 0, 'pendingUsesAtClose': 1, 'nativeBytes': 0,
                'sceneBytes': 1024, 'hostFailureCount': 1, 'hostHasCause': True,
                'viewCodes': ['preview.release_failed'], 'settingsUnchanged': True}

    def test_deferred_cleanup_gate_needs_real_failure_visible_to_host_without_false_release(self):
        good = self._cleanup_failure()
        native_probe.require_preview_cleanup_failure({'previewCleanupFailure': good})
        for key, value in [('actualInjectedCause', False), ('releaseCompleted', True),
                           ('released', True), ('pendingUsesAtClose', 0), ('nativeBytes', 1),
                           ('sceneBytes', 0), ('hostFailureCount', 0), ('hostHasCause', False),
                           ('viewCodes', []), ('settingsUnchanged', False)]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Deferred preview cleanup'):
                native_probe.require_preview_cleanup_failure({'previewCleanupFailure': {**good, key: value}})
        with self.assertRaisesRegex(RuntimeError, 'Deferred preview cleanup'):
            native_probe.require_preview_cleanup_failure({})

    def _cleanup_modes(self):
        return {'previewCleanupSynchronous': {**self._cleanup_failure(), 'pendingUsesAtClose': 0,
                                             'completedDuringClose': True, 'viewState': 'CLOSED'},
                'previewCleanupReplacement': {**self._cleanup_failure(), 'viewState': 'READY',
                                             'nativeBytes': 1024, 'finalNativeBytes': 0,
                                             'finalSceneBytes': 1024, 'finalHostFailureCount': 1,
                                             'newerReady': True, 'newerCauseAbsent': True,
                                             'newerDisplayed': True, 'replacementReleased': True}}

    def _review(self):
        return {**dict.fromkeys(('writeActuallyAllowed', 'reviewBlocksSubmission', 'dirtyPreviewDraw',
                                'reviewGuardAndDraftPreserved', 'testAndRuntimeSettingsUnchanged'), True),
                'preparations': 1}

    def _selection_states(self):
        return {'complete': True, 'failures': 0, 'settingsUnchanged': True,
                'rows': [{'selectionCase': i, 'enabled': i >= 2, 'container': c, 'looted': l, 'open': o,
                          'prepared': {'visualFingerprint': 'a' * 64, 'expectedVisualFingerprint': 'a' * 64, 'visualComparison':
                                       'Disabled reference captured' if i == 0 else
                                       'Disabled reference matched' if i == 1 else 'Built-in reference matched'},
                          'state': 'READY', 'drawn': True, 'retired': True}
                         for i in range(6) for c in ('CHEST', 'TRAPPED_CHEST', 'BARREL') for l in (False, True)
                         for o in ((False, True) if c == 'BARREL' else (False,))]}

    def test_selection_state_gate_needs_complete_matching_draw_and_retirement_evidence(self):
        good = self._selection_states()
        side = self.fixture / 'lootr-styled-selection-states-report.json'
        side.write_text(json.dumps(good))
        native_probe.require_preview_selection_states({'previewSelectionStates': good}, self.fixture)
        cases = []
        for key, value in [('drawn', False), ('retired', False), ('state', 'REFUSED'), ('enabled', True),
                           ('selectionCase', False), ('open', 0)]:
            bad = copy.deepcopy(good)
            bad['rows'][0][key] = value
            cases.append(bad)
        cases.append({**good, 'rows': good['rows'][:-1]})
        bad = copy.deepcopy(good)
        bad['rows'][1]['prepared']['visualComparison'] = 'Unchecked'
        cases.append(bad)
        bad = copy.deepcopy(good)
        bad['rows'][0]['prepared']['visualFingerprint'] = ''
        cases.append(bad)
        bad = copy.deepcopy(good)
        bad['rows'][8]['prepared']['expectedVisualFingerprint'] = 'b' * 64
        cases.append(bad)
        for bad in cases:
            side.write_text(json.dumps(bad))
            with self.assertRaises(RuntimeError):
                native_probe.require_preview_selection_states({'previewSelectionStates': bad}, self.fixture)
        side.write_text(json.dumps({**good, 'settingsUnchanged': False}))
        with self.assertRaises(RuntimeError):
            native_probe.require_preview_selection_states({'previewSelectionStates': good}, self.fixture)

    def test_preview_read_does_not_satisfy_a_required_save_review(self):
        good = self._review()
        native_probe.require_preview_review({'previewReviewRequired': good})
        for key, value in [(k, False) for k in good if k != 'preparations'] + [('preparations', 2)]:
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                native_probe.require_preview_review({'previewReviewRequired': {**good, key: value}})
        with self.assertRaises(RuntimeError):
            native_probe.require_preview_review({})

    def test_cleanup_modes_keep_exact_once_and_the_actual_newer_outcome(self):
        good = self._cleanup_modes()
        native_probe.require_preview_cleanup_modes(good)
        for section, key, value in [('previewCleanupSynchronous', 'pendingUsesAtClose', 1),
                                    ('previewCleanupSynchronous', 'completedDuringClose', False),
                                    ('previewCleanupSynchronous', 'hostFailureCount', 2),
                                    ('previewCleanupSynchronous', 'viewState', 'FAILED'),
                                    ('previewCleanupReplacement', 'newerCauseAbsent', False),
                                    ('previewCleanupReplacement', 'newerDisplayed', False),
                                    ('previewCleanupReplacement', 'replacementReleased', False),
                                    ('previewCleanupReplacement', 'finalNativeBytes', 1024),
                                    ('previewCleanupReplacement', 'finalHostFailureCount', 2),
                                    ('previewCleanupReplacement', 'nativeBytes', 0),
                                    ('previewCleanupReplacement', 'viewState', 'FAILED')]:
            bad = copy.deepcopy(good)
            bad[section][key] = value
            with self.subTest(section=section, key=key), self.assertRaises(RuntimeError):
                native_probe.require_preview_cleanup_modes(bad)
        with self.assertRaises(RuntimeError):
            native_probe.require_preview_cleanup_modes({})

    def test_authority_witness_needs_revocation_and_real_outstanding_use_without_owner_resurrection(self):
        good = self._authority()
        native_probe.require_preview_authority({'previewAuthority': good})
        for key, value in [(k, False) for k, v in good.items() if type(v) is bool] + [
                ('pendingUsesAtEditorRetirement', 0), ('unexpectedStoppedExecutorSubmissions', 1),
                ('preparations', 4), ('revokedTerminal', 'READY')]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Preview authority'):
                native_probe.require_preview_authority({'previewAuthority': {**good, key: value}})
        with self.assertRaisesRegex(RuntimeError, 'Preview authority'):
            native_probe.require_preview_authority({})

    def test_preview_combinations_require_complete_independent_selections_and_retirement(self):
        original = self._combinations()
        (self.fixture / 'lootr-styled-combinations-report.json').write_text(json.dumps(original))
        native_probe.require_preview_combinations({'previewCombinations': original}, self.fixture)
        for defect in ('missing', 'duplicate', 'not_drawn', 'not_retired', 'world_ao', 'stale'):
            bad = copy.deepcopy(original)
            if defect == 'missing':
                bad['rows'].pop()
            elif defect == 'duplicate':
                bad['rows'][0] = copy.deepcopy(bad['rows'][1])
            elif defect == 'not_drawn':
                bad['rows'][0]['drawn'] = False
            elif defect == 'not_retired':
                bad['rows'][0]['retired'] = False
            elif defect == 'world_ao':
                bad['rows'][0]['prepared']['aoTriangles'] = 12
            if defect != 'stale':
                (self.fixture / 'lootr-styled-combinations-report.json').write_text(json.dumps(bad))
            with self.subTest(defect=defect), self.assertRaisesRegex(RuntimeError, 'Preview combinations'):
                native_probe.require_preview_combinations({'previewCombinations': bad if defect != 'stale' else original}, self.fixture)

    def _refuse_shutdown(self, report, receipt, product):
        report = {**report, 'rendererShutdown': receipt}
        (self.fixture / 'lootr-styled-shutdown-report.json').write_text(json.dumps(receipt))
        with self.assertRaisesRegex(RuntimeError, 'Renderer shutdown'):
            native_probe.require_renderer_shutdown(report, self.fixture, product, 'neoforge-1_21_1')

    def test_invalid_sprite_expectations_preserve_settings_and_previous_evidence(self):
        report = self.fixture / 'lootr-styled-probe-report.json'
        report.write_text('Previous run')
        with self.assertRaisesRegex(ValueError, 'requires a local profile'):
            native_probe.prepare('neoforge-1_21_1', 'native', expected_private_sprites=4)
        self.assertEqual('Preserve running client input', self.settings.read_text())
        self.assertEqual('Previous run', report.read_text())

    def test_out_of_range_sprite_expectations_do_not_create_a_profile_fixture(self):
        profile = self.root / 'source-profile'
        profile.mkdir()
        (profile / 'profile.json').write_text('{"format":1}')
        for count in (-1, 129):
            with self.subTest(count=count):
                with self.assertRaisesRegex(ValueError, 'range 0..128'):
                    native_probe.prepare('neoforge-1_21_1', 'native', local_profile=profile,
                                         expected_private_sprites=count)
                self.assertFalse((self.fixture.parent / 'native-profile').exists())

    def test_valid_sprite_expectation_prepares_the_selected_profile(self):
        profile = self.root / 'source-profile'
        profile.mkdir()
        (profile / 'profile.json').write_text('{"format":1}')
        fixture, _, _ = native_probe.prepare('neoforge-1_21_1', 'native', local_profile=profile,
                                              expected_private_sprites=4)
        self.assertEqual('4', (fixture / '.lootr-styled-expected-private-sprites').read_text())
        self.assertEqual('{"format":1}', (fixture / 'config/lootr_styled/fixture/profile.json').read_text())

    def test_zero_exit_with_a_different_products_success_report_is_refused(self):
        product = self.root / 'build/distributions/lootr-styled-1.21.1-neoforge-fixture.jar'
        (self.fixture / 'lootr-styled-probe-report.json').write_text(json.dumps({
            'passed': True, 'artifactSha256': 'previous product', 'snapshots': [{}, {}],
        }))
        with patch.object(native_probe, 'prepare', return_value=(self.fixture, product, ['not-run'])):
            with patch.object(native_probe.subprocess, 'Popen') as launch:
                launch.return_value.wait.return_value = 0
                with patch.object(sys, 'argv', ['native_probe.py', '--target', 'neoforge-1_21_1', '--run']):
                    with self.assertRaisesRegex(RuntimeError, 'Client did not pass'):
                        native_probe.main()

    @unittest.skipUnless(os.name == 'posix', 'POSIX fixture link refusal')
    def test_a_linked_profile_directory_is_refused_before_copying_or_rewriting(self):
        profile = self.root / 'source-profile'
        profile.mkdir()
        (profile / 'outside').symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'file/byte policy'):
            native_probe.prepare('neoforge-1_21_1', 'native', local_profile=profile)
        self.assertEqual('Preserve running client input', self.settings.read_text())
        self.assertFalse((self.root / 'verification/neoforge-1_21_1/runs/native-profile').exists())
