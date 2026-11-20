# Architecture

The product has a loader-neutral core, two Minecraft-version adapters and thin native loader entries. Core policy/profile/geometry values must not import Minecraft or optional-mod classes. The native adapters own resource-manager, model-part, reload and renderer lifecycles.

Lootr owns its container registrations, per-player opened state, loot, sounds and materials. Minecraft owns vanilla container semantics. This addon contributes selected presentation, without replacing gameplay or imposing a server component.

Supported effective vanilla models and their UVs are read-only sources for Lootr adaptation. Native Lootr artwork/material state remains unless an explicit texture override is supplied. Do not impose canonical UVs or alter the source shape; unusual UV layouts need qualified material/texture mapping or a profile. Explicit Lootr-specific model/artwork resources and selected profiles override inherited contributions per slot. Default supplies only our Lootr barrel geometry, using installed native artwork. The addon ships no replacement chest geometry and does not modify vanilla model resources. Fresh Objects adaptation is AUTO/ON/OFF, default AUTO; native lid motion remains available without it. Missing or unsupported inputs retain a reported safe fallback.

One compiler prepares a complete immutable resource generation from config/resource-pack profiles and admitted lower resources. Native publication checks the current generation and publishes model/texture/animation output together. Generated output cannot become its own input. A failed or stale candidate does not publish partially; retaining previous objects is valid only while their native lifecycle remains live.

Optional foreign classes must not resolve when their adapter is absent or disabled. Client rendering must not load on a dedicated server. Native and foreign subclass renderers need explicit scope admission; inheritance alone is not authority to modify their output.

The build-only formal layer checks stated policy/publication models. It does not execute in Minecraft or establish Java translation correctness. [Verification](verification.md) owns the actual comparison and native evidence as implementation proceeds.
