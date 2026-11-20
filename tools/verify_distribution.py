"""Inspect installable artifacts, not development classpaths or upstream metadata labels."""

import json
from pathlib import Path
import struct
import sys
import tomllib
import zipfile


TARGETS = {'1.21.1': 65, '26.3': 69}


def verify(path, minecraft, loader, version):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError(f'{path.name}: duplicate or corrupt archive members')
        for required in ('LICENSE', 'NOTICE', 'design/inherent/lootr_styled/ResourceId.class'):
            if names.count(required) != 1:
                raise ValueError(f'{path.name}: missing or duplicate {required}')
        for name in names:
            if name.startswith('/') or '\\' in name or '..' in Path(name).parts:
                raise ValueError(f'{path.name}: unsafe archive path {name}')
            if name.lower().endswith(('.png', '.jem', '.jpm', '.zip', '.jar')):
                raise ValueError(f'{path.name}: unadmitted artwork/rig/archive {name}')
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
            if value['depends']['minecraft'] != minecraft or not value['depends'].get('lootr'):
                raise ValueError(f'{path.name}: wrong Fabric dependency scope')
            if 'META-INF/neoforge.mods.toml' in names:
                raise ValueError(f'{path.name}: mixed loader metadata')
        else:
            value = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
            mod = value['mods']
            if len(mod) != 1 or (mod[0]['modId'], mod[0]['version']) != ('lootr_styled', version):
                raise ValueError(f'{path.name}: wrong NeoForge identity')
            dependencies = {d['modId']: d for d in value['dependencies']['lootr_styled']}
            if set(dependencies) != {'minecraft', 'neoforge', 'lootr'}:
                raise ValueError(f'{path.name}: wrong required dependency set')
            if dependencies['minecraft']['versionRange'] != f'[{minecraft}]':
                raise ValueError(f'{path.name}: wrong Minecraft dependency')
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
                if archive.testzip() is not None or 'design/inherent/lootr_styled/ResourceId.java' not in archive.namelist():
                    raise ValueError(f'{sources.name}: missing or corrupt own sources')
                if any(n.endswith(('.png', '.jem', '.jpm', '.zip', '.jar')) for n in archive.namelist()):
                    raise ValueError(f'{sources.name}: unadmitted preview or donor content')
            found.extend((path.name, sources.name))
    if sorted(p.name for p in root.glob('*.jar')) != sorted(found):
        raise ValueError('Distribution contains missing or unadmitted JARs')
    return found


if __name__ == '__main__':
    files = distribution(Path(__file__).resolve().parents[1] / 'build/distributions', sys.argv[1])
    print(f'{len(files)} distribution artifacts verified; native rendering is a separate check')
