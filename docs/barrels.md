# Barrel rendering

Lootr owns the block, container, viewer count, physical `OPEN`, facing and per-player looted appearance. Styled changes client resources and selected lighting policy. It does not change collision, selection, support, inventories, comparator behavior, drops or light propagation.

## Geometry and material

Default uses a common shell and rim, with separate lid/handle/roof fill and revealed interior. Closed models contain 32 cuboids and 76 quads; open models contain 25 cuboids and 98 quads. Their occupied cells exactly match the owner's voxel reference: 3,912 closed and 1,272 open. The closed model has one fully covered internal quad because native elements require a face. The open cavity has inward walls and a solid floor, without an opaque carrier.

Unchanged outer walls retain the same quad boundaries, orientation and texture binding. Persistent rim regions share continuous texture fields. The current material pattern also keeps the shared underside consistent between states.

Barrel trim shading uses membership of the actual exposed face. Importing shadow roles from a differently shaped trim can introduce isolated dark strips, even when the numerical lighting response matches its formula. The texture generator owns that classification and its regressions; the client publishes the admitted artwork without recoloring it. Chest artwork uses its separately approved treatment.

Two 512×512 material maps serve gold and gray. Physical open/closed variants use the same sprite for their material. Fields have 16-pixel alignment and gutters for mip levels 0–4. These maps supplement the twelve native chest/barrel images. Source artwork remains separate from compiled padding. The manifests record all fourteen images, eight model inputs and their source hashes.

Models and Artwork remain independent. Packed maps require the matching effective Default artwork for all four native slots of that material. An explicit image override, including a byte-identical one, uses projected native slots instead. Partial, missing or refused artwork never leaves a hidden Default skin underneath another selection. Native-slot variants preserve the same geometry; the cavity floor binds bottom artwork. Custom models and their auxiliary textures keep their declared UVs. [Profiles](profiles.md) owns selection, precedence, admission and export.

## Lighting and state

Default declares AO off and directional shading on. World/block light and shaders still apply. Authored trim shading is neither emission nor fullbright rendering.

On 1.21.1, a resource-generation-local marker identifies Styled-generated plain models. The NeoForge wrapper delegates AO to the selected generated child; the Fabric wrapper retains its native selected child and uses an AO-off material from the current renderer. Other branches retain native lighting. Adapters refuse an unknown wrapper ABI. Modern native model parts already carry their selected lighting policy. [Dependencies](dependencies.md) owns those version-specific update checks.

Physical `OPEN` selects geometry. Lootr's player-looted value selects gold/gray independently. Facing applies the native six-way transform. Item forms use their native closed-model definitions and transforms, without reading a placed barrel's player state. No client-selected style becomes server authority.

The opaque outer walls, floor and continuous boundary rim seal adjoining full-cube contacts. Native neighbor-face culling is retained. An open mouth or recessed cap alone does not establish a visible culling defect. Own interior/inset faces have no boundary `cullface` tags. Resource authors retain responsibility for intentionally different custom shapes and alpha within Minecraft's native block assumptions.

## Lifetime and cost

Resource reload compiles bounded bytes; native model bake creates variants and quads. Opening selects existing baked models. It performs no file IO, image generation or procedural meshing. Native quad arrays are mutable inputs, so Styled does not force cross-model array sharing. Equal source surfaces and shared sprites preserve appearance without introducing unsafe aliases.

Lighting markers retain no source handles. The Fabric material cache belongs to its baked wrapper and current renderer/material identity; it retires with that resource generation. There is no position/player cache or global selected-state variable. Minecraft/Sodium may still rebuild and upload a containing section when a state changes.

The two padded maps occupy 524,288 base texels and 2,793,472 decoded RGBA bytes through mip 4, compared with 349,184 bytes for the former four 128×128 maps. This is a visual-correctness cost, not a claimed frame-rate improvement. The existing image and aggregate publication budgets are unchanged. [Verification](verification.md) separates native sampling, cached queries, rendered scenes and launcher evidence.
