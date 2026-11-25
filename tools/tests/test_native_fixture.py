"""Fixture preparation must not rewrite a running client's input files."""

import os
import copy
import contextlib
import io
import json
import hashlib
import zipfile
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


    def runtime_artifact_facts(self):
        facts = {}
        for mod, members in {
            'lootr_styled': ('design/inherent/lootr_styled/ResourceId.class',
                             'design/inherent/lootr_styled/client/ClientRuntime.class',
                             'design/inherent/lootr_styled/client/StylePreferences.class'),
            'reform': ('design/inherent/reform/minecraft/client/ClientFileSettings.class',
                       'design/inherent/reform/minecraft/client/ClientEditingWorkflow.class',
                       'design/inherent/reform/minecraft/client/ClientGraphicHost.class'),
        }.items():
            relative = f'runtime-artifacts/{mod}.jar'
            path = self.fixture / relative
            path.parent.mkdir(exist_ok=True)
            witnesses = []
            with zipfile.ZipFile(path, 'w') as archive:
                for member in members:
                    # Symbolic class bytes exercise the receipt verifier, not JVM class loading.
                    content = ('Fixture class resource: ' + member).encode()
                    archive.writestr(member, content)
                    witnesses.append(dict(member=member, sha256=hashlib.sha256(content).hexdigest(),
                                          resource='jar:' + path.as_uri() + '!/' + member,
                                          resourceMatchesArchive=True))
            facts[mod] = dict(copy=relative, path=str(path), bytes=path.stat().st_size,
                              sha256=native_probe.digest(path), witnesses=witnesses)
        return facts

    def test_runtime_archives_keep_loaded_bytes_distinct_from_original_inputs(self):
        facts = self.runtime_artifact_facts()
        native_probe.require_runtime_artifacts(dict(runtimeArtifacts=facts,
            artifactSha256='expected-original-before-remap', libraryInputSha256='expected-library'), self.fixture)

    def test_runtime_archives_refuse_missing_or_misidentified_copies_and_classes(self):
        facts = self.runtime_artifact_facts()
        for bad in (None, {}, {'reform': facts['reform']}, {**facts, 'foreign': facts['reform']}):
            with self.subTest(value=bad), self.assertRaisesRegex(RuntimeError, 'Loaded runtime'):
                native_probe.require_runtime_artifacts({'runtimeArtifacts': bad}, self.fixture)
        for key, bad in [('copy', '../outside.jar'), ('bytes', True), ('bytes', 0),
                         ('bytes', 64 * 1024 * 1024 + 1), ('sha256', 'other'), ('path', ''),
                         ('witnesses', []), ('witnesses', [facts['reform']['witnesses'][0]] * 3)]:
            broken = copy.deepcopy(facts)
            broken['reform'][key] = bad
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Loaded runtime'):
                native_probe.require_runtime_artifacts({'runtimeArtifacts': broken}, self.fixture)
        for key, bad in [('sha256', 'other'), ('resourceMatchesArchive', False),
                         ('member', 'foreign.class'), ('resource', '')]:
            broken = copy.deepcopy(facts)
            broken['reform']['witnesses'][0][key] = bad
            with self.subTest(witness=key), self.assertRaisesRegex(RuntimeError, 'Loaded runtime'):
                native_probe.require_runtime_artifacts({'runtimeArtifacts': broken}, self.fixture)

    def test_runtime_archive_copy_corruption_cannot_reuse_a_good_receipt(self):
        facts = self.runtime_artifact_facts()
        path = self.fixture / facts['reform']['copy']
        path.write_bytes(b'Corrupt archive')
        with self.assertRaisesRegex(RuntimeError, 'copy/hash'):
            native_probe.require_runtime_artifacts({'runtimeArtifacts': facts}, self.fixture)
        facts['reform'].update(bytes=path.stat().st_size, sha256=native_probe.digest(path))
        with self.assertRaisesRegex(RuntimeError, 'cannot supply'):
            native_probe.require_runtime_artifacts({'runtimeArtifacts': facts}, self.fixture)

    def test_expected_hash_markers_cannot_replace_loaded_archive_evidence_in_main(self):
        product = self.root / 'build/distributions/lootr-styled-1.21.1-neoforge-fixture.jar'
        report = dict(passed=True, artifactSha256=native_probe.digest(product),
                      libraryInputSha256='fixture', snapshots=[{}, {}], sharedGraphicHost={})
        (self.fixture / 'lootr-styled-probe-report.json').write_text(json.dumps(report))
        with patch.object(native_probe, 'prepare', return_value=(self.fixture, product, ['not-run'])):
            with patch.object(native_probe.subprocess, 'Popen') as launch:
                launch.return_value.wait.return_value = 0
                with patch.object(sys, 'argv', ['native_probe.py', '--target', 'neoforge-1_21_1', '--run', '--preview-host']):
                    with self.assertRaisesRegex(RuntimeError, 'Loaded runtime artifacts'):
                        native_probe.main()

    def test_preparation_removes_only_stopped_fixture_runtime_copies(self):
        directory = self.fixture / 'runtime-artifacts'
        directory.mkdir()
        saved = directory / 'reform.jar'
        saved.write_bytes(b'Keep while live')
        (self.fixture / '.lootr-styled-probe-pid').write_text(str(os.getpid()))
        with self.assertRaisesRegex(ValueError, 'before preparing'):
            native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertEqual(saved.read_bytes(), b'Keep while live')
        (self.fixture / '.lootr-styled-probe-pid').unlink()
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(directory.exists())


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

    def test_production_document_scope_and_complete_guarded_result(self):
        for scope in ('scene', 'graphics_only', 'preview_host', 'production_shutdown',
                      'production_viewport', 'production_close', 'production_effects'):
            with self.subTest(scope=scope), self.assertRaisesRegex(ValueError, 'Choose one check'):
                native_probe.prepare('neoforge-1_21_1', 'native', production_document=True, **{scope: True})
        self.assertEqual('Preserve running client input', self.settings.read_text())
        marker = self.fixture / '.lootr-styled-production-document'
        native_probe.prepare('neoforge-1_21_1', 'native', production_document=True)
        self.assertTrue(marker.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())
        keys = ('conflictSameOwnerRoot', 'comparedModelsAndArtwork', 'resolutionDoesNotSave',
                'importNoImplicitPreview', 'importValueFormatDistinctFromNativeFile',
                'staleImportTargetRefused', 'wholeDraftInvalidImportRetained',
                'undoRestoresCompleteDraft', 'originalSavedValuesRestored',
                'resourceGenerationUnchanged', 'closedCleanly')
        value = dict.fromkeys(keys, True)
        native_probe.require_production_document({'productionDocument': value})
        for key in keys:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production document'):
                native_probe.require_production_document({'productionDocument': {**value, key: False}})

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

    def test_filtered_document_actions_cover_all_fields_without_write(self):
        defaults = dict(enabled='true',models='lootr_styled:default',artwork='lootr_styled:default',freshObjects='AUTO')
        staged = dict(enabled='false',models='lootr_styled:vanilla',artwork='lootr_styled:vanilla',freshObjects='OFF')
        facts = dict(query='Vanilla',hiddenModelAndArtworkControls=True,wholeDocumentDefaultsAndUndo=True,
                     sameOwnerAndNoWrite=True,searchRemovedDefaultChoice=True,stagedWholeDraft=staged,undoneWholeDraft=staged,
                     defaultWholeDraft=defaults,restoredWholeDraft=defaults)
        value = dict(baselineWholeDraft=defaults,filteredActions=facts)
        native_probe.require_filtered_actions({'productionInput':value})
        for key,replacement in [('query',''),('hiddenModelAndArtworkControls',False),
                                ('sameOwnerAndNoWrite',False),('undoneWholeDraft',defaults),
                                ('defaultWholeDraft',staged),('restoredWholeDraft',staged)]:
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'Filtered document actions'):
                native_probe.require_filtered_actions({'productionInput':{**value,'filteredActions':{**facts,key:replacement}}})

    def test_production_locale_scope_is_exclusive_and_cleared(self):
        marker = self.fixture / '.lootr-styled-production-locale'
        native_probe.prepare('neoforge-1_21_1','native',production_locale=True)
        self.assertTrue(marker.is_file())
        with self.assertRaisesRegex(ValueError,'Choose one'):
            native_probe.prepare('neoforge-1_21_1','native',production_locale=True,production_input=True)
        native_probe.prepare('neoforge-1_21_1','native')
        self.assertFalse(marker.exists())

    def test_production_locale_requires_native_witness_and_complete_retained_input(self):
        baseline=dict(enabled='true',models='lootr_styled:default',artwork='lootr_styled:default',freshObjects='AUTO')
        invalid={**baseline,'enabled':'false','models':'bad id'}
        def state(phase,lang,unicode,done,widths,generation):
            return dict(phase=phase,language=lang,unicode=unicode,doneLabel=done,fontWidths=widths,
                        generation=generation,draft=invalid,canSubmit=False,text='bad id',caret=3,
                        selection='ad',modelFocus=True)
        states=[state('original','en_us',False,'Done',[8,16,20,8],'a'),
                state('changed','de_de',True,'Fertig',[16,24,28,10],'b'),
                state('restored','en_us',False,'Done',[8,16,20,8],'c')]
        value=dict(states=states,baselineWholeDraft=baseline,invalidWholeDraft=invalid,
                   restoredWholeDraft=baseline,localeRoundTripRetainsWholeDraft=True,
                   caretSelectionFocusAndSaveGuardRetained=True,originalLocaleRestored=True,
                   settingsUnchanged=True,sameOwnerUntilClose=True,closedCleanly=True)
        native_probe.require_production_locale({'productionLocale':value})
        for key,replacement in [('modelFocus',False),('caret',0),('language','en_us'),('unicode',False),
                                ('doneLabel','Done'),('fontWidths',[8,16,20,8]),('generation','a'),('draft',baseline)]:
            bad=copy.deepcopy(value);bad['states'][1][key]=replacement
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'Production locale'):
                native_probe.require_production_locale({'productionLocale':bad})
        for states in [[],[None]*3]:
            with self.assertRaisesRegex(RuntimeError,'Production locale'):
                native_probe.require_production_locale({'productionLocale':{**value,'states':states}})

    def test_production_operation_scope_is_exclusive_and_cleared(self):
        marker = self.fixture / '.lootr-styled-production-operation'
        native_probe.prepare('neoforge-1_21_1', 'native', production_operation=True)
        self.assertTrue(marker.is_file())
        with self.assertRaisesRegex(ValueError, 'Choose one'):
            native_probe.prepare('neoforge-1_21_1', 'native', production_operation=True, production_effects=True)
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())

    def test_production_operation_requires_same_running_handle_and_actual_application(self):
        def state(phase, size):
            return dict(phase=phase, window=size, gui=size, handle='original-handle',
                        operation='lootr_styled:reload', state='RUNNING', holding=True,
                        requests=1, releases=0, nativeEntryAttempts=0, technicalExpanded=True)
        value = dict(runningHandleAndDisclosureRetained=True, oneActualReloadAfterRelease=True,
                     sourceFileUnchanged=True, sameDraftAndOwner=True, closedCleanly=True,
                     savedRevision='saved', oldGeneration='old',
                     states=[state('running','854x480'),state('narrow','320x240'),state('returned','854x480')],
                     completedReload=dict(handle='original-handle',operation='lootr_styled:reload',
                                          hasPersistenceReceipt=False,application='APPLIED',
                                          sourceRevision='saved',generation='new'))
        native_probe.require_production_operation({'productionOperation': value})
        for key, replacement in [('handle','replacement'),('state','COMPLETED'),('holding',False),
                                 ('nativeEntryAttempts',1),('technicalExpanded',False),('window','854x480')]:
            bad = copy.deepcopy(value)
            bad['states'][1][key] = replacement
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError,'Production operation'):
                native_probe.require_production_operation({'productionOperation':bad})
        for key, replacement in [('handle','replacement'),('hasPersistenceReceipt',True),
                                 ('application','FAILED'),('sourceRevision','different'),('generation','old')]:
            bad = copy.deepcopy(value)
            bad['completedReload'][key] = replacement
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError,'Production operation'):
                native_probe.require_production_operation({'productionOperation':bad})
        for states in [[],[None]*3]:
            with self.assertRaisesRegex(RuntimeError,'Production operation'):
                native_probe.require_production_operation({'productionOperation':{**value,'states':states}})

    def test_production_chooser_scope_is_exclusive_and_cleared(self):
        marker = self.fixture / '.lootr-styled-production-chooser'
        native_probe.prepare('neoforge-1_21_1', 'native', production_chooser=True)
        self.assertTrue(marker.is_file())
        with self.assertRaisesRegex(ValueError, 'Choose one'):
            native_probe.prepare('neoforge-1_21_1', 'native', production_chooser=True, production_input=True)
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())

    def test_production_chooser_requires_inspection_without_staging(self):
        def state(phase, size):
            return dict(phase=phase, window=size, gui=size, query='Vanilla', caret=3, selection='an',
                        queryFocus=True, inspected='lootr_styled:vanilla',
                        models='lootr_styled:default', artwork='lootr_styled:default')
        states = [state('inspected','854x480'), state('narrow','320x240'), state('returned','854x480')]
        value = dict(searchRemovedDefaultChoice=True, queryAndInspectionSurviveWindowReflow=True,
                     inspectionDoesNotStage=True, sameDraftAndOwner=True,
                     sourceFileAndGenerationUnchanged=True, closedCleanly=True,
                     baselineModels='lootr_styled:default', baselineArtwork='lootr_styled:default', states=states)
        native_probe.require_production_chooser({'productionChooser': value})
        for bad in [[], [None]*3, [states[0],{**states[1],'queryFocus':False},states[2]],
                    [states[0],states[1],{**states[2],'models':'lootr_styled:vanilla'}],
                    [states[0],{**states[1],'inspected':'lootr_styled:default'},states[2]]]:
            with self.subTest(states=bad), self.assertRaisesRegex(RuntimeError,'Production chooser'):
                native_probe.require_production_chooser({'productionChooser': {**value,'states':bad}})
        with self.assertRaisesRegex(RuntimeError,'Production chooser'):
            native_probe.require_production_chooser({'productionChooser': {**value,'inspectionDoesNotStage':False}})

    def test_production_chooser_matches_authored_long_title_and_narration(self):
        title = ('Woodland containers with alternating polished trim and dark oak panels ' * 3)[:160]
        choice = dict(id='lootr_styled_local:fixture', title=title, query=title[:32])
        def state(phase, window):
            return dict(phase=phase, window=window, gui=window, query=choice['query'], caret=3,
                        selection=choice['query'][1:3], queryFocus=True, inspected=choice['id'],
                        models=choice['id'], artwork=choice['id'])
        value = dict(searchRemovedDefaultChoice=True, queryAndInspectionSurviveWindowReflow=True,
                     inspectionDoesNotStage=True, sameDraftAndOwner=True,
                     sourceFileAndGenerationUnchanged=True, closedCleanly=True,
                     baselineModels=choice['id'], baselineArtwork=choice['id'],
                     inspectedTitle=title, inspectedId=choice['id'], queryText=choice['query'],
                     inspectedChoiceNarration='Button ' + title,
                     states=[state('inspected','854x480'), state('narrow','320x240'),
                             state('returned','854x480')])
        native_probe.require_production_chooser({'productionChooser': value}, choice)
        for key, replacement in [('inspectedTitle', title[:-1]), ('inspectedId','lootr_styled:default'),
                                 ('queryText','Vanilla'), ('inspectedChoiceNarration',title[:80])]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError,'Production chooser'):
                native_probe.require_production_chooser({'productionChooser': {**value,key:replacement}}, choice)
        stale = copy.deepcopy(value)
        stale['states'][1]['inspected'] = 'lootr_styled:vanilla'
        with self.assertRaisesRegex(RuntimeError,'Production chooser'):
            native_probe.require_production_chooser({'productionChooser':stale}, choice)

    def test_fresh_captions_require_native_cycle_and_canonical_review(self):
        modes = ['AUTO', 'ON', 'OFF', 'AUTO']
        labels = dict(AUTO='Auto', ON='On', OFF='Off')
        value = dict(freshKeyboardCyclePreservesSource=True, reviewFreshCanonical='AUTO',
                     reviewFreshCaption='Auto', freshChoiceCaptions=[
                         dict(canonical=mode,caption=labels[mode],narration='Fresh Objects: '+labels[mode])
                         for mode in modes])
        native_probe.require_fresh_captions(dict(nativeWidgets=value))
        for key,replacement in [('freshKeyboardCyclePreservesSource',False),
                                ('reviewFreshCanonical','ON'),('reviewFreshCaption','AUTO')]:
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'Fresh Objects'):
                native_probe.require_fresh_captions(dict(nativeWidgets={**value,key:replacement}))
        for key,replacement in [('canonical','ON'),('caption','AUTO'),('narration','raw ON')]:
            bad = copy.deepcopy(value)
            bad['freshChoiceCaptions'][0][key] = replacement
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'Fresh Objects'):
                native_probe.require_fresh_captions(dict(nativeWidgets=bad))
        with self.assertRaisesRegex(RuntimeError,'Fresh Objects'):
            native_probe.require_fresh_captions(dict(nativeWidgets={**value,'freshChoiceCaptions':[]}))

    def test_production_failure_is_explicit_and_keeps_saved_defaults(self):
        with self.assertRaisesRegex(ValueError, 'requires a local tint profile'):
            native_probe.prepare('neoforge-1_21_1','native',production_failure=True)
        profile = self.root / 'tint-profile'
        profile.mkdir()
        (profile / 'profile.json').write_text('{"format":1,"title":"Tinted preview fixture"}')
        local, _, _ = native_probe.prepare('neoforge-1_21_1','native',local_profile=profile,
                                          production_failure=True,expected_private_sprites=0)
        self.assertTrue((local / '.lootr-styled-production-failure').is_file())
        settings = json.loads((local / 'config/lootr_styled/settings.json').read_text())
        self.assertEqual('lootr_styled:default',settings['models'])
        self.assertEqual('lootr_styled:default',settings['artwork'])
        with self.assertRaisesRegex(ValueError,'Choose one'):
            native_probe.prepare('neoforge-1_21_1','native',local_profile=profile,
                                 production_failure=True,production_chooser=True)
        native_probe.prepare('neoforge-1_21_1','native',local_profile=profile)
        self.assertFalse((local / '.lootr-styled-production-failure').exists())

    def test_production_failure_requires_specific_cause_and_explicit_recovery(self):
        value = dict(specificDomainRefusal=True,failedPreparationLeavesNoGraphic=True,
                     readableFailureCause=True,failedDraftRetained=True,
                     explicitRecoveryDrawAndRelease=True,sourceFileAndGenerationUnchanged=True,
                     closedCleanly=True,failureState='REFUSED',
                     failureCodes=['lootr_styled.preview.tint_unavailable'],requests=2,
                     expectedFailureMessage='Unresolved tint; choose another model.',
                     failureFeedback='Preview refused. Unresolved tint; choose another model.',
                     nativeBytes=0,nativeResources=0,sceneBytes=0,sceneResources=0)
        native_probe.require_production_failure({'productionFailure':value})
        for key,replacement in [('failureState','READY'),('failureCodes',['preview.prepare_failed']),
                                ('requests',1),('nativeBytes',1),('sceneResources',1),
                                ('failureFeedback','Preview request refused'),('expectedFailureMessage',''),
                                ('readableFailureCause',False),('failedDraftRetained',False),
                                ('explicitRecoveryDrawAndRelease',False)]:
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'Production preview failure'):
                native_probe.require_production_failure({'productionFailure':{**value,key:replacement}})

    def test_local_and_standard_fixture_inputs_stay_isolated(self):
        profile = self.root / 'private-profile'
        profile.mkdir()
        (profile / 'profile.json').write_text('{"format":1,"title":"Fixture"}')
        local, _, _ = native_probe.prepare('neoforge-1_21_1', 'native', local_profile=profile,
                                          production_chooser=True, expected_private_sprites=0)
        self.assertNotEqual(local, self.fixture)
        marker = local / '.lootr-styled-local-profile'
        copied = local / 'config/lootr_styled/fixture'
        self.assertTrue(marker.is_file())
        self.assertTrue(copied.is_dir())
        settings = json.loads((local / 'config/lootr_styled/settings.json').read_text())
        self.assertEqual('lootr_styled:default', settings['models'])
        self.assertEqual('lootr_styled:default', settings['artwork'])
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertTrue(marker.is_file())
        self.assertTrue(copied.is_dir())
        self.assertFalse((self.fixture / '.lootr-styled-local-profile').exists())
        self.assertTrue((profile / 'profile.json').is_file())

    def test_production_input_scope_is_exclusive_and_cleared(self):
        marker = self.fixture / '.lootr-styled-production-input'
        native_probe.prepare('neoforge-1_21_1', 'native', production_input=True)
        self.assertTrue(marker.is_file())
        with self.assertRaisesRegex(ValueError, 'Choose one'):
            native_probe.prepare('neoforge-1_21_1', 'native', production_input=True, production_effects=True)
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())

    def test_production_input_requires_complete_retained_native_state(self):
        draft = dict(enabled='false', models='bad id', artwork='lootr_styled:default', freshObjects='AUTO')
        baseline = {**draft, 'enabled': 'true', 'models': 'lootr_styled:default'}
        def state(phase, window, gui):
            return dict(phase=phase, window=window, gui=gui, draft=draft, canSubmit=False,
                        text='bad id', caret=3, selection='ad', modelFocus=True)
        states = [state('invalid', '854x480', '854x480'), state('narrow', '320x240', '320x240'),
                  state('returned', '854x480', '854x480')]
        value = dict(invalidInputReflowKeepsWholeDraft=True, invalidInputReflowKeepsCaretSelectionFocus=True,
                     repairRestoresOriginalValuesWithoutSaving=True, settingsUnchanged=True,
                     resourceGenerationUnchanged=True, sameOwnerUntilClose=True, closedCleanly=True,
                     invalidWholeDraft=draft, baselineWholeDraft=baseline, restoredWholeDraft=baseline,
                     states=states, editToInvalidGuardNanos=1000)
        native_probe.require_production_input({'productionInput': value})
        for bad in [[], [None]*3, [states[0], {**states[1], 'caret': 6}, states[2]],
                    [states[0], {**states[1], 'modelFocus': False}, states[2]],
                    [states[0], states[1], {**states[2], 'draft': {**draft, 'enabled': 'true'}}]]:
            with self.subTest(states=bad), self.assertRaisesRegex(RuntimeError, 'Production input'):
                native_probe.require_production_input({'productionInput': {**value, 'states': bad}})
        with self.assertRaisesRegex(RuntimeError, 'Production input'):
            native_probe.require_production_input({'productionInput': {**value, 'closedCleanly': False}})

    def test_production_effects_requires_each_capture_to_complete(self):
        # File presence is a completion gate, not image-content or rendering certification.
        for name in native_probe.EFFECT_CAPTURES:
            (self.fixture / (name + '.png')).write_bytes(b'completed fixture capture')
        native_probe.require_effect_captures(self.fixture)
        for name in native_probe.EFFECT_CAPTURES:
            path = self.fixture / (name + '.png')
            path.unlink()
            with self.subTest(name=name), self.assertRaisesRegex(RuntimeError, 'capture'):
                native_probe.require_effect_captures(self.fixture)
            path.write_bytes(b'')
            with self.subTest(name=name, empty=True), self.assertRaisesRegex(RuntimeError, 'capture'):
                native_probe.require_effect_captures(self.fixture)
            path.write_bytes(b'completed fixture capture')

    def test_production_effects_requires_persistence_then_a_distinct_no_write_retry(self):
        failed = dict(hasPersistenceReceipt=True, persistence='SAVED', application='FAILED',
                      code='NATIVE_FAILURE', savedRevision='saved', sourceRevision='saved', handle='first',
                      operation='lootr_styled:save_reload')
        retry = dict(hasPersistenceReceipt=False, application='APPLIED', sourceRevision='saved',
                     generation='new', handle='second', operation='lootr_styled:retry',
                     technicalDetails=True, detailCount=12, matchesCurrentResourceTrace=True)
        inspection = dict(hasPersistenceReceipt=False, sourceRevision='saved', generation='new',
                          operation='lootr_styled:details', technicalDetails=False, detailCount=12,
                          matchesCurrentResourceTrace=True)
        value = dict(saveOnlyDoesNotReload=True, confirmedSaveSurvivesFailedReload=True,
                     retryUsesSavedRevisionWithoutPersistence=True, retryPreservesSavedFileIdentity=True,
                     originalSavedValuesRestored=True, sameOwnerAndRoot=True, closedCleanly=True,
                     failedDisclosurePreservesHandleFileAndEffects=True,
                     retryDisclosurePreservesHandleFileAndEffects=True,
                     routineTraceTechnical=True, resourceInspectionKeepsTraceVisible=True,
                     injectedFailures=1, failedSaveReload=failed, retryReload=retry,
                     resourceInspection=inspection)
        value['cancellation'] = self.cancellation_facts()
        native_probe.require_production_effects({'productionEffects': value})
        for key, bad in [('saveOnlyDoesNotReload', False), ('injectedFailures', True),
                         ('originalSavedValuesRestored', False),
                         ('failedDisclosurePreservesHandleFileAndEffects', False),
                         ('retryDisclosurePreservesHandleFileAndEffects', False),
                         ('routineTraceTechnical', False), ('resourceInspectionKeepsTraceVisible', False),
                         ('failedSaveReload', []),
                         ('failedSaveReload', {**failed, 'persistence': 'UNKNOWN'}),
                         ('retryReload', {**retry, 'sourceRevision': 'different'}),
                         ('retryReload', {**retry, 'hasPersistenceReceipt': True}),
                         ('retryReload', {**retry, 'handle': 'first'}),
                         ('retryReload', {**retry, 'operation': 'lootr_styled:save_reload'})]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production effects'):
                native_probe.require_production_effects({'productionEffects': {**value, key: bad}})
        for bad in [None, [], {**inspection, 'technicalDetails': True},
                    {**inspection, 'matchesCurrentResourceTrace': False},
                    {**inspection, 'detailCount': True},
                    {**inspection, 'hasPersistenceReceipt': True},
                    {**inspection, 'generation': 'other'}]:
            with self.subTest(inspection=bad), self.assertRaisesRegex(RuntimeError, 'Production effects'):
                native_probe.require_production_effects({'productionEffects': {**value, 'resourceInspection': bad}})

    @staticmethod
    def cancellation_facts():
        cancelled = dict(operation='lootr_styled:save_reload', hasPersistenceReceipt=True,
                         persistence='SAVED', application='UNKNOWN', code='CANCELLED',
                         savedRevision='cancel-save', sourceRevision='cancel-save', handle='cancel')
        retry = dict(operation='lootr_styled:retry', hasPersistenceReceipt=False, application='APPLIED',
                     sourceRevision='cancel-save', handle='retry', generation='confirmed')
        restored = dict(operation='lootr_styled:save_reload', hasPersistenceReceipt=True, persistence='SAVED', application='APPLIED',
                        savedRevision='original', sourceRevision='original', generation='restored')
        return dict(requestRetainsPendingWork=True, escapeReturnsWithoutCancellingOwner=True,
                    pendingWorkPreventsWorkspaceClose=True, confirmedSaveAndUnknownApplication=True,
                    cancellationRequested=True, terminalPendingNoticeCleared=True, pendingReloadActionsBlocked=True, retryPreservesSavedFileIdentity=True,
                    originalSavedValuesRestored=True, nativeReloads=3,
                    pendingCloseNarration='Wait for pending operations before closing.',
                    terminalNarration='Saved; reload not confirmed',
                    publishedGenerationAfterCancellation='unconfirmed', cancelledSaveReload=cancelled,
                    retryAfterCancellation=retry, restoredSaveReload=restored,
                    detailsRetainSameOutcome=True, captureFrames=[
                        dict(name=name, expectedRootDrawn=True,
                             expectedScreen='design.inherent.reform.minecraft.client.ClientEditingWorkflow$Overview',
                             baselineRootDraw=10, requestedRootDraw=14, requestedLoop=20, completedLoop=21)
                        for name in ('production-effects-cancel-pending', 'production-effects-cancelled',
                                     'production-effects-cancelled-details')])

    def test_production_preparation_requires_entered_work_real_reload_and_explicit_recovery(self):
        rows = [dict(case=name, operation='actual-' + name, workerThread='actual-worker',
                     workerEntered=True, heldWithoutObsoleteDraw=True, workerExited=True,
                     terminalState='CANCELLED', terminalBeforeWorkerExit=False,
                     explicitRecoveryDrawn=True, recoveryReleased=True)
                for name in ('cancel', 'reload', 'hidden-return', 'close')]
        rows[0]['nativeCancellationRequested'] = True
        rows[1].update(generationBefore='old', generationAfter='new', recoveryGeneration='new')
        rows[2]['returnDoesNotPrepare'] = True
        value = dict(settingsUnchanged=True, savedFileIdentityUnchanged=True,
                     draftRetainedUntilClose=True, closedWorkflowDiscardsLateResult=True, localProfileFilesUnchanged=True, resourceGenerationPreservedOutsideReload=True,
                     actualResourceReloads=1, requests=7, nativeBytes=0, sceneBytes=0, cases=rows)
        native_probe.require_production_preparation(dict(productionPreparation=value))
        for key, bad in [('requests', True), ('requests', 8), ('actualResourceReloads', True), ('actualResourceReloads', 0), ('nativeBytes', False),
                         ('sceneBytes', 1), ('settingsUnchanged', False), ('localProfileFilesUnchanged', False), ('resourceGenerationPreservedOutsideReload', False), ('cases', rows[:-1])]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production preparation'):
                native_probe.require_production_preparation(dict(productionPreparation={**value, key: bad}))
        for index, key, bad in [(0, 'nativeCancellationRequested', False), (0, 'terminalBeforeWorkerExit', True),
                                (0, 'workerEntered', False), (1, 'generationAfter', 'old'),
                                (1, 'recoveryGeneration', 'old'), (2, 'returnDoesNotPrepare', False),
                                (2, 'explicitRecoveryDrawn', False), (3, 'terminalState', 'READY'),
                                (3, 'workerThread', ''), (3, 'operation', ''), (2, 'recoveryReleased', False)]:
            changed = copy.deepcopy(value)
            changed['cases'][index][key] = bad
            with self.subTest(index=index, key=key), self.assertRaisesRegex(RuntimeError, 'Production preparation'):
                native_probe.require_production_preparation(dict(productionPreparation=changed))

    def test_production_preparation_is_explicit_and_cleared_by_ordinary_prepare(self):
        native_probe.prepare('neoforge-1_21_1', 'native', production_preparation=True)
        marker = self.fixture / '.lootr-styled-production-preparation'
        self.assertTrue(marker.is_file())
        with self.assertRaisesRegex(ValueError, 'Choose one check'):
            native_probe.prepare('neoforge-1_21_1', 'native', production_preparation=True, production_viewport=True)
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())

    def test_capture_frames_requires_actual_root_draws_and_completion_order(self):
        rows = self.cancellation_facts()['captureFrames']
        native_probe.require_capture_frames(rows)
        for key, bad in [('expectedRootDrawn', False), ('expectedScreen', 'OtherScreen'),
                         ('baselineRootDraw', True), ('requestedRootDraw', 13),
                         ('requestedLoop', 12), ('completedLoop', 19), ('name', 'wrong')]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Capture'):
                native_probe.require_capture_frames([{**rows[0], key: bad}, *rows[1:]])
        for bad in (None, [], rows[:-1], [rows[0], rows[0], rows[2]]):
            with self.subTest(rows=bad), self.assertRaisesRegex(RuntimeError, 'Capture'):
                native_probe.require_capture_frames(bad)

    def test_production_cancellation_requires_saved_receipt_uncertainty_and_real_retry(self):
        value = self.cancellation_facts()
        native_probe.require_production_cancellation(value)
        for key in ('requestRetainsPendingWork', 'escapeReturnsWithoutCancellingOwner',
                    'pendingWorkPreventsWorkspaceClose', 'confirmedSaveAndUnknownApplication',
                    'cancellationRequested', 'terminalPendingNoticeCleared', 'pendingReloadActionsBlocked', 'retryPreservesSavedFileIdentity', 'originalSavedValuesRestored'):
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production cancellation'):
                native_probe.require_production_cancellation({**value, key: False})
        for key, bad in [('nativeReloads', True), ('nativeReloads', 2), ('pendingCloseNarration', ''),
                         ('terminalNarration', 'Wait for pending operations. Saved; reload not confirmed'),
                         ('terminalNarration', ''),
                         ('cancelledSaveReload', {**value['cancelledSaveReload'], 'hasPersistenceReceipt': False}),
                         ('cancelledSaveReload', {**value['cancelledSaveReload'], 'application': 'APPLIED'}),
                         ('cancelledSaveReload', {**value['cancelledSaveReload'], 'savedRevision': 'other'}),
                         ('retryAfterCancellation', {**value['retryAfterCancellation'], 'hasPersistenceReceipt': True}),
                         ('retryAfterCancellation', {**value['retryAfterCancellation'], 'handle': 'cancel'}),
                         ('retryAfterCancellation', {**value['retryAfterCancellation'], 'generation': 'unconfirmed'}),
                         ('restoredSaveReload', {**value['restoredSaveReload'], 'application': 'UNKNOWN'})]:
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'Production cancellation'):
                native_probe.require_production_cancellation({**value, key: bad})

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
            'runtimeArtifacts': self.runtime_artifact_facts(), 'libraryInputSha256': 'fixture', 'snapshots': [{}, {}], 'sharedGraphicHost': {},
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
            'runtimeArtifacts': self.runtime_artifact_facts(), 'libraryInputSha256': 'fixture', 'snapshots': [{}, {}],
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

    def test_production_cost_requires_real_callbacks_and_every_released_cycle(self):
        phases=['accepted-extreme-id','ordinary-id-restoration','profile-search','preview-cycle-1',
                'returned-profile-1','preview-cycle-2','returned-profile-2','preview-cycle-3',
                'returned-profile-3','preview-cycle-4']
        value=dict(acceptedInputCharacters=256,explicitPreparations=4,noImplicitPreparationDuringInputSearch=True,
                   noAutomaticPreparationOnReturn=True,noUnexplainedTrackedRetention=True,retirementAfterEveryCycle=True,
                   sameOwnerDraftAndSource=True,nativeExtremeInputCallNanos=1,nativeOrdinaryInputCallNanos=2,
                   nativeQueryInputCallNanos=3,nativeSearchActionCallNanos=4,searchToFilteredChoiceObservedNanos=10,
                   nativeBudgetBytes=1000,outputBudgetBytes=1000,resourceBudget=10,prepareToFirstDrawObservedNanos=[1,2,3,4],
                   cycles=[dict(cycle=i,providerEntries=i,released=True,outstandingUses=0,releasedNativeBytes=0,
                                releasedSceneBytes=0,releasedNativeResources=0,releasedSceneResources=0,
                                nativePeakBytes=128,scenePeakBytes=64,nativePeakResources=3,scenePeakResources=2,
                                drawWidth=220,drawHeight=160) for i in range(1,5)],
                   uiCallbacks=[dict(phase=p,samples=8,callbackWallMinNanos=1,callbackWallMedianNanos=2,
                                     callbackWallP95Nanos=3,callbackWallMaxNanos=4,
                                     providerEntriesInCallback=0,resourceManagerCallsInCallback=0,packMetadataReadsInCallback=0,resourceContentLookupsInCallback=0) for p in phases])
        native_probe.require_production_cost({'productionViewport':{'cost':value}})
        for location,key,bad in [('cycles','releasedNativeBytes',1),('cycles','releasedSceneResources',1),
                                 ('cycles','providerEntries',8),('cycles','nativePeakBytes',1001),
                                 ('uiCallbacks','providerEntriesInCallback',1),('uiCallbacks','resourceContentLookupsInCallback',1),
                                 ('uiCallbacks','samples',0),('uiCallbacks','callbackWallMedianNanos',10)]:
            altered=copy.deepcopy(value);altered[location][0][key]=bad
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'Production costs'):
                native_probe.require_production_cost({'productionViewport':{'cost':altered}})
        for key,bad in [('acceptedInputCharacters',255),('cycles',[]),('prepareToFirstDrawObservedNanos',[]),
                        ('nativeExtremeInputCallNanos','guessed'),('noAutomaticPreparationOnReturn',False)]:
            with self.subTest(key=key),self.assertRaisesRegex(RuntimeError,'Production costs'):
                native_probe.require_production_cost({'productionViewport':{'cost':{**value,key:bad}}})

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
        report = {'runtimeArtifacts': self.runtime_artifact_facts(), 'passed': True, 'artifactSha256': native_probe.digest(product),
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
