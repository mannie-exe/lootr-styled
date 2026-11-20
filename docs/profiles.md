# Profiles

Config profiles and resource-pack profiles will use one versioned declarative schema and compiler. The exact file contract is not implemented yet; do not treat provisional paths as a released API.

Local profiles live under `config/lootr_styled/<profile>/`, with an editable `default/` starter. Seeding must preserve existing edits. Explicit restore/export is separate from reload. Resource-pack descriptors use namespaced identities resolved through the native effective resource stack; built-in and local identities cannot be impersonated by a pack.

A profile may contribute artwork, supported model declarations or both. The Default profile contributes only our Lootr-specific barrel geometry, referencing installed native Lootr textures. Supported vanilla-derived models/rigs are lower-priority sources for Lootr; explicit Lootr-specific resources and selected profile contributions take precedence. A profile without a barrel override can inherit the effective vanilla barrel shape. Artwork-only selection must not enable model changes or animation adaptation implicitly. Partial profiles retain ordinary lower-resource fallback. A missing selected identity remains saved while the effective native/Vanilla fallback and reason are reported.

Client textures and models belong to resource packs under `assets`. Server datapack style associations and synchronization are deferred. An eventual server association would still need matching client resources and a bounded, separately owned protocol.

Validate paths and symlink containment for local files, resource IDs and effective sources for packs, metadata versions, references, cycles, counts, aggregate bytes, texture dimensions and geometry bounds before publication. No remote resources or executable profile metadata are supported. Refresh occurs on initialization, explicit request and relevant reload, never per frame or per container.

Artwork permissions are independent of schema validity. Permission-pending local previews must not enter base/public/source artifacts. Native installed resources provide a usable fallback without a bundled styled texture set.
