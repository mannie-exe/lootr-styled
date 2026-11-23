"""Fixture preparation must not rewrite a running client's input files."""

import os
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
        with self.assertRaisesRegex(ValueError, 'before preparing'):
            native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertEqual('Preserve running client input', self.settings.read_text())
        self.assertEqual(str(os.getpid()), marker.read_text())
        self.assertEqual('Preserve live report', report.read_text())

    def test_a_stopped_fixture_cannot_reuse_a_previous_success_receipt(self):
        for name in ('lootr-styled-probe-report.json', 'lootr-styled-probe-state.txt'):
            (self.fixture / name).write_text('Previous run')
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse((self.fixture / 'lootr-styled-probe-report.json').exists())
        self.assertFalse((self.fixture / 'lootr-styled-probe-state.txt').exists())

    def test_graphics_scope_is_explicit_and_cleared_by_ordinary_preparation(self):
        marker = self.fixture / '.lootr-styled-graphic-probe'
        native_probe.prepare('neoforge-1_21_1', 'native', graphics_only=True)
        self.assertTrue(marker.is_file())
        native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertFalse(marker.exists())

    def test_conflicting_graphics_and_world_scopes_preserve_existing_evidence(self):
        with self.assertRaisesRegex(ValueError, 'either isolated graphics'):
            native_probe.prepare('neoforge-1_21_1', 'native', scene=True, graphics_only=True)
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
