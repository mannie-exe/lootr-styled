# Development

Use Java 25 to run the Gradle Wrapper. Minecraft 1.21.1 sources compile with Java 21; 26.3 sources use Java 25. Keep toolchains available explicitly and do not replace a global Java installation. The project will produce separate Fabric/NeoForge artifacts for each Minecraft target.

Build setup and independent native qualification are still in progress. The intended routine checks are:

```sh
./gradlew --no-daemon check build
./gradlew spotlessApply
mise run check:docs
```

Use ignored `build/`, target `runs/` and `artifacts/` directories for disposable research and verification. Keep one task continuation in Moment. Maintained architecture, resource contracts, dependency procedures and verification belong in their respective documents here.

Core tests exercise policy, validation and compilation without game classes. Native fixtures must use separate test-owned instances, actual installed artifacts and qualified upstream inputs. A source run is not a public artifact test. Desktop actions require cooperative access through re:desktop; stop on human pause and release when finished. Personal clients, servers and worlds are outside ordinary fixtures.

Keep CI credential-free and artifact inventories explicit. Do not publish permission-pending inputs through CI logs, caches, attachments or source JARs. Document changes to loader/private renderer hooks beside the relevant dependency maintenance procedure.

[Publishing](publishing.md) owns artifact review and pack cutover. The first implemented resource-identity value caps the complete namespaced ID at 256 characters and refuses empty/traversing path segments. That is a local input-policy bound, not a claim about Minecraft's maximum resource identifier length. Filesystem containment still belongs to the local profile input adapter.
