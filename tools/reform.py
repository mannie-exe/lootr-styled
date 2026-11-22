"""Resolve the exact external settings-library input for an isolated native consumer."""

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def dependency(target, repository=None):
    pins = json.loads((ROOT / 'gradle/reform.json').read_text())
    if target not in pins['artifacts']:
        raise ValueError(f'Unknown re:form target: {target}')
    loader, game = target.split('-', 1)
    module = f'reform-{loader}-{game.replace("_", ".")}'
    version = pins['version']
    result = {'module': module, 'version': version, 'sha256': pins['artifacts'][target]}
    if repository is None:
        if '-dev.' in version:
            raise ValueError(f're:form {version} is an unpublished candidate; pass --reform-repository PATH')
        return result
    repository = Path(repository).resolve()
    jar = repository / 'design/inherent' / module / version / f'{module}-{version}.jar'
    if not jar.is_file() or hashlib.sha256(jar.read_bytes()).hexdigest() != result['sha256']:
        raise ValueError(f'Missing or changed re:form input for {target}: {jar}')
    result['repository'] = repository
    result['jar'] = jar
    return result
