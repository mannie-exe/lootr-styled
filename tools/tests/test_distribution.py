"""Mutate the actual four-target build products to exercise packaging refusals."""

import json
import hashlib
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import Mock
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_distribution import ARTWORK_MANIFEST, archive_inventory, distribution, verify


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
                # Preserve the intended raw ZIP name even on Windows.
                member = zipfile.ZipInfo()
                member.filename = member.orig_filename = name
                archive.writestr(member, value)
        return path

    def refused(self, entries, reason):
        with self.assertRaisesRegex(ValueError, reason):
            verify(self.write(entries), '1.21.1', 'fabric', self.version)

    def test_raw_archive_name_cannot_hide_behind_reader_normalization(self):
        for raw, normalized in (('folder\\outside.txt', 'folder/outside.txt'),
                                ('safe.txt\x00outside', 'safe.txt')):
            with self.subTest(raw=raw):
                member = zipfile.ZipInfo(raw)
                member.filename = normalized
                archive = Mock()
                archive.infolist.return_value = [zipfile.ZipInfo('LICENSE'),
                                                zipfile.ZipInfo('NOTICE'), member]
                archive.namelist.return_value = ['LICENSE', 'NOTICE', normalized]
                archive.testzip.return_value = None
                with self.assertRaisesRegex(ValueError, 'unsafe archive path'):
                    archive_inventory(archive, 'normalized reader')

    def test_actual_build_inventory(self):
        self.assertEqual(8, len(distribution(self.staged, self.version)))

    def test_foreign_classes(self):
        entries = dict(self.entries)
        entries['noobanidus/mods/lootr/Unexpected.class'] = self.entries[
            'design/inherent/lootr_styled/ResourceId.class']
        self.refused(entries, 'foreign or probe class')

    def test_installable_refuses_unsafe_archive_paths(self):
        for name in ('../outside.txt', '/absolute.txt', 'folder\\outside.txt', 'C:/outside.txt'):
            with self.subTest(name=name):
                entries = dict(self.entries)
                entries[name] = b'not a product resource'
                self.refused(entries, 'unsafe archive path')

    def test_reform_is_required_without_bundling_library_classes(self):
        self.assertFalse(any(n.startswith('design/inherent/reform/') for n in self.entries))
        for version in (None, '0.1.0', '*'):
            entries = dict(self.entries)
            metadata = json.loads(entries['fabric.mod.json'])
            if version is None:
                del metadata['depends']['reform']
            else:
                metadata['depends']['reform'] = version
            entries['fabric.mod.json'] = json.dumps(metadata).encode()
            self.refused(entries, 'wrong re:form dependency')

    def test_superseded_settings_ui_cannot_return_in_a_stale_build(self):
        entries = dict(self.entries)
        entries['design/inherent/lootr_styled/client/StyleScreen.class'] = entries['design/inherent/lootr_styled/ResourceId.class']
        self.refused(entries, 'retired settings/UI type')

    def test_loader_minimum_tracks_the_qualified_runtime(self):
        entries = dict(self.entries)
        metadata = json.loads(entries['fabric.mod.json'])
        metadata['depends']['fabricloader'] = '>=0.19.5'
        entries['fabric.mod.json'] = json.dumps(metadata).encode()
        self.refused(entries, 'wrong Fabric Loader minimum')

        root = Path(__file__).resolve().parents[2] / 'build/distributions'
        jar = next(p for p in root.glob('lootr-styled-26.3-neoforge-*.jar') if not p.name.endswith('-sources.jar'))
        with zipfile.ZipFile(jar) as archive:
            modern = {name: archive.read(name) for name in archive.namelist()}
        name = 'META-INF/neoforge.mods.toml'
        import tomllib
        version = tomllib.loads(modern[name].decode())['mods'][0]['version']
        modern[name] = modern[name].replace(b'versionRange="[26.3.0.115,)"', b'versionRange="[26.3.0.22-beta,)"')
        with self.assertRaisesRegex(ValueError, 'wrong NeoForge minimum'):
            verify(self.write(modern), '26.3', 'neoforge', version)

    def test_pending_artwork_and_rig(self):
        for name in ('assets/lootr/textures/preview.png',
                     'assets/lootr_styled/preview.jem', 'upstream.jar'):
            entries = dict(self.entries)
            entries[name] = b'not-admitted'
            self.refused(entries, 'unadmitted artwork/rig/archive')

    def test_default_artwork_cannot_be_missing_or_changed(self):
        image = 'assets/lootr_styled/textures/default/chest.png'
        entries = dict(self.entries)
        del entries[image]
        self.refused(entries, 'missing or duplicate default artwork')
        entries = dict(self.entries)
        entries[image] = b'changed'
        self.refused(entries, 'changed default artwork')

    def test_candidate_cannot_redeclare_its_own_artwork_inventory(self):
        entries = dict(self.entries)
        image = 'assets/lootr_styled/textures/default/chest.png'
        entries[image] = b'changed'
        manifest = json.loads(entries[ARTWORK_MANIFEST])
        manifest['files'][image] = hashlib.sha256(entries[image]).hexdigest()
        entries[ARTWORK_MANIFEST] = json.dumps(manifest).encode()
        self.refused(entries, 'missing or changed default artwork manifest')

    def test_default_model_cannot_be_changed_or_reintroduce_retired_carrier(self):
        entries = dict(self.entries)
        entries['assets/lootr_styled/models/block/barrel/default/unlooted_open.json'] = b'{}'
        self.refused(entries, 'missing or changed Default model')
        entries = dict(self.entries)
        entries['assets/lootr_styled/models/block/barrel_clasps.json'] = b'{}'
        self.refused(entries, 'retired barrel carrier')

    def test_verification_consumer_cannot_ship(self):
        entries = dict(self.entries)
        entries['design/inherent/lootr_styled/verification/NativeProbe.class'] = self.entries[
            'design/inherent/lootr_styled/ResourceId.class']
        self.refused(entries, 'verification-only code')

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

    def test_legacy_renderer_api_is_declared(self):
        entries = dict(self.entries)
        metadata = json.loads(entries['fabric.mod.json'])
        del metadata['depends']['fabric-renderer-api-v1']
        entries['fabric.mod.json'] = json.dumps(metadata).encode()
        self.refused(entries, 'missing required renderer API')

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

    def source_fixture(self):
        target = Path(self.temp.name, 'distribution')
        target.mkdir()
        for path in self.staged.glob('*.jar'):
            shutil.copyfile(path, target / path.name)
        sources = target / self.source.name.replace('.jar', '-sources.jar')
        with zipfile.ZipFile(sources) as archive:
            entries = {name: archive.read(name) for name in archive.namelist()}
        return target, sources, entries

    def test_sources_refuse_unsafe_archive_paths(self):
        target, sources, original = self.source_fixture()
        for name in ('../outside.txt', '/absolute.txt', 'folder\\outside.txt', 'C:/outside.txt'):
            with self.subTest(name=name):
                entries = dict(original)
                entries[name] = b'not a source resource'
                shutil.copyfile(self.write(entries), sources)
                with self.assertRaisesRegex(ValueError, 'unsafe archive path'):
                    distribution(target, self.version)

    def test_sources_require_both_license_notices(self):
        target, sources, original = self.source_fixture()
        for name in ('LICENSE', 'NOTICE'):
            with self.subTest(name=name):
                entries = dict(original)
                del entries[name]
                shutil.copyfile(self.write(entries), sources)
                with self.assertRaisesRegex(ValueError, 'missing or duplicate'):
                    distribution(target, self.version)

    def test_sources_refuse_foreign_code_and_compiled_classes(self):
        target, sources, original = self.source_fixture()
        for name in ('foreign/Donor.java', 'design/inherent/lootr_styled/Unexpected.class'):
            with self.subTest(name=name):
                entries = dict(original)
                entries[name] = b'not admitted source code'
                shutil.copyfile(self.write(entries), sources)
                with self.assertRaisesRegex(ValueError, 'foreign or compiled source member'):
                    distribution(target, self.version)

    def test_sources_refuse_duplicate_members(self):
        target, sources, entries = self.source_fixture()
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(sources, 'a') as archive:
                archive.writestr('NOTICE', entries['NOTICE'])
        with self.assertRaisesRegex(ValueError, 'duplicate or corrupt'):
            distribution(target, self.version)


if __name__ == '__main__':
    unittest.main()
