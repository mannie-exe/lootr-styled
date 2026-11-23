"""Inspect installable artifacts, not development classpaths or upstream metadata labels."""

import argparse
import json
import hashlib
from pathlib import Path
import struct
import tomllib
import zipfile


TARGETS = {'1.21.1': 65, '26.3': 69}
ARTWORK_MANIFEST = 'lootr-styled-artwork.json'
MODEL_MANIFEST = 'lootr-styled-content.json'
REFORM_VERSION = json.loads((Path(__file__).resolve().parents[1] / 'gradle/reform.json').read_text())['version']
VERSIONS = tomllib.loads((Path(__file__).resolve().parents[1] / 'gradle/libs.versions.toml').read_text())['versions']
RETIRED_TYPES = (
    'design/inherent/lootr_styled/client/StyleScreen',
    'design/inherent/lootr_styled/client/ProfilePicker',
    'design/inherent/lootr_styled/client/ExportScreen',
    'design/inherent/lootr_styled/client/ReasonScreen',
    'design/inherent/lootr_styled/client/NativePanel',
    'design/inherent/lootr_styled/profile/ClientConfiguration',
)


def retired_settings_inventory(names, label):
    for name in names:
        if any(name == old + suffix or name.startswith(old + '$')
               for old in RETIRED_TYPES for suffix in ('.class', '.java')):
            raise ValueError(f'{label}: retired settings/UI type {name}')


def artwork_inventory(archive, label):
    manifest = (Path(__file__).resolve().parents[1] / 'resources' / ARTWORK_MANIFEST).read_bytes()
    names = archive.namelist()
    if names.count(ARTWORK_MANIFEST) != 1 or archive.read(ARTWORK_MANIFEST) != manifest:
        raise ValueError(f'{label}: missing or changed default artwork manifest')
    approved = json.loads(manifest)['files']
    if len(approved) != 14:
        raise ValueError('Default artwork inventory must contain twelve native images and two shared material maps')
    for name, digest in approved.items():
        if names.count(name) != 1:
            raise ValueError(f'{label}: missing or duplicate default artwork {name}')
        if hashlib.sha256(archive.read(name)).hexdigest() != digest:
            raise ValueError(f'{label}: changed default artwork {name}')
    for name in names:
        if name.lower().endswith(('.png', '.jem', '.jpm', '.zip', '.jar')) and name not in approved:
            raise ValueError(f'{label}: unadmitted artwork/rig/archive {name}')
    model_manifest = (Path(__file__).resolve().parents[1] / 'resources' / MODEL_MANIFEST).read_bytes()
    if names.count(MODEL_MANIFEST) != 1 or archive.read(MODEL_MANIFEST) != model_manifest:
        raise ValueError(f'{label}: missing or changed Default model manifest')
    models = json.loads(model_manifest)['files']
    if len(models) != 8:
        raise ValueError('Default model inventory must contain four states and two UV variants')
    for name, entry in models.items():
        if names.count(name) != 1 or hashlib.sha256(archive.read(name)).hexdigest() != entry['sha256']:
            raise ValueError(f'{label}: missing or changed Default model {name}')
    for name in names:
        if name == 'assets/lootr_styled/models/block/barrel_clasps.json':
            raise ValueError(f'{label}: retired barrel carrier')
        if (name.startswith('assets/lootr_styled/models/block/barrel/default/')
                and not name.endswith('/') and name not in models):
            raise ValueError(f'{label}: unadmitted Default model {name}')


def verify(path, minecraft, loader, version):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError(f'{path.name}: duplicate or corrupt archive members')
        for required in ('LICENSE', 'NOTICE', 'design/inherent/lootr_styled/ResourceId.class'):
            if names.count(required) != 1:
                raise ValueError(f'{path.name}: missing or duplicate {required}')
        artwork_inventory(archive, path.name)
        retired_settings_inventory(names, path.name)
        for name in names:
            if name.startswith('design/inherent/lootr_styled/verification/'):
                raise ValueError(f'{path.name}: verification-only code {name}')
            if name.startswith('/') or '\\' in name or '..' in Path(name).parts:
                raise ValueError(f'{path.name}: unsafe archive path {name}')
            if name.startswith('assets/minecraft/'):
                raise ValueError(f'{path.name}: unexpected vanilla resource override {name}')
            if name.endswith('.class'):
                if not name.startswith('design/inherent/lootr_styled/') or '/probe/' in name:
                    raise ValueError(f'{path.name}: foreign or probe class {name}')
                data = archive.read(name)
                if len(data) < 8 or data[:4] != b'\xca\xfe\xba\xbe':
                    raise ValueError(f'{path.name}: malformed class {name}')
                if struct.unpack('>H', data[6:8])[0] > TARGETS[minecraft]:
                    raise ValueError(f'{path.name}: unsupported Java baseline {name}')
        if loader == 'fabric':
            value = json.loads(archive.read('fabric.mod.json'))
            if (value['id'], value['version'], value['environment']) != ('lootr_styled', version, 'client'):
                raise ValueError(f'{path.name}: wrong Fabric identity or side')
            if (value['depends']['minecraft'] != minecraft or not value['depends'].get('lootr')
                    or not value['depends'].get('fabric-command-api-v2')):
                raise ValueError(f'{path.name}: wrong Fabric dependency scope')
            if value['depends'].get('reform') != REFORM_VERSION:
                raise ValueError(f'{path.name}: wrong re:form dependency')
            if value['depends'].get('fabricloader') != f'>={VERSIONS["fabric-loader"]}':
                raise ValueError(f'{path.name}: wrong Fabric Loader minimum')
            if minecraft == '1.21.1' and not value['depends'].get('fabric-renderer-api-v1'):
                raise ValueError(f'{path.name}: missing required renderer API')
            if 'META-INF/neoforge.mods.toml' in names:
                raise ValueError(f'{path.name}: mixed loader metadata')
        else:
            value = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
            mod = value['mods']
            if len(mod) != 1 or (mod[0]['modId'], mod[0]['version']) != ('lootr_styled', version):
                raise ValueError(f'{path.name}: wrong NeoForge identity')
            dependencies = {d['modId']: d for d in value['dependencies']['lootr_styled']}
            if set(dependencies) != {'minecraft', 'neoforge', 'lootr', 'reform'}:
                raise ValueError(f'{path.name}: wrong required dependency set')
            if (dependencies['reform']['versionRange'] != f'[{REFORM_VERSION}]'
                    or dependencies['reform']['type'] != 'required'):
                raise ValueError(f'{path.name}: wrong re:form dependency')
            if dependencies['minecraft']['versionRange'] != f'[{minecraft}]':
                raise ValueError(f'{path.name}: wrong Minecraft dependency')
            minimum = VERSIONS['neoforge-modern' if minecraft == '26.3' else 'neoforge-legacy']
            if dependencies['neoforge']['versionRange'] != f'[{minimum},)':
                raise ValueError(f'{path.name}: wrong NeoForge minimum')
            if any(d['side'] != 'CLIENT' for d in dependencies.values()):
                raise ValueError(f'{path.name}: nonclient dependency')
            if 'fabric.mod.json' in names:
                raise ValueError(f'{path.name}: mixed loader metadata')


def distribution(root, version):
    found = []
    for minecraft in TARGETS:
        for loader in ('fabric', 'neoforge'):
            prefix = f'lootr-styled-{minecraft}-{loader}-{version}'
            path = root / f'{prefix}.jar'
            verify(path, minecraft, loader, version)
            sources = root / f'{prefix}-sources.jar'
            with zipfile.ZipFile(sources) as archive:
                retired_settings_inventory(archive.namelist(), sources.name)
                if any(n.startswith('design/inherent/lootr_styled/verification/') for n in archive.namelist()):
                    raise ValueError(f'{sources.name}: verification-only sources')
                if archive.testzip() is not None or 'design/inherent/lootr_styled/ResourceId.java' not in archive.namelist():
                    raise ValueError(f'{sources.name}: missing or corrupt own sources')
                artwork_inventory(archive, sources.name)
            found.extend((path.name, sources.name))
    if sorted(p.name for p in root.glob('*.jar')) != sorted(found):
        raise ValueError('Distribution contains missing or unadmitted JARs')
    return found


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', help='Exact mod_version from gradle.properties')
    args = parser.parse_args()
    files = distribution(Path(__file__).resolve().parents[1] / 'build/distributions', args.version)
    print(f'{len(files)} distribution artifacts verified; native rendering is a separate check')
