"""Prepare a new, disposable Prism instance from an owned bundled-pack projection.

This does not launch Prism or touch its accounts, global settings or existing instances.
Obtain desktop access before launching the returned instance through Prism.
"""

import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def prepare(source, destination, java, shaders):
    source = source.resolve(strict=True)
    destination = destination.absolute()
    if not (source / '.lootr-styled-fixture').is_file():
        raise ValueError('The source must be an owned, extracted pack fixture')
    if destination.exists() or destination.is_symlink():
        raise ValueError('Refusing to replace any existing instance')
    if not java.is_file():
        raise ValueError('An explicit Java 21 executable is required')
    game = source / '.minecraft'
    candidates = list((game / 'mods').glob('lootr-styled-*.jar'))
    if len(candidates) != 1:
        raise ValueError('Expected exactly one packaged product in the projection')
    product = candidates[0]
    built = ROOT / 'build/distributions' / product.name
    if not built.is_file() or product.read_bytes() != built.read_bytes():
        raise ValueError('Pack projection differs from the current identified product')
    probe = ROOT / 'verification/neoforge-1_21_1/build/libs/neoforge-1_21_1-1.0.0.jar'
    if not probe.is_file():
        raise ValueError('Build the separate packaged verification consumer first')
    destination.mkdir(parents=True)
    (destination / '.lootr-styled-fixture').write_text('lootr-styled-prism-verification-v1\n')
    shutil.copyfile(source / 'mmc-pack.json', destination / 'mmc-pack.json')
    shutil.copytree(game, destination / '.minecraft')
    game = destination / '.minecraft'
    shutil.copyfile(probe, game / 'mods/lootr-styled-verification-only.jar')
    options = dict(line.split(':', 1) for line in (game / 'options.txt').read_text().splitlines() if ':' in line)
    options.update({'incompatibleResourcePacks': '[]', 'onboardAccessibility': 'false', 'renderDistance': '4', 'simulationDistance': '5', 'maxFps': '30', 'guiScale': '2', 'fullscreen': 'false', 'pauseOnLostFocus': 'false'})
    (game / 'options.txt').write_text(''.join(f'{key}:{value}\n' for key, value in options.items()))
    iris = game / 'config/iris.properties'
    iris.write_text('\n'.join('enableShaders=' + str(shaders).lower() if line.startswith('enableShaders=') else line for line in iris.read_text().splitlines()) + '\n')
    for name, value in {
        '.lootr-styled-pack': 'Owned production-launch pack fixture',
        '.lootr-styled-scene': 'Owned disposable container scene',
        '.lootr-styled-shader-expected': str(shaders).lower(),
        '.lootr-styled-expected-sha256': hashlib.sha256(product.read_bytes()).hexdigest(),
        'lootr-styled-probe-mode.txt': 'fresh',
    }.items():
        (game / name).write_text(value + '\n')
    (destination / 'instance.cfg').write_text('\n'.join([
        '[General]', 'ConfigVersion=1.2', 'InstanceType=OneSix',
        f'name=Lootr Styled verification shaders {"ON" if shaders else "OFF"}',
        'ManagedPack=false', 'AutoUpdate=false', 'OverrideMemory=true',
        'MaxMemAlloc=6144', 'MinMemAlloc=512', 'OverrideJavaLocation=true',
        f'JavaPath={java}', 'OverrideJavaArgs=true', 'JvmArgs=-XX:+UseZGC -XX:+ZGenerational',
        'OverrideCommands=true', 'PreLaunchCommand=', 'WrapperCommand=', '',
    ]))
    return {'instance': str(destination), 'shaderExpected': shaders, 'artifactSha256': hashlib.sha256(product.read_bytes()).hexdigest(), 'probeSha256': hashlib.sha256(probe.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--shaders', choices=('on', 'off'), required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.source, args.instance, args.java, args.shaders == 'on')))


if __name__ == '__main__':
    main()
