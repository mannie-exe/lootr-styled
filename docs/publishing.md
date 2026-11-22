# Publishing and pack integration

The build produces four installable artifacts and four source artifacts under `build/distributions/`. [Verification](verification.md) records the exercised presentation features and their limits. Local instance qualification and public publication are separate operations.

Before a release, complete the current product's native acceptance and review the actual distribution inventory. Set the version through `mod_version` in `gradle.properties`, run the applicable independent tests and `./gradlew --no-daemon check build`, then retain SHA-256 identities for the exact outputs. A passing bootstrap or configured CI is insufficient for a renderer release.

The selected re:form dependency must resolve publicly with the exact game/loader bytes in [the pin manifest](../gradle/reform.json). Local candidate qualification uses an explicitly staged Maven repository and does not establish hosted availability. Promote or select a public library release, refresh its four hashes and metadata together, and rerun the affected acceptance before publishing this addon. Keep the library external and include its installation requirement in release instructions.

Verify client-only loading, native/default and resource-pack model inheritance, explicit Lootr override priority, profile/reload behavior and optional rig absence against each advertised target. Retain the tested upstream dependency and artifact identities. Do not extrapolate one loader, Minecraft version or shader result to the others.

Inspect both installable and source artifacts for donor files, pending artwork, foreign classes and accidental vanilla output overrides. Permission-pending previews remain local and separate from the base. Source license text does not clear artwork for publication. Do not copy another product's security exceptions without reviewing their exact applicability.

When artwork is included, carry its source credits, license links and modification notices with the assets and in the bundled [NOTICE](../NOTICE). Keep Apache-2.0 code terms distinct from artwork terms and record each donor's permission basis. [Development](development.md#artwork-handoffs) owns handoff checks.

Publication requires explicit authorization. The build workflow retains artifacts for review and the security workflow checks its documented scope; neither publishes a release. A future delivery workflow must verify the version, exact artifact set and hashes before creating a draft or public release.

The modpack cutover is a separate source integration. Pin the qualified candidate/release, remove only the duplicate Lootr-specific generated contributions and preserve the selected vanilla composition, Atlas and relighting tools. Keep pack-managed rules and explicitly seeded player/profile edits with their respective owners. Verify the final effective resource order in an isolated client. Personal instance synchronization or server/world operations require their own authorization.

For an authorized Prism update, stop Minecraft and verify the existing instance root contains `instance.cfg`, `mmc-pack.json` and `.empack/instance.json` before running instance commands there. An authoring checkout is not an instance root. Compare release entries first and preserve settings, worlds and unrelated candidates. Reconcile the installed ledger/release archive, `.minecraft/.empack-consumer/release.json`, its referenced assets and `.empack-tools/prepare.json` together. The release digest must agree in the ledger, consumer file and prepare arguments. Run native `empack instance ensure` with that exact release identity and require no file changes before relaunch.
