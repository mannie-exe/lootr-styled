"""Candidate intake refuses absent, altered and ambiguous library inputs before native launch."""

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import reform


class ReformInputTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'gradle').mkdir()
        self.version = '0.2.0-dev.55'
        self.bytes = b'owned candidate test bytes'
        self.pins = {'version': self.version, 'artifacts': {'neoforge-1_21_1': hashlib.sha256(self.bytes).hexdigest()}}
        (self.root / 'gradle/reform.json').write_text(json.dumps(self.pins))
        self.patch = patch.object(reform, 'ROOT', self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def test_unpublished_candidate_requires_explicit_repository(self):
        with self.assertRaisesRegex(ValueError, '--reform-repository'):
            reform.dependency('neoforge-1_21_1')
        with self.assertRaisesRegex(ValueError, 'Unknown re:form target'):
            reform.dependency('invented-target', self.root)

    def test_exact_module_and_bytes_are_required(self):
        repo = self.root / 'repository'
        path = repo / 'design/inherent/reform-neoforge-1.21.1' / self.version
        path.mkdir(parents=True)
        jar = path / f'reform-neoforge-1.21.1-{self.version}.jar'
        with self.assertRaisesRegex(ValueError, 'Missing or changed'):
            reform.dependency('neoforge-1_21_1', repo)
        jar.write_bytes(self.bytes)
        self.assertEqual(jar.resolve(), reform.dependency('neoforge-1_21_1', repo)['jar'])
        jar.write_bytes(self.bytes + b'changed')
        with self.assertRaisesRegex(ValueError, 'Missing or changed'):
            reform.dependency('neoforge-1_21_1', repo)
