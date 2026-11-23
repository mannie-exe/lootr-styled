# Lootr: Styled

## Features and getting started

Client-side chest and barrel styling for [Lootr](https://modrinth.com/mod/lootr), developed under inherent.design. Supports Minecraft 1.21.1 and 26.3 on Fabric and NeoForge.

- Inherit supported vanilla/resource-pack models or choose custom profiles.
- Select models and artwork independently, with optional Fresh Objects animation support.
- Export artwork or the Default barrel model for editing.

Install Lootr and [re:form 0.2.0](https://github.com/mannie-exe/reform/releases/tag/v0.2.0) for your game and loader, then add the matching [Lootr: Styled release](https://github.com/mannie-exe/lootr-styled/releases/tag/v0.1.0) to the client. Open `/lootrstyled`, or use the NeoForge Mods configuration button. Choose Models and Artwork, then Save and reload resources. Local profiles live under `config/lootr_styled/`.

## Development status

Version 0.1.0 provides client-side model and artwork profiles with re:form settings. [Verification](docs/verification.md) records tested behavior and limits. Default includes the selected chest and barrel artwork. Layered trim, emission, and recoloring remain design work.

Development targets re:form 0.4 for shared navigation, responsive settings, and isolated profile previews. The selected engineering inputs are pinned in [the dependency manifest](gradle/reform.json); they require an explicit staged repository. Profiles shares the settings draft and draws explicitly requested previews. Full UI qualification remains in progress. See [development](docs/development.md) for build instructions.

## Further reading and participation

| Guide                                  | Owns                                                          |
| -------------------------------------- | ------------------------------------------------------------- |
| [Profiles](docs/profiles.md)           | Settings, selection, precedence, profile formats, and exports |
| [Architecture](docs/architecture.md)   | Module boundaries, resource publication, and lifetimes        |
| [Barrel rendering](docs/barrels.md)    | Geometry, state, lighting, culling, and asset costs           |
| [Interface writing](docs/interface.md) | Labels, feedback, and shared UI ownership                     |
| [Development](docs/development.md)     | Prerequisites, commands, fixtures, and artwork handoffs       |
| [Dependencies](docs/dependencies.md)   | Input pins, adapters, updates, and retirement                 |
| [Verification](docs/verification.md)   | Observed checks and their limits                              |
| [Publishing](docs/publishing.md)       | Release checks and pack cutover                               |

Follow [contributing](CONTRIBUTING.md) and [security reporting](SECURITY.md) for changes and disclosures.

## Licensing and attribution

Project code by inherent.design contributors is licensed under [Apache-2.0](LICENSE). Artwork and rigs retain their own terms; [NOTICE](NOTICE) records source permissions and modifications. The selected artwork combines adjusted Pixel Perfection Legacy dark-oak wood, recolored Spectral chest trims and an owner-authored barrel trim pattern.

| Source                                                                               | Authors                          | Contribution                              | Terms                                                                                   |
| ------------------------------------------------------------------------------------ | -------------------------------- | ----------------------------------------- | --------------------------------------------------------------------------------------- |
| [Lootr](https://github.com/LootrMinecraft/Lootr)                                     | Noobanidus and contributors      | Original mod and native container formats | [MIT code; assets reserved](https://www.curseforge.com/minecraft/mc-mods/lootr/license) |
| [Pixel Perfection Legacy](https://modrinth.com/resourcepack/pixel-perfection-legacy) | XSSheep; continued by NovaWostra | Dark-oak wood                             | [Published permissions](https://modrinth.com/resourcepack/pixel-perfection-legacy)      |
| [Spectral](https://modrinth.com/resourcepack/spectral)                               | Fulmine                          | Chest trim artwork                        | [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)                   |
