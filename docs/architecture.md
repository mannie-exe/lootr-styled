# Architecture

The product has a loader-neutral core, two Minecraft-version adapters and thin native loader entries. Core policy/profile/geometry values must not import Minecraft or optional-mod classes. The native adapters own resource-manager, model-part, reload and renderer lifecycles. Shared runtime services receive each target’s admitted capabilities explicitly; consolidation must not replace them with compiler defaults. Modern ordinary-chest adaptation depends on the target’s native copper-isolation admission.

Lootr owns its container registrations, per-player opened state, loot, sounds and materials. Minecraft owns vanilla container semantics. This addon contributes selected presentation, without replacing gameplay or imposing a server component.

Effective vanilla models and their UVs are read-only inputs. Native adapters preserve source geometry and texture coordinates; unusual layouts need qualified mapping or an explicit profile. The addon publishes Lootr-specific outputs rather than vanilla resource overrides, and ships no replacement chest geometry. [Profiles](profiles.md) owns independent Models, Artwork, and Fresh Objects selections, per-slot precedence, and fallbacks. [Barrel rendering](barrels.md) owns Default's geometry and effective-artwork UV binding.

One compiler prepares a complete immutable resource generation from config/resource-pack profiles and admitted lower resources. Native publication checks the current generation and publishes model/texture/animation output together. Generated output cannot become its own input. A failed or stale candidate does not publish partially; retaining previous objects is valid only while their native lifecycle remains live.

Optional foreign classes must not resolve when their adapter is absent or disabled. Client rendering must not load on a dedicated server. Native and foreign subclass renderers need explicit scope admission; inheritance alone is not authority to modify their output.

Settings persistence, retained editing, conflicts, references, commands and operation screens use public re:form services. One staged file owner reads and writes the existing format 1 `settings.json`; profile discovery, compilation and export remain domain operations. Profile initialization never writes settings. Ordinary control rendering and search use immutable cached metadata, without filesystem or resource analysis.

Resource reload captures that same settings store on a bounded worker. Native publication binds the consumed file revision to the new generated-pack identity. Save and reload resources retains the actual persistence receipt, then separately waits for native reload and verifies the active generation and current source. A failed or cancelled reload cannot erase a confirmed save. A native future completing without the matching new generation is insufficient to report application. Closing an editor or client retires its operation authority; it does not imply rollback of a completed write or native reload.

The build-only formal layer checks stated policy/publication models. It does not execute in Minecraft or establish Java translation correctness. [Verification](verification.md) owns the actual comparison and native evidence.

The core resource envelope captures a complete owned byte set in deterministic identity order. It defensively copies mutable input and offers independent read streams; it retains no live models, sprites or source handles. [Profiles](profiles.md#resource-limits) owns intake and publication limits; these are product policy rather than native format maxima. The native reload argument adapter appends one owned generation after a borrowed lower-stack view. Generation identity and close checks prevent stale suppliers from serving output; closing the layer never closes borrowed packs. Namespace discovery prepares the immutable generation once before native listeners consume it. The hook checks client-resource type so integrated-server data reloads keep their existing inputs.

[Barrel rendering](barrels.md) owns the shared shell, state/UV, lighting, culling and cache contract. Native section rebuilds remain Minecraft/Sodium’s responsibility.
