"""Mutate the actual four-target build products to exercise packaging refusals."""

import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_distribution import distribution, verify


class DistributionTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(__file__).resolve().parents[2]
        self.staged = self.root / 'build/distributions'
        self.source = next(p for p in self.staged.glob('*1.21.1-fabric-*.jar')
                           if not p.name.endswith('-sources.jar'))
        with zipfile.ZipFile(self.source) as archive:
            self.entries = {n: archive.read(n) for n in archive.namelist()}
        self.version = json.loads(self.entries['fabric.mod.json'])['version']
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)

    def write(self, entries):
        path = Path(self.temp.name, 'candidate.jar')
        with zipfile.ZipFile(path, 'w') as archive:
            for name, value in entries.items():
                archive.writestr(name, value)
        return path

    def refused(self, entries, reason):
        with self.assertRaisesRegex(ValueError, reason):
            verify(self.write(entries), '1.21.1', 'fabric', self.version)

    def test_actual_build_inventory(self):
        self.assertEqual(8, len(distribution(self.staged, self.version)))

    def test_foreign_classes(self):
        entries = dict(self.entries)
        entries['noobanidus/mods/lootr/Unexpected.class'] = self.entries[
            'design/inherent/lootr_styled/ResourceId.class']
        self.refused(entries, 'foreign or probe class')

    def test_pending_artwork_and_rig(self):
        for name in ('assets/lootr/textures/preview.png',
                     'assets/lootr_styled/preview.jem', 'upstream.jar'):
            entries = dict(self.entries)
            entries[name] = b'not-admitted'
            self.refused(entries, 'unadmitted artwork/rig/archive')

    def test_vanilla_model_override(self):
        entries = dict(self.entries)
        entries['assets/minecraft/models/block/barrel.json'] = b'{}'
        self.refused(entries, 'vanilla resource override')

    def test_wrong_java_baseline(self):
        entries = dict(self.entries)
        key = 'design/inherent/lootr_styled/ResourceId.class'
        entries[key] = entries[key][:6] + (69).to_bytes(2, 'big') + entries[key][8:]
        self.refused(entries, 'unsupported Java baseline')

    def test_wrong_side_or_dependency(self):
        for update in ({'environment': '*'}, {'depends': {'minecraft': '26.3'}}):
            entries = dict(self.entries)
            metadata = json.loads(entries['fabric.mod.json'])
            metadata.update(update)
            entries['fabric.mod.json'] = json.dumps(metadata).encode()
            self.refused(entries, 'identity or side|dependency scope')

    def test_duplicate_members(self):
        path = self.write(self.entries)
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(path, 'a') as archive:
                archive.writestr('fabric.mod.json', self.entries['fabric.mod.json'])
        with self.assertRaisesRegex(ValueError, 'duplicate or corrupt'):
            verify(path, '1.21.1', 'fabric', self.version)

    def test_unadmitted_distribution_artifact(self):
        target = Path(self.temp.name)
        for path in self.staged.glob('*.jar'):
            shutil.copyfile(path, target / path.name)
        shutil.copyfile(self.source, target / 'unadmitted.jar')
        with self.assertRaisesRegex(ValueError, 'missing or unadmitted'):
            distribution(target, self.version)


if __name__ == '__main__':
    unittest.main()
