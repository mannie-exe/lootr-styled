# Verification

This guide separates released behavior, development checks, and unresolved limits. Historical reports retain their actual inputs; a current build does not refresh their native or hosted evidence.

## Release 0.1.0

Release 0.1.0 passes 582 Java tests and 27 tooling checks across the four Fabric/NeoForge targets for Minecraft 1.21.1 and 26.3. Core policy runs on Java 21/Gson 2.10.1 and against each native target’s Gson, including 26.3’s 2.14.0. Hosted builds pass on Linux, Windows and macOS; actual client rendering checks use macOS.

Policy and resource tests cover strict metadata, catalogue isolation, local containment and edit detection, PNG/model/reference budgets, explicit precedence, independent selections, generation retirement and native parsing. Installable and source inventories require fourteen approved images and eight models. Mutation controls reject preview artwork/rigs, foreign or probe classes, vanilla asset overrides, retired carriers and screens, duplicate entries, incorrect dependency/side metadata and Java baselines.

The settings tests use re:form’s actual file owner with the existing format 1 representation. They preserve invalid or oversized files, missing identities, no-op bytes and stale-write refusals. Controlled application tests distinguish native failure, unreadable source, mismatched generation, cancellation after native entry and closure. A confirmed save survives a failed reload; retry reads that saved revision without another write. Export retirement before metadata publication leaves an unadmitted partial copy and preserves it when a later copy succeeds. These controls do not establish native widget behavior.

### Native clients

The release 0.1.0 consumer under `verification/` loads packaged product JARs with exact requested SHA-256 identities and an external public re:form 0.2.0 library. It excludes product source outputs and a separate core JAR. Fabric 1.21.1’s Loom consumer remaps the installable artifact into its development namespace; the other targets use their pinned ModDev/Loom bootstrap. These are actual client executions with those bootstrap limits, rather than production-launcher tests.

| Target          | Integrated container scene | Startup, resource and settings checks |
| --------------- | -------------------------- | ------------------------------------- |
| Fabric 1.21.1   | Fresh AUTO                 | Fresh AUTO, OFF, no EMF               |
| NeoForge 1.21.1 | Fresh AUTO                 | Fresh AUTO, OFF, no EMF               |
| Fabric 26.3     | Fresh AUTO                 | Fresh AUTO, OFF, no EMF               |
| NeoForge 26.3   | Fresh AUTO                 | Fresh AUTO, OFF, no EMF               |

Each startup fixture observes two reload generations and independently instantiated ordinary/trapped renderer parts. Modern fixtures invoke native state-specific model submission and retain independent copper parts. Resource tests exercise both available and unavailable ordinary-chest isolation capabilities. Controlled retirement tests observe no source reads after the borrowed resource owner closes.

The native settings checks show profile titles, open the source browser through keyboard activation, search and stage resolved choices, and retain missing or invalid IDs. They preserve a valid sibling beside an invalid draft and refuse partial submission. Save and reload resources persists and applies disabled/missing selections, restores the original settings, and reaches a terminal result. Export verifies twelve active PNGs and four auxiliary barrel bindings. Normal-scale scenes use GUI scale 2; separate OFF clients use scale 4. Checks also exercise a 320×240 viewport, resize and focus retention. Exact reports retain their actual viewport and artifact identities; these checks do not establish every language, OS input layout or custom profile workload.

Container scenes place chests in four horizontal facings and barrels in six facings and both physical states. They drive native server interactions, ordered close/open operations, looted appearance, an untouched neighbor and repeated transitions. Each checks 216 neighbor/facing/state combinations against native culling controls and retains full collision. Item captures include held, inventory, dropped and framed forms. Resource reload also runs in the loaded world. These are controlled interactions, rather than adversarial packet or general multiplayer synchronization tests.

The pinned EMF 3.3.12/ETF 7.2.7/Fresh Objects 2.1.2 inputs and their hashes are in [the native manifest](../verification/inputs.json). ON diagnostics, malformed or missing profiles, explicit overrides, partial artwork, corrupt-map fallback and exhaustion have shared/native resource tests. ON does not have a separate four-target rendered scene matrix. Dedicated-server exclusion has metadata/startup-plugin controls, rather than a full dedicated-server matrix.

## Development settings interface

Development builds use the public re:form 0.3.0 inputs in [the pin manifest](../gradle/reform.json). Anonymous release downloads and a fresh Gradle-home resolver verify all four inputs without a staged repository or local fallback. The library's owned classes match the qualified dev.10 implementation.

The recorded `0.2.0-ux-dev.1` settings review passed 582 Java tests and 27 tooling checks. It preserves the compiled settings definition, public codec, assets, paths, defaults, and profile formats. Its actual macOS NeoForge 1.21.1 clients pass at GUI scales 2 and 4 and exit normally. Checks cover source browsing, Defaults and Undo over the same draft, invalid-ID retention and native repair, refusal of partial submission, save followed by the matching published resource generation, export of twelve PNGs and four barrel bindings, and restoration of settings bytes. Logical 320×240 and 900×540 reflows retain related-row expansion and narrow stacking.

Earlier re:form 0.3.0-dev.10 runs cover four-target initial loading and saved-file reopening, GUI scales 1 and 4, and minimum Compat coexistence. Four further public-library clients used the preceding product, including Compat dev.6 through its stable-version admission. Those reports retain their actual artifact identities; they are not current four-target rendering or full-pack qualification.

Native observations confirm an unresolved re:form 0.3 navigation mismatch: the sidebar follows Enabled, Models, Artwork, Fresh Objects while the rendered groups follow Enabled, Fresh Objects, Models, Artwork. Functional workflow passes do not qualify that ordering. Shared chooser separation, manual-ID containment, and action-button presentation remain library design work; the settings copy changes do not fix them. These checks do not establish physical IME, spoken audio, other-OS graphics, or a new hosted or full-pack result.

## Barrel geometry and artwork

Actual native face baking and mip generation pass on all four targets. Both appearances match at texel centers and near-boundaries through mip levels 0–4, including the 68 persistent rim cells. Native-slot models have an identical-top control and separate actual-artwork wall checks; foreign open-top filtering is not forced to match a closed lid. A shifted-grid negative preserves base texels but changes filtered samples. Modern AUTO coverage is checked against its actual alpha cutoff.

Selected-model AO is observed across all six facings and physical/material states. One hundred repeated native queries per branch retain the same quad/part objects. Separate local-profile controls enable AO and require exactly four copied private sprites. Earlier fixed-view transition captures compare 14,697 front-wall and 9,565 rim pixels with zero differences after closing/reopening the same gray barrel. Those masks establish the transition repair for that earlier paint pattern; they do not approve later artwork.

The current artwork uses five gold and four gray trim shades. Its palette-only update preserves all eight model documents, trim masks, alpha and 371,784 pixels outside trim coverage; 170,936 trim and continuation pixels use the retained ramps. The updated images pass actual native bake/mip tests. Asset equality and visual preference remain separate checks.

The retained 1.21.1 read/bake/mip workload for four independent packed state models rose from a 4.2 ms median to 22.0 ms over ten rounds after three warmups per phase. This includes redundant per-model image reads/mips and excludes atlas stitching, GPU work and frame time. These measurements describe a correctness cost, not a frame-rate improvement or elimination of section rebuilds. [Barrel rendering](barrels.md) owns geometry, state, lighting and lifetime contracts.

## Release 0.1.0 pack and publication checks

The public dependencies are downloaded anonymously and checked against release API digests and checksum manifests. Clean-checkout builds resolve the public library and reproduce all eight release archives byte for byte. Exact native-qualified implementation classes and artwork are compared with the final archives; loader version metadata remains separately checked.

The isolated NeoForge 1.21.1 Prism shader-off/on fixtures pass rendering, settings, reload and export checks with public Compat 0.42.0 and re:form 0.2.0. Both require packed Default bindings, compare twelve effective artwork images with the packaged bytes, and exercise native container interactions and 24 rendered neighbor cases. Iris’s API reports false/true respectively with Sodium 0.8.13 and Iris 1.8.14-beta.1; the shader-on input is Complementary Unbound r5.10 + Euphoria 1.11.1 + Skyweave 0.4.0. These use a retained bundled-pack projection with explicit dependency updates, rather than certifying the current modpack. Direct application stop and a separate native disconnect-before-stop control both stalled after successful reports. Both required forced fixture cleanup; normal full-pack exit remains unqualified and the cause is unresolved. All four standalone clients exit normally.

## Formal and security checks

[Model laws](../proofs/LAWS.bend) and [proofs](../proofs/PROOF.bend) pass Bend’s separate Lean-backed kernel. [Lean bounds](../proofs/Bounds.lean) check subtraction-first admission and stale publication. A deliberately broken precedence branch is refused. Thirty-two compiled decision vectors drive the actual Java compiler in every test lane, with a deliberate disagreement detected. These checks cover explicit decision/publication models; they are not proofs of the Java implementation, filesystem or renderer.

Release 0.1.0 hosted documentation, formal, and security checks pass. Trivy scans the resolved core/test inventory and tracked source secrets; that scope does not include every game, loader or mod dependency. Standalone rendering uses no Iris, and a selected 1.21.1 shader result does not qualify 26.3 shaders or arbitrary combinations. Pre-existing pack resource warnings are outside these container checks.

Retain exact source/artifact identities, commands, outcomes, representative images and limitations in compact evidence before removing owned disposable runs. Kernel verdicts, Java conformance, native execution and visual review remain separate receipts.
