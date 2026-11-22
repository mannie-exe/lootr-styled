"""Pack fixtures select the exact external library without changing their source snapshot."""

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_pack_probe


class PackFixtureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        (self.source / '.lootr-styled-fixture').write_text('Owned fixture\n')
        (self.source / 'mmc-pack.json').write_text(json.dumps({
            'formatVersion': 1,
            'components': [
                {'uid': 'net.minecraft', 'version': '1.21.1'},
                {'uid': 'net.neoforged', 'version': '21.1.265'},
            ],
        }))
        self.game = self.source / '.minecraft'
        (self.game / 'mods').mkdir(parents=True)
        (self.game / 'options.txt').write_text('resourcePacks:[]\n')
        (self.game / 'config').mkdir()
        (self.game / 'config/iris.properties').write_text('enableShaders=false\n')
        product = self.game / 'mods/lootr-styled-1.21.1-neoforge-fixture.jar'
        self.jar(product, 'lootr_styled', 'fixture')
        built = self.root / 'build/distributions' / product.name
        built.parent.mkdir(parents=True)
        built.write_bytes(product.read_bytes())
        probe = self.root / 'verification/neoforge-1_21_1/build/libs/neoforge-1_21_1-1.0.0.jar'
        probe.parent.mkdir(parents=True)
        probe.write_bytes(b'Independent fixture observer')
        self.old = self.game / 'mods/reform-old.jar'
        self.jar(self.old, 'reform', 'old')
        self.library = self.root / 'reform-qualified.jar'
        self.jar(self.library, 'reform', 'qualified')
        self.sha = hashlib.sha256(self.library.read_bytes()).hexdigest()
        self.java = self.root / 'java'
        self.java.touch()
        for name, replacement in [('ROOT', self.root), ('reform_dependency', lambda *_: {'jar': self.library, 'sha256': self.sha})]:
            mocked = patch.object(prepare_pack_probe, name, replacement)
            mocked.start()
            self.addCleanup(mocked.stop)

    @staticmethod
    def jar(path, mod_id, version):
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('META-INF/neoforge.mods.toml', f'[[mods]]\nmodId="{mod_id}"\nversion="{version}"\n')

    def test_selected_library_and_receipt_change_only_in_the_new_owned_copy(self):
        destination = self.root / 'copy'
        result = prepare_pack_probe.prepare(self.source, destination, self.java, False)
        game = destination / '.minecraft'
        self.assertFalse((game / 'mods/reform-old.jar').exists())
        self.assertEqual(self.library.read_bytes(), (game / 'mods' / self.library.name).read_bytes())
        self.assertEqual(self.sha, (game / '.lootr-styled-expected-reform-sha256').read_text().strip())
        self.assertEqual(self.sha, result['reformSha256'])
        self.assertTrue(self.old.is_file())
        self.assertFalse((self.game / 'mods' / self.library.name).exists())

    def test_duplicate_library_owners_refuse_before_the_destination_is_created(self):
        self.jar(self.game / 'mods/reform-second.jar', 'reform', 'second')
        destination = self.root / 'refused'
        with self.assertRaisesRegex(ValueError, 'at most one external re:form'):
            prepare_pack_probe.prepare(self.source, destination, self.java, False)
        self.assertFalse(destination.exists())

    def test_a_projection_without_the_new_library_accepts_only_the_explicit_pinned_input(self):
        self.old.unlink()
        destination = self.root / 'first-library-copy'
        prepare_pack_probe.prepare(self.source, destination, self.java, False)
        self.assertEqual(self.library.read_bytes(), (destination / '.minecraft/mods' / self.library.name).read_bytes())

    def test_missing_lwjgl_component_is_declared_before_the_first_launch(self):
        source = self.source / 'mmc-pack.json'
        before = source.read_bytes()
        destination = self.root / 'complete-profile'
        prepare_pack_probe.prepare(self.source, destination, self.java, False)
        profile = json.loads((destination / 'mmc-pack.json').read_text())
        self.assertEqual(['3.3.3'], [c['version'] for c in profile['components'] if c['uid'] == 'org.lwjgl3'])
        self.assertEqual(before, source.read_bytes())

    def test_a_different_game_refuses_before_creating_the_instance(self):
        profile = self.source / 'mmc-pack.json'
        value = json.loads(profile.read_text())
        value['components'][0]['version'] = '26.3'
        profile.write_text(json.dumps(value))
        destination = self.root / 'wrong-game'
        with self.assertRaisesRegex(ValueError, 'Minecraft 1.21.1'):
            prepare_pack_probe.prepare(self.source, destination, self.java, False)
        self.assertFalse(destination.exists())
