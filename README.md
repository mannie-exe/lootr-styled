# Lootr: Styled

Client-side chest and barrel styling for Minecraft and Lootr, developed under inherent.design. The project targets Minecraft 1.21.1 and 26.3 on Fabric and NeoForge.

This is an unreleased development project. Four native artifacts provide reload-scoped barrel models, local/resource-pack profiles, independent artwork selection and optional Fresh Objects adaptation. See [verification](docs/verification.md) for the exact native and pack coverage.

The selected design adapts supported effective vanilla chest/barrel models and animation resources to Lootr, with explicit Lootr-specific profile/resource overrides taking precedence. No replacement chest geometry or vanilla model override is shipped. The Default profile contributes our Lootr-only barrel geometry without replacement artwork. Custom profiles may provide models, artwork or both, with optional adaptation of installed Fresh Objects resources. Native loot, inventories and per-player container state stay with Minecraft and Lootr.

Own code is [Apache-2.0](LICENSE). External artwork and rigs retain their own terms. Permission-pending preview content is separate from distributable product content; installed native resources remain the fallback.

Open the local settings with `/lootrstyled`, or the NeoForge Mods configuration button. Apply saves preferences and reloads resources; changing a draft or exporting Default does not apply it. Local profiles are under `config/lootr_styled/`. [Profiles](docs/profiles.md) describes precedence, supported inputs and diagnostics.

See [architecture](docs/architecture.md), [profiles](docs/profiles.md), [development](docs/development.md), [dependencies](docs/dependencies.md) and [verification](docs/verification.md). Follow [contributing](CONTRIBUTING.md) and [security reporting](SECURITY.md) for changes and disclosures.
