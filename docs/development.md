# Development

Use Java 25 to run the Gradle Wrapper. Minecraft 1.21.1 sources compile with Java 21; 26.3 sources use Java 25. Keep toolchains available explicitly and do not replace a global Java installation. The project produces separate Fabric/NeoForge artifacts for each Minecraft target.

Use Python 3.11 or newer for the repository tools. Install the tools declared in `mise.toml` for Markdown and workflow checks. The formal checks additionally need Node 22 or newer and `tar` with Zstandard support; their pinned Bend and Lean tools stay local to `.tools/`.

## Build and checks

Routine checks are:

```sh
./gradlew --no-daemon check build
./gradlew spotlessApply
mise run check:docs
mise run check:ci
```

The exact four-target re:form dependency is recorded in [the pin manifest](../gradle/reform.json). An unpublished candidate requires an explicit staged Maven repository: append `-Preform_repository=/path/to/repository` to Gradle commands and pass `--reform-repository /path/to/repository` to the native fixture helper. The build verifies each original, unmapped library JAR before compilation; Loom may remap its runtime copy. Never substitute a sibling source directory or overwrite an already-consumed candidate version. [Dependencies](dependencies.md) owns update checks and [publishing](publishing.md) owns the public dependency gate.

## Native client fixtures

The independent [native consumer](../verification/settings.gradle) loads packaged distribution JARs, not this project's source outputs. Prepare one fixture with `python3 tools/native_probe.py --target neoforge-1_21_1 --mode fresh --fetch`. After obtaining desktop access, add `--run`; add `--scene` for a disposable flat container world and rendered captures. Available targets are the four module names; modes are `native` (no optional renderer), `fresh` and `off`. The pinned optional inputs and their permitted verification scope are in [the input manifest](../verification/inputs.json). They are never product contents. The fixture acknowledges only the two pinned NeoForge 26.3 logo metadata warnings and fails on unexpected loading issues.

Use `--graphics-only` for a separate low-level chest/barrel mesh draw through re:form’s viewport. It checks static geometry, still textures, native use/release, and unchanged fixture settings. It excludes editor interaction and integrated-world checks; combine neither its result nor its flag with `--scene`. Ordinary preparation clears this selection so a later settings check cannot silently run the narrower scope.

Use `--preview-host` to exercise the retained settings owner, actual domain provider, worker preparation, and shared GPU host. The fixture stages an unsaved artwork selection, verifies the resulting images, mounts the shared widget for drawing, refuses an invalid draft before preparation, and checks repair, changed-draft revocation, entered cancellation/replacement, resource-generation changes, late closure, and native/scene retirement. Its observer mounting is separate from the final production layout. The host fixture requests a normal stop with a live submitted preview, observes the native renderer-close return, and writes a final JVM-shutdown receipt. The launcher waits for process exit and requires matching complete reports; the observer never performs cleanup. Choose one of `--preview-host`, `--graphics-only`, or `--scene`; ordinary preparation clears both special preview selections.

Ordinary fixtures select Default models and artwork. The mode selects the optional rig inputs and adaptation state; it does not substitute native artwork. Use the saved profile selection explicitly when qualifying Vanilla (Inherited) or a custom-artwork control.

The host fixture also stages every built-in Models/Artwork combination without saving. It checks chest, trapped chest, and both physical barrel states through the default provider, including native AO facts, disclosed reference lighting, drawing, and retirement. Its separate combinations report retains partial results when one selection fails.

A separate owner in that fixture uses its own settings file, read-only access, and a controllable editor executor with the same domain definition and provider. It checks read-only drawing, read revocation before publication and after preparation, and retirement after a submitted draw while the old executor rejects work. The observer waits for automatic native release before closing the remaining fixture objects. Both its file and the runtime settings must remain unchanged. This does not introduce permissions or another writer into the production local-file binding.

The host fixture also injects a post-cleanup failure for an exact native lease, after its real GPU deletion method returns. Separate cases check deferred release after view closure, immediate release without an outstanding draw, and release of an old interest during replacement. They require truthful failed-release state, retained output accounting, exactly one host diagnostic, a closed view remaining closed, and a newer result retaining its own outcome. The healthy replacement must still retire. This fault hook is verification-only and stays unarmed for unrelated graphics. It does not simulate uncertain or partial GPU deletion, and the fixture never calls a fabricated native teardown to clear a failure.

### Prism pack fixtures

Full-pack qualification uses the production launcher rather than adding the entire pack to a ModDev runtime. Build the separate NeoForge 1.21.1 probe JAR against the requested product hash, then use `python3 tools/prepare_pack_probe.py --help` to prepare a new disposable Prism instance from an owned, extracted bundled-pack projection. The helper refuses existing destinations and never reads or copies launcher accounts. Launch that instance through Prism after obtaining desktop access. In this marked fixture, the probe waits for AnalogLib's “Ask me later” action without downloading optional binaries and accepts Minecraft's experimental-settings confirmation for the disposable scene. Separate shader-off/on instances check the actual Iris API state after rendering; configuration text alone is insufficient. Keep the probe out of every product artifact and remove only these marked instances after collecting reports and confirming their JVMs stopped.

The pack helper accepts a NeoForge Minecraft 1.21.1 launch profile and declares its native LWJGL 3.3.3 dependency when absent. A copied profile containing only Minecraft and NeoForge can reach Sodium's early bootstrap without LWJGL classes on its first launch. Keep this launcher dependency explicit; removing Sodium or changing the addon cannot repair that incomplete launch profile. Existing LWJGL selections remain unchanged.

Prism's launch command can return after an IPC handoff, before the client starts or exits. Require the matching artifact report and observe the owned JVM's actual exit separately. A normal in-world disconnect can be tested separately from direct application stop. Retain a failed direct-stop or forced-cleanup result separately; a passed scene report alone does not establish a normal exit.

The disposable pack fixtures explicitly select Default models and artwork. Their observer requires packed model bindings and compares all twelve effective artwork images with the packaged inputs. Inheriting the pack's seeded artwork choice alone does not qualify Default artwork. Personal profile selections remain untouched.

If a running Prism reports that the new fixture ID is unknown, restart the launcher after its game processes have stopped so it discovers the new instance directory. Do not replace launcher account files or register a fixture by modifying a personal instance.

## Formal checks

Build-only formal checks use `python3 tools/setup_proofs.py`, `python3 tools/check_proofs.py` and `node tools/proof_vectors.mjs`. The setup verifies pinned release archives; Node 22 or newer evaluates the generated decision module. Normal Java tests use committed evaluated vectors and need no Bend, Lean or Node runtime. After reviewing a model change, regenerate those vectors with `node tools/proof_vectors.mjs --write`. Source laws, independent kernel acceptance, deliberate refusal controls and actual Java comparisons are distinct checks.

## Retained output and recovery

Use ignored `.cache/`, `build/`, target `runs/` and `artifacts/` directories for disposable research and verification. Keep one task continuation in Moment. Maintained architecture, resource contracts, dependency procedures and verification belong in their respective documents here.

Core tests exercise policy, validation and compilation without game classes. Native fixtures must use separate test-owned instances, actual installed artifacts and qualified upstream inputs. A source run is not a public artifact test. Desktop actions require cooperative access through re:desktop; stop on human pause and release when finished. Personal clients, servers and worlds are outside ordinary fixtures.

Keep CI credential-free and artifact inventories explicit. Do not publish permission-pending inputs through CI logs, caches, attachments or source JARs. Document changes to loader/private renderer hooks beside the relevant dependency maintenance procedure.

Use `--help` with `native_probe.py`, `prepare_pack_probe.py`, `setup_proofs.py`, `check_proofs.py`, and `verify_distribution.py` for their arguments. `tools/reform.py` is an imported dependency resolver. `python3 tools/verify_distribution.py VERSION` checks the eight staged archives against the current manifests. Use the exact `mod_version` in `gradle.properties`. It inspects outputs; it does not build, repair, or publish them. [Publishing](publishing.md) owns artifact review and pack cutover.

Fixture preparation writes a marked directory under `verification/<target>/runs/`, resets its test settings and options, and clears the previous report and named probe captures. Copy consequential evidence elsewhere before preparing it again. A live PID marker refuses preparation; inspect the actual process and working directory before removing a stale marker. The Prism helper always requires a new destination. It copies an owned projection, including that projection's game files, so use a disposable source without accounts or personal saves.

An interrupted preparation or export is not a rollback. Inspect its marked output before retrying. A timeout report does not establish a normal JVM exit; confirm the owned process stopped before removing a fixture. Preserve the failed report, exact inputs, and logs separately from a later successful run.

Proof setup retains a downloaded archive if its hash fails, including after an interrupted download. Check the named file and pinned hash, remove only that failed archive from `.tools/`, then rerun setup. Do not change the pin to accept it. Repeated proof checks replace `build/proofs/`; retain a consequential failure before rerunning. `proof_vectors.mjs` writes only build output unless `--write` explicitly replaces the committed vectors.

## Artwork handoffs

An artwork handoff includes the twelve native chest/barrel PNGs, any auxiliary model maps and geometry, profile mappings, source versions and hashes, modification notes, and a comparison preview. Retain editable body, trim and mask layers alongside the flattened output when available. Keep imported donor archives and authoring inputs in local workspaces; [NOTICE](../NOTICE) owns the selected donors' credits and published terms.

Native-slot artwork inputs live under `resources/assets/lootr_styled/textures/default/`; two shared packed material maps live under `resources/assets/lootr_styled/textures/block/default/barrel/` so native atlas discovery includes them. `resources/lootr-styled-artwork.json` records its sources and fourteen exact image hashes; `resources/lootr-styled-content.json` records the eight state/UV model inputs. The built-in profile maps native-slot artwork and state-specific models to each game's Lootr slots; private source files are not unconditional Lootr overrides. Packed and projected-native models must retain identical geometry. Select UVs only after artwork validation; a refusal or explicit override must not leave prepainted Default colors under another selected artwork. Artwork updates must refresh the manifest, preserve the source notices, and pass the installable/source artifact checks. Extra, missing or changed images are refused.

Before replacing Default artwork, verify the receipt against decoded images, validate the profile, and inspect normal/trapped, looted/unlooted and physical-open barrel states. Check chest seams, alpha, apertures and barrel handles, then map resources for both Minecraft targets and qualify the affected native rendering and export paths. Update [profiles](profiles.md), [architecture](architecture.md) and [verification](verification.md) with the behavior actually integrated. An authoring export alone does not change the built-in profile or client settings.

Verify material-boundary classification independently of the lighting formula. Numerically correct shading can still place the wrong shadows when roles come from a different trim shape. For barrel-only changes, compare chest images byte-for-byte and recheck paired model UVs with actual native mip generation. For palette-only changes, require identical model documents, trim masks, alpha and pixels outside trim coverage; check the retained ramp across complete images, including packed continuation regions.
