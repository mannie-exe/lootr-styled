"""Check maintained local Markdown links without credentials or network access."""

import os
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


def check(root):
    failures = []
    count = 0
    excluded = {'.git', '.gradle', '.cache', '.tools', 'build', 'runs', 'run', 'artifacts', 'node_modules'}
    for directory, children, files in os.walk(root):
        children[:] = [name for name in children if name not in excluded]
        for name in files:
            if not name.endswith('.md'):
                continue
            source = Path(directory, name)
            for target in re.findall(r'\]\(([^)]+)\)', source.read_text(encoding='utf-8')):
                target = target.strip('<>')
                if urlsplit(target).scheme or target.startswith('#'):
                    continue
                count += 1
                path = unquote(target.split('#', 1)[0])
                if not (source.parent / path).exists():
                    failures.append(f'{source.relative_to(root)}: missing {target}')
    if failures:
        raise ValueError('\n'.join(failures))
    return count


if __name__ == '__main__':
    print(f'{check(Path(__file__).resolve().parents[1])} local documentation links checked')
