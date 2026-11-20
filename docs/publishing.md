# Publishing and pack integration

No release is currently qualified or authorized. The build produces four installable artifacts and four source artifacts under `build/distributions/`. Implemented presentation features and native qualification are listed in [verification](verification.md). The current GitHub repository is private while proof-of-concept permissions and implementation are developed.

Before a release, complete the current product's native acceptance and review the actual distribution inventory. Set the version through `mod_version` in `gradle.properties`, run the applicable independent tests and `./gradlew --no-daemon check build`, then retain SHA-256 identities for the exact outputs. A passing bootstrap or configured CI is insufficient for a renderer release.

Verify client-only loading, native/default and resource-pack model inheritance, explicit Lootr override priority, profile/reload behavior and optional rig absence against each advertised target. Retain the tested upstream dependency and artifact identities. Do not extrapolate one loader, Minecraft version or shader result to the others.

Inspect both installable and source artifacts for donor files, pending artwork, foreign classes and accidental vanilla output overrides. Permission-pending previews remain local and separate from the base. Source license text does not clear artwork for publication. Do not copy another product's security exceptions without reviewing their exact applicability.

Publication requires explicit authorization. The build workflow retains artifacts for review and the security workflow checks its documented scope; neither publishes a release. A future delivery workflow must verify the version, exact artifact set and hashes before creating a draft or public release.

The modpack cutover is a separate source integration. Pin the qualified candidate/release, remove only the duplicate Lootr-specific generated contributions and preserve the selected vanilla composition, Atlas and relighting tools. Keep pack-managed rules and explicitly seeded player/profile edits with their respective owners. Verify the final effective resource order in an isolated client. Personal instance synchronization or server/world operations require their own authorization.
