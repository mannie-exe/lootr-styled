# Development

Use Java 25 to run the Gradle Wrapper. Minecraft 1.21.1 sources compile with Java 21; 26.3 sources use Java 25. Keep toolchains available explicitly and do not replace a global Java installation. The project will produce separate Fabric/NeoForge artifacts for each Minecraft target.

Routine checks are:

```sh
./gradlew --no-daemon check build
./gradlew spotlessApply
mise run check:docs
```

The independent [native consumer](../verification/settings.gradle) loads packaged distribution JARs, not this project's source outputs. Prepare one fixture with `python3 tools/native_probe.py --target neoforge-1_21_1 --mode fresh --fetch`. After obtaining desktop access, add `--run`; add `--scene` for a disposable flat container world and rendered captures. Available targets are the four module names; modes are `native` (no optional renderer), `fresh` and `off`. The pinned optional inputs and their permitted verification scope are in [the input manifest](../verification/inputs.json). They are never product contents. The fixture acknowledges only the two pinned NeoForge 26.3 logo metadata warnings and fails on unexpected loading issues.

Full-pack qualification uses the production launcher rather than adding the entire pack to a ModDev runtime. Build the separate NeoForge 1.21.1 probe JAR against the requested product hash, then use `python3 tools/prepare_pack_probe.py --help` to prepare a new disposable Prism instance from an owned, extracted bundled-pack projection. The helper refuses existing destinations and never reads or copies launcher accounts. Launch that instance through Prism after obtaining desktop access. Accept the native experimental-world prompt for this disposable scene if shown. Separate shader-off/on instances check the actual Iris API state after rendering; configuration text alone is insufficient. Keep the probe out of every product artifact and remove only these marked instances after collecting reports and confirming their JVMs stopped.

Build-only formal checks use `python3 tools/setup_proofs.py`, `python3 tools/check_proofs.py` and `node tools/proof_vectors.mjs`. The setup verifies pinned release archives; Node 22 or newer evaluates the generated decision module. Normal Java tests use committed evaluated vectors and need no Bend, Lean or Node runtime. After reviewing a model change, regenerate those vectors with `node tools/proof_vectors.mjs --write`. Source laws, independent kernel acceptance, deliberate refusal controls and actual Java comparisons are distinct checks.

Use ignored `build/`, target `runs/` and `artifacts/` directories for disposable research and verification. Keep one task continuation in Moment. Maintained architecture, resource contracts, dependency procedures and verification belong in their respective documents here.

Core tests exercise policy, validation and compilation without game classes. Native fixtures must use separate test-owned instances, actual installed artifacts and qualified upstream inputs. A source run is not a public artifact test. Desktop actions require cooperative access through re:desktop; stop on human pause and release when finished. Personal clients, servers and worlds are outside ordinary fixtures.

Keep CI credential-free and artifact inventories explicit. Do not publish permission-pending inputs through CI logs, caches, attachments or source JARs. Document changes to loader/private renderer hooks beside the relevant dependency maintenance procedure.

[Publishing](publishing.md) owns artifact review and pack cutover. The first implemented resource-identity value caps the complete namespaced ID at 256 characters and refuses empty/traversing path segments. That is a local input-policy bound, not a claim about Minecraft's maximum resource identifier length. Filesystem containment still belongs to the local profile input adapter.
