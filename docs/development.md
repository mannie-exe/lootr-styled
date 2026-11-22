# Development

Use Java 25 to run the Gradle Wrapper. Minecraft 1.21.1 sources compile with Java 21; 26.3 sources use Java 25. Keep toolchains available explicitly and do not replace a global Java installation. The project produces separate Fabric/NeoForge artifacts for each Minecraft target.

Routine checks are:

```sh
./gradlew --no-daemon check build
./gradlew spotlessApply
mise run check:docs
```

The exact four-target re:form dependency is recorded in [the pin manifest](../gradle/reform.json). An unpublished candidate requires an explicit staged Maven repository: append `-Preform_repository=/path/to/repository` to Gradle commands and pass `--reform-repository /path/to/repository` to the native fixture helper. The build verifies each original, unmapped library JAR before compilation; Loom may remap its runtime copy. Never substitute a sibling source directory or overwrite an already-qualified candidate version. [Dependencies](dependencies.md) owns update checks and [publishing](publishing.md) owns the public dependency gate.

The independent [native consumer](../verification/settings.gradle) loads packaged distribution JARs, not this project's source outputs. Prepare one fixture with `python3 tools/native_probe.py --target neoforge-1_21_1 --mode fresh --fetch`. After obtaining desktop access, add `--run`; add `--scene` for a disposable flat container world and rendered captures. Available targets are the four module names; modes are `native` (no optional renderer), `fresh` and `off`. The pinned optional inputs and their permitted verification scope are in [the input manifest](../verification/inputs.json). They are never product contents. The fixture acknowledges only the two pinned NeoForge 26.3 logo metadata warnings and fails on unexpected loading issues.

Ordinary fixtures select Default models and artwork. The mode selects the optional rig inputs and adaptation state; it does not substitute native artwork. Use the saved profile selection explicitly when qualifying Vanilla (Inherited) or a custom-artwork control.

Full-pack qualification uses the production launcher rather than adding the entire pack to a ModDev runtime. Build the separate NeoForge 1.21.1 probe JAR against the requested product hash, then use `python3 tools/prepare_pack_probe.py --help` to prepare a new disposable Prism instance from an owned, extracted bundled-pack projection. The helper refuses existing destinations and never reads or copies launcher accounts. Launch that instance through Prism after obtaining desktop access. In this marked fixture, the probe waits for AnalogLib's “Ask me later” action without downloading optional binaries and accepts Minecraft's experimental-settings confirmation for the disposable scene. Separate shader-off/on instances check the actual Iris API state after rendering; configuration text alone is insufficient. Keep the probe out of every product artifact and remove only these marked instances after collecting reports and confirming their JVMs stopped.

The pack helper accepts a NeoForge Minecraft 1.21.1 launch profile and declares its native LWJGL 3.3.3 dependency when absent. A copied profile containing only Minecraft and NeoForge can reach Sodium's early bootstrap without LWJGL classes on its first launch. Keep this launcher dependency explicit; removing Sodium or changing the addon cannot repair that incomplete launch profile. Existing LWJGL selections remain unchanged.

Prism's launch command can return after an IPC handoff, before the client starts or exits. Require the matching artifact report and observe the owned JVM's actual exit separately. A normal in-world disconnect can be tested separately from direct application stop. Retain a failed direct-stop or forced-cleanup result separately; a passed scene report alone does not establish a normal exit.

The disposable pack fixtures explicitly select Default models and artwork. Their observer requires packed model bindings and compares all twelve effective artwork images with the packaged inputs. Inheriting the pack's seeded artwork choice alone does not qualify Default artwork. Personal profile selections remain untouched.

If a running Prism reports that the new fixture ID is unknown, restart the launcher after its game processes have stopped so it discovers the new instance directory. Do not replace launcher account files or register a fixture by modifying a personal instance.

Build-only formal checks use `python3 tools/setup_proofs.py`, `python3 tools/check_proofs.py` and `node tools/proof_vectors.mjs`. The setup verifies pinned release archives; Node 22 or newer evaluates the generated decision module. Normal Java tests use committed evaluated vectors and need no Bend, Lean or Node runtime. After reviewing a model change, regenerate those vectors with `node tools/proof_vectors.mjs --write`. Source laws, independent kernel acceptance, deliberate refusal controls and actual Java comparisons are distinct checks.

Use ignored `build/`, target `runs/` and `artifacts/` directories for disposable research and verification. Keep one task continuation in Moment. Maintained architecture, resource contracts, dependency procedures and verification belong in their respective documents here.

Core tests exercise policy, validation and compilation without game classes. Native fixtures must use separate test-owned instances, actual installed artifacts and qualified upstream inputs. A source run is not a public artifact test. Desktop actions require cooperative access through re:desktop; stop on human pause and release when finished. Personal clients, servers and worlds are outside ordinary fixtures.

Keep CI credential-free and artifact inventories explicit. Do not publish permission-pending inputs through CI logs, caches, attachments or source JARs. Document changes to loader/private renderer hooks beside the relevant dependency maintenance procedure.

[Publishing](publishing.md) owns artifact review and pack cutover. The first implemented resource-identity value caps the complete namespaced ID at 256 characters and refuses empty/traversing path segments. That is a local input-policy bound, not a claim about Minecraft's maximum resource identifier length. Filesystem containment still belongs to the local profile input adapter.

## Artwork handoffs

An artwork handoff includes the twelve native chest/barrel PNGs, any auxiliary model maps and geometry, profile mappings, source versions and hashes, modification notes, and a comparison preview. Retain editable body, trim and mask layers alongside the flattened output when available. Keep imported donor archives and authoring inputs in local workspaces; [NOTICE](../NOTICE) owns the selected donors' credits and published terms.

Native-slot artwork inputs live under `resources/assets/lootr_styled/textures/default/`; two shared packed material maps live under `resources/assets/lootr_styled/textures/block/default/barrel/` so native atlas discovery includes them. `resources/lootr-styled-artwork.json` records its sources and fourteen exact image hashes; `resources/lootr-styled-content.json` records the eight state/UV model inputs. The built-in profile maps native-slot artwork and state-specific models to each game's Lootr slots; private source files are not unconditional Lootr overrides. Packed and projected-native models must retain identical geometry. Select UVs only after artwork validation; a refusal or explicit override must not leave prepainted Default colors under another selected artwork. Artwork updates must refresh the manifest, preserve the source notices, and pass the installable/source artifact checks. Extra, missing or changed images are refused.

Before replacing Default artwork, verify the receipt against decoded images, validate the profile, and inspect normal/trapped, looted/unlooted and physical-open barrel states. Check chest seams, alpha, apertures and barrel handles, then map resources for both Minecraft targets and qualify the affected native rendering and export paths. Update [profiles](profiles.md), [architecture](architecture.md) and [verification](verification.md) with the behavior actually integrated. An authoring export alone does not change the built-in profile or client settings.

Verify material-boundary classification independently of the lighting formula. Numerically correct shading can still place the wrong shadows when roles come from a different trim shape. For barrel-only changes, compare chest images byte-for-byte and recheck paired model UVs with actual native mip generation. For palette-only changes, require identical model documents, trim masks, alpha and pixels outside trim coverage; check the retained ramp across complete images, including packed continuation regions.
