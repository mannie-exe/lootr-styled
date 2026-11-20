# Profiles

The format 1 reader, local/resource-pack catalogue and presentation compiler run at native client resource reload. Plain barrel models and CEM chest rigs have separate admitted targets; accepting a metadata file extension does not establish renderer support.

Local profiles live under `config/lootr_styled/<profile>/`, with an editable `default/` starter. Seeding must preserve existing edits. Explicit restore/export is separate from reload. Resource-pack descriptors use namespaced identities resolved through the native effective resource stack; built-in and local identities cannot be impersonated by a pack.

A profile may contribute artwork, supported model declarations or both. The Default profile contributes only our Lootr-specific barrel geometry, referencing installed native Lootr textures. Supported vanilla-derived models/rigs are lower-priority sources for Lootr; explicit Lootr-specific resources and selected profile contributions take precedence. A profile without a barrel override can inherit the effective vanilla barrel shape. Artwork-only selection must not enable model changes or animation adaptation implicitly. Partial profiles retain ordinary lower-resource fallback. A missing selected identity remains saved while the effective native/Vanilla fallback and reason are reported.

Client textures and models belong to resource packs under `assets`. Server datapack style associations and synchronization are deferred. An eventual server association would still need matching client resources and a bounded, separately owned protocol.

Validate paths and symlink containment for local files, resource IDs and effective sources for packs, metadata versions, references, cycles, counts, aggregate bytes, texture dimensions and geometry bounds before publication. No remote resources or executable profile metadata are supported. Refresh occurs on initialization, explicit request and relevant reload, never per frame or per container.

Artwork permissions are independent of schema validity. Permission-pending local previews must not enter base/public/source artifacts. Native installed resources provide a usable fallback without a bundled styled texture set.

## Metadata format 1

The source adapter supplies identity; metadata cannot rename itself. Namespaced IDs use `namespace:path`. Mapping keys and values name complete asset resources, including category and extension, rather than ambiguous model aliases. For example, this 1.21.1 profile declares a model override without an artwork override:

```json
{
  "format": 1,
  "title": "Woodland",
  "models": {
    "lootr:models/block/lootr_barrel_unopened.json": "example:models/barrel.json"
  }
}
```

`format` is required and must be integer 1. `title` defaults to the source identity. `models` and `artwork` default to empty maps. Unknown or duplicate properties, duplicate targets, coercion, trailing JSON values, invalid UTF-8, escaping references and unsupported file categories are refused. Metadata contains references, not embedded model documents or image bytes.

The metadata input policy permits 256 KiB, at most 128 mappings across both maps and a readable title of at most 160 Unicode characters. Resource IDs use the core's 256-character bound. Model references end in `.json`, `.jem` or `.jpm`; artwork references end in `.png` or `.png.mcmeta`. These are intake categories, not proof that a target renderer supports every such document. Native target, dependency, geometry, UV, reference and texture checks are still required before publication.

The parser's stream API consumes at most the metadata limit plus one byte and leaves the caller's stream open. Parsed values capture immutable, stably ordered maps. Local files still need rooted containment and file/reference budgets. Resource packs retain native overlay and priority semantics; version-specific target paths can be supplied through native resource-pack overlays rather than guessing one target map for both Minecraft versions.

## Selection and input ownership

`config/lootr_styled/settings.json` owns `enabled`, `models`, `artwork` and `freshObjects`. It uses format 1. Defaults select `lootr_styled:default` models, `lootr_styled:vanilla` artwork and AUTO adaptation. The Vanilla ID means inherited geometry, rather than a forced replacement of the effective vanilla resource. A requested missing identity remains saved; its effective fallback is reported separately. Apply changes through a resource reload.

Pack descriptors live at `assets/<namespace>/lootr_styled/profiles/<path>.json` and resolve to `<namespace>:<path>` through the effective native resource stack. `lootr_styled` and `lootr_styled_local` are reserved namespaces for profile identities. Local `<slug>/profile.json` resolves to `lootr_styled_local:<slug>`, with resource dependencies beneath `<slug>/assets/<namespace>/<path>`. Local missing files may reference installed resources. The seeded `default/` is an empty editable inherited profile; it is distinct from the built-in Default barrel contribution. Seeding never replaces an existing file, including an invalid draft.

Explicit selected custom mappings win their declared slots. For automatic inheritance and built-in Default, an effective Lootr-specific resource supplied outside the native Lootr pack wins. Native source identity comes from loader pack metadata, not filenames or byte equality. A geometry-only profile does not select artwork. Invalid model/artwork selections fall back independently; aggregate input exhaustion refuses the complete generated layer.

Plain barrel JSON supports bounded parent chains and cuboids. Parents resolve before child texture-variable validation. Custom executable model loaders are not admitted. Local model texture dependencies are copied under private generated resource IDs, preserving UV values without publishing vanilla texture overrides. PNG inputs must decode within their dimension, byte and aggregate pixel bounds; animation metadata is checked against its image.

Settings save by atomic replacement. Local assets use anchored directory reads where the filesystem exposes them; otherwise each path component, containment and file identity is checked before and after capture. Referenced local bytes are rehashed before publication. These checks reject observed links, traversal and edits; portable path checks are not a sandbox against a hostile process concurrently replacing the filesystem. The game-owned config directory is a trusted root. No filesystem mechanism grants permission to redistribute source artwork.

The settings screen edits a draft. Apply checks the settings file's captured digest before atomic replacement, then requests native resource reload. An observed external edit refuses the save; reopen to load it. Invalid on-disk drafts remain untouched. Defaults changes only the editor draft; Apply still makes the change concrete. Refresh reloads current files/packs without saving the draft. The reason view reports requested/effective identities and source decisions separately.

Export Default creates `exported-default/` once with four separately material-bound barrel models, a profile descriptor and no artwork. It never replaces an existing directory; rename/remove that local export explicitly before making another. Metadata is written last, so an interrupted export cannot become a partial admitted profile. Select `lootr_styled_local:exported-default` after Refresh to use the editable copy. The `default/` starter remains a separate, empty inherited profile.

## Chest rigs

Supported explicit Lootr CEM model keys are:

| Target | Ordinary                                  | Trapped                                           |
| ------ | ----------------------------------------- | ------------------------------------------------- |
| 1.21.1 | `minecraft:emf/cem/lootr_lootr_chest.jem` | `minecraft:emf/cem/lootr_lootr_trapped_chest.jem` |
| 26.3   | `minecraft:emf/cem/lootr_chest.jem`       | `minecraft:emf/cem/lootr_trapped_chest.jem`       |

These name Lootr-specific EMF identities, not vanilla chest overrides. A selected explicit profile wins its declared slot; effective Lootr-specific resources supplied by other packs are kept above automatic vanilla adaptation. Otherwise AUTO/ON read the effective vanilla `chest.jem` or `trapped_chest.jem` under `emf/cem/`, falling back to `optifine/cem/`. Source geometry and UV values remain unchanged. The native 64×64 material contract must qualify; arbitrary texture rebinding, renderer code or unknown attachment layouts are not admitted.

Native attachment names map `base` to `bottom` and `knob` to `lock`; matching animation references are remapped without renaming arbitrary IDs or text. Relative JPM dependencies are captured and rewritten to private, target-specific resource names. Missing files, cycles, oversized expanded graphs and malformed/unsupported geometry cause an actionable native fallback. The reader permits 64 distinct references, depth 32, 2,048 cuboids and expanded graph cost 4,096; repeated graph edges consume expanded cost even when bytes are memoized. ON without usable input reports why it fell back. OFF keeps native parts while preserving native lid movement; explicit Lootr overrides remain independently owned.

Input policy permits 256 resource lookups and 16 MiB across profile scopes, at most 2 MiB per resource, 32 model-parent levels, 1,024 cuboids per model and 128 texture variables. PNG dimensions are at most 4,096 per axis, with 16 million decoded pixels across a compilation. Generated output is independently limited to 256 resources and 16 MiB. These are product intake bounds rather than native format maxima.
