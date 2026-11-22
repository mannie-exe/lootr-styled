"""Prepare a new, disposable Prism instance from an owned bundled-pack projection.

This does not launch Prism or touch its accounts, global settings or existing instances.
Obtain desktop access before launching the returned instance through Prism.
"""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tomllib
import zipfile
from reform import dependency as reform_dependency

ROOT = Path(__file__).resolve().parents[1]


def launch_profile(source):
    profile = json.loads((source / 'mmc-pack.json').read_text())
    components = profile.get('components', []) if isinstance(profile, dict) else []
    if not isinstance(components, list) or any(not isinstance(c, dict) for c in components):
        raise ValueError('Expected a NeoForge Minecraft 1.21.1 launch profile')
    games = [c for c in components if c.get('uid') == 'net.minecraft']
    loaders = [c for c in components if c.get('uid') == 'net.neoforged']
    if len(games) != 1 or games[0].get('version') != '1.21.1' or len(loaders) != 1:
        raise ValueError('Expected a NeoForge Minecraft 1.21.1 launch profile')
    if not any(c.get('uid') == 'org.lwjgl3' for c in components):
        # Minecraft 1.21.1's native Prism metadata suggests this dependency.
        # Declare it before first launch; discovery after launch is too late for Sodium.
        components.insert(0, {'uid': 'org.lwjgl3', 'version': '3.3.3', 'dependencyOnly': True})
    return profile


def prepare(source, destination, java, shaders, reform_repository=None, product=None):
    library = reform_dependency('neoforge-1_21_1', reform_repository)
    source = source.resolve(strict=True)
    destination = destination.absolute()
    if not (source / '.lootr-styled-fixture').is_file():
        raise ValueError('The source must be an owned, extracted pack fixture')
    if destination.exists() or destination.is_symlink():
        raise ValueError('Refusing to replace any existing instance')
    if not java.is_file():
        raise ValueError('An explicit Java 21 executable is required')
    profile = launch_profile(source)
    game = source / '.minecraft'
    candidates = list((game / 'mods').glob('lootr-styled-*.jar'))
    if len(candidates) != 1:
        raise ValueError('Expected exactly one packaged product in the projection')
    previous_product = candidates[0]
    product = previous_product if product is None else product.resolve(strict=True)
    if not product.name.startswith('lootr-styled-1.21.1-neoforge-') or product.name.endswith('-sources.jar'):
        raise ValueError('Select the installable NeoForge 1.21.1 product JAR')
    built = ROOT / 'build/distributions' / product.name
    if not built.is_file() or product.read_bytes() != built.read_bytes():
        raise ValueError('Pack projection differs from the current identified product')
    probe = ROOT / 'verification/neoforge-1_21_1/build/libs/neoforge-1_21_1-1.0.0.jar'
    if not probe.is_file():
        raise ValueError('Build the separate packaged verification consumer first')
    library_inputs = []
    for jar in (game / 'mods').glob('*.jar'):
        with zipfile.ZipFile(jar) as archive:
            if 'META-INF/neoforge.mods.toml' not in archive.namelist():
                continue
            metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
            if any(mod.get('modId') == 'reform' for mod in metadata.get('mods', [])):
                library_inputs.append(jar)
    if len(library_inputs) > 1:
        raise ValueError('Expected at most one external re:form library in the pack projection')
    selected_library = library.get('jar') or (library_inputs[0] if library_inputs else None)
    if selected_library is None:
        raise ValueError('Pack projection needs the pinned re:form input; pass --reform-repository PATH')
    if hashlib.sha256(selected_library.read_bytes()).hexdigest() != library['sha256']:
        raise ValueError('Pack projection needs the pinned re:form input; pass --reform-repository PATH')
    destination.mkdir(parents=True)
    (destination / '.lootr-styled-fixture').write_text('lootr-styled-prism-verification-v1\n')
    (destination / 'mmc-pack.json').write_text(json.dumps(profile, indent=2) + '\n')
    shutil.copytree(game, destination / '.minecraft')
    game = destination / '.minecraft'
    (game / 'mods' / previous_product.name).unlink()
    shutil.copyfile(product, game / 'mods' / product.name)
    for previous_library in library_inputs:
        (game / 'mods' / previous_library.name).unlink()
    shutil.copyfile(selected_library, game / 'mods' / selected_library.name)
    shutil.copyfile(probe, game / 'mods/lootr-styled-verification-only.jar')
    options = dict(line.split(':', 1) for line in (game / 'options.txt').read_text().splitlines() if ':' in line)
    options.update({'incompatibleResourcePacks': '[]', 'onboardAccessibility': 'false', 'renderDistance': '4', 'simulationDistance': '5', 'maxFps': '30', 'guiScale': '2', 'fullscreen': 'false', 'pauseOnLostFocus': 'false'})
    (game / 'options.txt').write_text(''.join(f'{key}:{value}\n' for key, value in options.items()))
    style = game / 'config/lootr_styled/settings.json'
    style.parent.mkdir(parents=True, exist_ok=True)
    style.write_text(json.dumps({'format': 1, 'enabled': True, 'models': 'lootr_styled:default', 'artwork': 'lootr_styled:default', 'freshObjects': 'AUTO'}, indent=2) + '\n')
    iris = game / 'config/iris.properties'
    iris.write_text('\n'.join('enableShaders=' + str(shaders).lower() if line.startswith('enableShaders=') else line for line in iris.read_text().splitlines()) + '\n')
    for name, value in {
        '.lootr-styled-pack': 'Owned production-launch pack fixture',
        '.lootr-styled-default-artwork': 'Require effective packaged Default artwork',
        '.lootr-styled-scene': 'Owned disposable container scene',
        '.lootr-styled-shader-expected': str(shaders).lower(),
        '.lootr-styled-expected-sha256': hashlib.sha256(product.read_bytes()).hexdigest(),
        '.lootr-styled-expected-reform-sha256': library['sha256'],
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
    return {'instance': str(destination), 'shaderExpected': shaders, 'artifactSha256': hashlib.sha256(product.read_bytes()).hexdigest(), 'reformSha256': library['sha256'], 'probeSha256': hashlib.sha256(probe.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--shaders', choices=('on', 'off'), required=True)
    parser.add_argument('--reform-repository', type=Path, help='Staged Maven repository for the pinned unpublished re:form candidate')
    parser.add_argument('--product', type=Path, help='Exact current distribution JAR to install only into the new fixture')
    args = parser.parse_args()
    print(json.dumps(prepare(args.source, args.instance, args.java, args.shaders == 'on', args.reform_repository, args.product)))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        print(f'Pack fixture failed: {error}', file=sys.stderr)
        sys.exit(1)
