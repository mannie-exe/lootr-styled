# Dependencies and maintenance

The four targets use native Loom or ModDevGradle, with a shared Java-only core. Minecraft 1.21.1 needs Loom's remapping plugin and official mappings on Fabric. Unobfuscated Minecraft 26.3 uses the non-remapping Loom plugin. These are build distinctions, not evidence that private renderer APIs match.

Lootr is the initial companion dependency. Inspect runtime metadata rather than deriving version predicates from archive names. The inspected 1.21.1 Lootr version includes a Minecraft prefix; 26.3 uses a different version format. Its code license and asset permissions are separate. External mods and resource packs are not embedded.

EMF/ETF/Fresh Objects capability admission must inspect the effective supported resources. Filename changes and installed-but-disabled packs do not establish availability. Keep adapters conditional before optional class resolution and document unsupported structures with native fallback.

When updating Minecraft, a loader, Lootr or a rig adapter, inspect exact model parts, pivots, texture/material selection, resource lookup names and reload retirement. Update admitted ranges from actual ABI/capability evidence; do not silently widen coverage from a successful build. Repeat the affected absence, native rendering, reload, profile and artifact cases. Pack cutover must retain exactly one active Lootr presentation owner while preserving the existing pack-owned vanilla chest composition. Do not remove its selected geometry donor merely because runtime Lootr adaptation replaces the old Lootr outputs.

Build-only Bend/Lean tools are planned for bounded verification. They will have pinned installers and independent kernel checks; no JVM bridge or runtime dependency is assumed. Source pins, formal laws, Java comparisons and native evidence must remain separately attributable.

Build pins are owned by [the version catalogue](../gradle/libs.versions.toml) and [wrapper properties](../gradle/wrapper/gradle-wrapper.properties). Gradle 9.7.1 runs with Java 25; the shared core and 1.21.1 outputs use Java 21 class files, and 26.3 outputs allow Java 25. Loom's unobfuscated target uses ordinary implementation dependencies; its legacy target uses remapped mod dependencies. Do not interchange these configurations when updating tooling.

The [build workflow](../.github/workflows/build.yml) configures three operating systems, with both Java toolchains and all four native targets. These are configured checks, not observed hosted results. Renovate is configured with deliberate Minecraft/Java-major boundaries; bot installation and actual repository access remain separate external state. No release is automatically published by this bootstrap.

Trivy's configured scope is the resolved core/test library inventory and tracked source secrets. It does not certify all Minecraft, loader, Lootr or installed resource-pack dependencies. No sibling project's vulnerability exceptions are inherited. Review actual findings and applicability before adding a narrow, expiring exception.

Lootr's barrel model IDs changed between the inspected targets. The 1.21.1 leaves use `lootr_barrel_unopened`, `lootr_barrel_unopened_open`, `lootr_opened_barrel` and `lootr_opened_barrel_open`; 26.3 removes the `lootr_` prefix. Keep version-specific target maps rather than publishing one guessed model path on both versions. Lootr still distinguishes player-looted material from physical-open state.
