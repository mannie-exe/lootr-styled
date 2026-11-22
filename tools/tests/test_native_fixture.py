"""Fixture preparation must not rewrite a running client's input files."""

import os
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
        with self.assertRaisesRegex(ValueError, 'before preparing'):
            native_probe.prepare('neoforge-1_21_1', 'native')
        self.assertEqual('Preserve running client input', self.settings.read_text())
        self.assertEqual(str(os.getpid()), marker.read_text())

    @unittest.skipUnless(os.name == 'posix', 'POSIX fixture link refusal')
    def test_a_linked_profile_directory_is_refused_before_copying_or_rewriting(self):
        profile = self.root / 'source-profile'
        profile.mkdir()
        (profile / 'outside').symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'file/byte policy'):
            native_probe.prepare('neoforge-1_21_1', 'native', local_profile=profile)
        self.assertEqual('Preserve running client input', self.settings.read_text())
        self.assertFalse((self.root / 'verification/neoforge-1_21_1/runs/native-profile').exists())
