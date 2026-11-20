# Verification

On macOS arm64, the bootstrap passed `./gradlew --no-daemon check build --warning-mode all`: three core identity tests, four native compilation/packaging targets, eight artifact inventory/refusal tests and local documentation links. Source and installable artifacts are separate; class-file baselines and client metadata were inspected. `mise run check:docs` and `mise run check:ci` passed locally. No hosted CI, native Minecraft startup, rendering, profile, rig, shader or pack-cutover qualification is claimed.

Keep evidence in three distinct categories: stated model laws and kernel verdicts; actual Java conformance/refusal tests; native artifact and rendering fixtures. A passing category cannot substitute for the others. Tests must record source and artifact identity, commands, input capabilities, outcomes and remaining limitations.

Native coverage must include independent artwork/model/Fresh choices, absent and unsupported optional resources, user-edit preservation, resource priority and reload, ordinary/trapped states, native lid motion, physical-open and player-looted barrel states, and explicit Lootr override precedence. Verify that vanilla outputs and live mutable model/animation state are not modified or aliased, and that the Default profile contains no artwork. Use standalone minimum dependencies and the final 1.21.1 modpack source cutover. Shader and platform claims apply only to combinations actually exercised.

Packaging checks must refuse permission-pending preview content and upstream binaries in distributable/base/source inventories. These checks establish the tested inventory rule, not copyright ownership or author permission.

Trivy 0.76.2 read one generated Gradle inventory and reported zero HIGH/CRITICAL findings for core/test dependencies. Its source-secret scan reported no issues. Commands used `trivy fs --scanners vuln --severity HIGH,CRITICAL --exit-code 1 --config trivy.yaml build/security` and the corresponding `--scanners secret` scan of the checkout. This does not qualify all game/loader/mod dependencies, artifact publication, a hosted scan or runtime profile security.
