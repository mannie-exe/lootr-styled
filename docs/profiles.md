# Profiles

The format 1 reader, local/resource-pack catalogue and presentation compiler run at native client resource reload. Plain barrel models and CEM chest rigs have separate admitted targets; accepting a metadata file extension does not establish renderer support.

Local profiles live under `config/lootr_styled/<profile>/`, with an editable `default/` starter. Seeding must preserve existing edits. Explicit restore/export is separate from reload. Resource-pack descriptors use namespaced identities resolved through the native effective resource stack; built-in and local identities cannot be impersonated by a pack.

A profile may contribute artwork, supported model declarations or both. The Default profile contributes complete Lootr-specific barrel geometry and bundled artwork, selected independently. Closed and open barrels have separate geometry in both looted states. Matching Default artwork uses packed maps; other effective artwork uses native side/top/bottom UVs on the same shape. An explicit image override disables the packed mapping for its material state, even when its bytes match Default. A missing packed surface retains the shape through native UVs. Vanilla (Inherited) keeps installed artwork. Supported vanilla-derived models/rigs are lower-priority sources for Lootr; explicit Lootr-specific resources and selected profile contributions take precedence. A profile without a barrel override can inherit the effective vanilla barrel shape. Artwork-only selection must not enable model changes or animation adaptation implicitly. Partial profiles retain ordinary lower-resource fallback. A missing selected identity remains saved while the effective native/Vanilla fallback and reason are reported.

Client textures and models belong to resource packs under `assets`. Server datapack style associations and synchronization are deferred. An eventual server association would still need matching client resources and a bounded, separately owned protocol.

Validate paths and symlink containment for local files, resource IDs and effective sources for packs, metadata versions, references, cycles, counts, aggregate bytes, texture dimensions and geometry bounds before publication. No remote resources or executable profile metadata are supported. Refresh occurs on initialization, explicit request and relevant reload, never per frame or per container.

Artwork permissions are independent of schema validity. Permission-pending local previews must not enter base/public/source artifacts. Bundled Default artwork has exact source and file identities in `lootr-styled-artwork.json` and attribution in [NOTICE](../NOTICE). Native installed resources remain the fallback; emission is deferred.

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

`format` is required and must be integer 1. The optional `title` appears in selectors; it defaults to the source identity. The profile ID remains available in tooltips and saved settings. `models` and `artwork` default to empty maps. Unknown or duplicate properties, duplicate targets, coercion, trailing JSON values, invalid UTF-8, escaping references and unsupported file categories are refused. Metadata contains references, not embedded model documents or image bytes.

The metadata input policy permits 256 KiB, at most 128 mappings across both maps and a readable title of at most 160 Unicode characters. Resource IDs use the core's 256-character bound. Model references end in `.json`, `.jem` or `.jpm`; artwork references end in `.png` or `.png.mcmeta`. These are intake categories, not proof that a target renderer supports every such document. Native target, dependency, geometry, UV, reference and texture checks are still required before publication.

The parser's stream API consumes at most the metadata limit plus one byte and leaves the caller's stream open. Parsed values capture immutable, stably ordered maps. Local files still need rooted containment and file/reference budgets. Resource packs retain native overlay and priority semantics; version-specific target paths can be supplied through native resource-pack overlays rather than guessing one target map for both Minecraft versions.

## Selection and input ownership

`config/lootr_styled/settings.json` owns `enabled`, `models`, `artwork` and `freshObjects`. It uses format 1. Defaults select Default (`lootr_styled:default`) models and artwork, with Auto adaptation. Existing saved selections remain unchanged. Vanilla (Inherited) follows the effective installed resources. A requested missing identity remains saved; its fallback is reported in Details. Apply changes through a resource reload.

Pack descriptors live at `assets/<namespace>/lootr_styled/profiles/<path>.json` and resolve to `<namespace>:<path>` through the effective native resource stack. `lootr_styled` and `lootr_styled_local` are reserved namespaces for profile identities. Local `<slug>/profile.json` resolves to `lootr_styled_local:<slug>`, with resource dependencies beneath `<slug>/assets/<namespace>/<path>`. Local missing files may reference installed resources. The seeded `default/` is an empty editable inherited profile; it is distinct from the built-in Default profile. Seeding never replaces an existing file, including an invalid draft.

Explicit selected custom mappings win their declared slots. For automatic inheritance and built-in Default, an effective Lootr-specific resource supplied outside the native Lootr pack wins. Native source identity comes from loader pack metadata, not filenames or byte equality. A geometry-only profile does not select artwork. Invalid model/artwork selections fall back independently; aggregate input exhaustion refuses the complete generated layer.

Plain barrel JSON supports bounded parent chains and cuboids. Parents resolve before child texture-variable validation. Custom executable model loaders are not admitted. Local model texture dependencies are copied under private generated resource IDs, preserving UV values without publishing vanilla texture overrides. PNG inputs must decode within their dimension, byte and aggregate pixel bounds; animation metadata is checked against its image.

Settings save by atomic replacement. Local assets use anchored directory reads where the filesystem exposes them; otherwise each path component, containment and file identity is checked before and after capture. Referenced local bytes are rehashed before publication. These checks reject observed links, traversal and edits; portable path checks are not a sandbox against a hostile process concurrently replacing the filesystem. The game-owned config directory is a trusted root. No filesystem mechanism grants permission to redistribute source artwork.

The re:form settings screen retains a draft. Save persists it without reloading resources. Save and reload resources saves the draft and waits for its new resource generation. Defaults loads appearance defaults into the draft without saving. Reload resources rereads saved settings and current profiles without saving edits. Inspect resources reports selected profiles, fallbacks, source decisions and the last reload result. The profile catalogue remains available while styling is disabled.

Saving checks the captured file revision before replacement. An external edit enters conflict review; a stale or invalid draft does not overwrite the file. Invalid on-disk settings remain untouched. Resolving a conflict does not save automatically. A confirmed save remains saved if a later resource reload fails. Retry reload checks that same saved revision and does not write settings again. If the source changes or application is uncertain, inspect the current file and resource details before retrying.

Export barrel model creates `exported-default/` once with all four complete Default state models using native texture slots and no artwork. Rename or remove an older geometry copy before exporting it again. The `default/` starter remains a separate, empty inherited profile.

Export artwork copies the twelve active Lootr chest and barrel PNGs, valid image metadata, and any extra barrel textures. Extra barrel textures retain their flattened model bindings; select the copy under Models as well as Artwork when editing those. Chest animation rigs are not exported. The copy records source pack names and original byte hashes in `SOURCES.json`; source artwork licenses still apply. Repeated exports create `artwork-copy/`, `artwork-copy-2/`, and so on, preserving existing edits. Capture and validation finish before creating the copy, and metadata is published last. An interrupted copy may leave files without `profile.json`; these are not admitted as a profile. Input and pixel budgets are the same as profile compilation.

After exporting, use Open profiles folder to open `config/lootr_styled/`. Edit the copy's PNG files and optional `title` in `profile.json`. Reload resources, then choose the copy and save and reload to use it. Export reads the active resource stack, so reload a profile before exporting its appearance. An exported copy is local and is not added to a public artifact automatically.

## Chest rigs

Supported explicit Lootr CEM model keys are:

| Target | Ordinary                                  | Trapped                                           |
| ------ | ----------------------------------------- | ------------------------------------------------- |
| 1.21.1 | `minecraft:emf/cem/lootr_lootr_chest.jem` | `minecraft:emf/cem/lootr_lootr_trapped_chest.jem` |
| 26.3   | `minecraft:emf/cem/lootr_chest.jem`       | `minecraft:emf/cem/lootr_trapped_chest.jem`       |

These name Lootr-specific EMF identities, not vanilla chest overrides. A selected explicit profile wins its declared slot; effective Lootr-specific resources supplied by other packs are kept above automatic vanilla adaptation. Otherwise AUTO/ON read the effective vanilla `chest.jem` or `trapped_chest.jem` under `emf/cem/`, falling back to `optifine/cem/`. Source geometry and UV values remain unchanged. The native 64×64 material contract must qualify; arbitrary texture rebinding, renderer code or unknown attachment layouts are not admitted.

Native attachment names map `base` to `bottom` and `knob` to `lock`; matching animation references are remapped without renaming arbitrary IDs or text. Relative JPM dependencies are captured and rewritten to private, target-specific resource names. Missing files, cycles, oversized expanded graphs and malformed/unsupported geometry cause an actionable native fallback. The reader permits 64 distinct references, depth 32, 2,048 cuboids and expanded graph cost 4,096; repeated graph edges consume expanded cost even when bytes are memoized. ON without usable input reports why it fell back. OFF keeps native parts while preserving native lid movement; explicit Lootr overrides remain independently owned.

Input policy permits 256 resource lookups and 16 MiB across profile scopes, at most 2 MiB per resource, 32 model-parent levels, 1,024 cuboids per model and 128 texture variables. PNG dimensions are at most 4,096 per axis, with 16 million decoded pixels across a compilation. Generated output is independently limited to 256 resources and 16 MiB. These are product intake bounds rather than native format maxima.
