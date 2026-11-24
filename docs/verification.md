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

The re:form 0.4 migration is incomplete. The current engineering inputs are recorded in [the pin manifest](../gradle/reform.json); historical reports retain their original inputs. The four-target production-task baseline below uses Lootr: Styled development candidate 17 with re:form development candidate 33. The newer meta-screen adoption, candidate 18 with re:form candidate 35, has a bounded NeoForge 1.21.1 check. Final combined acceptance remains open.

### Chooser, review, and operation screens

The NeoForge 1.21.1 meta-screen check selects Vanilla (Inherited) by its friendly title, verifies its resolved reference ID, and stages it through the existing service. The actual review index follows the form's displayed field order while the captured entries retain schema order. Review and Back preserve the retained draft, saved bytes, and published resource generation.

The immediate Save and reload resources button has its effect name. Its separate Inspect result control opens the same operation ID without another write or resource generation. The remaining settings and export checks complete, restore the saved values, and exit normally. This is one packaged client check, rather than a new four-target or full-pack qualification.

### Production Profiles task

Packaged clients check the registered production task on all four targets. Home initially admits no editor. Entering Profiles admits the settings editor, which remains the same object through Home and Appearance. Inspection leaves the draft unchanged; Use for Models and Use for Artwork stage their actual resource-ID fields independently. Both selections restore through the same document actions without saving. Entry and selection leave tracked native and retained preview bytes at zero before any explicit preparation.

The actual Prepare draft preview button draws a closed, unlooted Default barrel through the registered provider and shared native renderer. The inspected list item remains Vanilla (Inherited), while the draft retains Default Models and Artwork. Preparation leaves the draft and settings bytes unchanged. Changing the subject to an open barrel without preparing again retires the submitted preview and both tracked ledgers reach zero, without another rendered submission. The existing retained-input, chooser, save/reload, and export controls then pass, restore the settings file, and exit normally. These observers use public widget actions, host accounting, and a verification-only draw hook; they do not mount a substitute viewport.

All four targets cycle all twelve production subject choices: ordinary and trapped chests and barrels, each looted/unlooted and closed/open. Every explicit preparation draws, and every following subject change retires before another request. These are static reference poses; a rig's empty native lid attachment does not become animated geometry. Draw acceptance and retirement do not establish pixel-perfect pose differences or world-render parity.

Show preview reveals the whole viewport within its actual native pane, retains the editor, and focuses the read-only graphic. Idle and ready narration include its identity and state. The scale 4 capture shows the complete barrel inside an 83-pixel-high logical viewport. Discarding and reopening binds a fresh editor: the rebuilt task stages Models independently, restores the draft, and preserves the file. These checks use public controls and enclosing-pane hit tests, without reading private task state.

NeoForge 1.21.1 and Fabric 26.3 also pass a separate submitted-use discard and normal-stop check. The observer discards the actual settings session with one native use outstanding, waits for real release completion and zero tracked allocations, and reopens a fresh editor while retaining the workflow. Another explicit preparation requests application stop with a submitted use still outstanding. The actual renderer-close return and JVM epilogue confirm release completion, zero tracked allocations, closed workflow/editor scopes, and unchanged settings. Both processes exit normally. The observer calls the public discard action, without invoking native cleanup. This does not qualify device loss or resolve the historical full-pack shutdown stall.

A separate candidate 18/re:form 35 check on NeoForge 1.21.1 and Fabric 26.3 prepares one actual production preview, activates Rotate left, Zoom in, and Reset view through native keyboard controls, and resizes the OS window to 320×240 and back. Actual draw observations retain the same graphic and decoded scene. The editor, draft revision, search widget, query, caret, settings bytes, and resource generation remain unchanged. Show preview focuses the fully contained graphic at each size; closing releases both tracked ledgers. Fabric 26.3 starts with GUI scale 4, whose effective scale changes with window size. These are programmatic native keyboard/window events, rather than physical-input tests or frame-time measurements.

These checks do not establish the complete production lifecycle. Subject replacement during entered preparation, source replacement, and complete accessibility remain open. Programmatic native controls do not establish physical input or uninterrupted OS focus. Earlier settings and isolated-preview results below retain their separate inputs and scope.

### Settings and navigation

The earlier packaged settings checks pass on all four targets. Home leaves the appearance editor unopened and settings unchanged. Styling and Profile selection focus through their declared section identities; an absent section refuses. Section and field order match the rendered body without changing codec order. Navigation and a Home round trip retain the actual editor, actions, draft revision, and invalid input.

The same runs exercise profile search and selection, Defaults and Undo, missing and malformed IDs, native repair, refusal of partial submission, save followed by matching resource publication, active-artwork export, and restoration of settings bytes. They use GUI scale 2 on three targets and scale 4 on Fabric 26.3, plus logical 320×240 and 900×540 reflows. Large-scale panes scroll rather than keeping every row visible. Navigation-driven checks await an identified document reveal before inspecting controls.

The updated four-target chooser checks measure native layouts at 1100×540, 320×240, and 1100×540. Two panes become one and return to two while the same query and inspected-ID widgets retain their values, query caret, and native focus path. Keyboard collapse of repaired manual-ID input preserves the draft revision and file bytes and focuses the new disclosure control. These logical resizes occur within one client tick; screenshots show the restored native viewport, rather than every intermediate size. Each run also requires the actual Models widget in the mounted workflow tree when normal document reveal completes. This does not reproduce the library's separate cached-page tick navigation case.

These checks do not qualify the final profile/preview layout, complete chooser/task presentation, physical IME, spoken audio, other-OS graphics, or a new hosted/full-pack result.

### Isolated previews

Packaged NeoForge 1.21.1 and Fabric 26.3 fixtures exercise the actual domain provider, worker, and shared GPU host. A paused worker demonstrates cancellation, replacement waiting for entered work to drain, reload-generation invalidation, fresh recovery, and rejection of late results after workflow closure. Closing immediately after submission observes an outstanding native use before both tracked ledgers settle at zero. Preparation and drawing leave settings unchanged.

Independent verification owners exercise READ without WRITE, READ revocation before and after preparation, explicit recovery, and context retirement with an executor that actually rejects work. Native resources retire without reviving the editor or dispatching cleanup through its stopped executor. A separate writable owner requires save review: the full dirty candidate still previews, while submission remains guarded, the revision is retained, and the review validator is never invoked. These fixtures do not change the production local-file menu's permissions or review policy.

Verification-only faults occur after an exact lease's native deletion method returns. Immediate and deferred failures each retain the cause, report one host failure, preserve closed state, and keep failed output conservatively accounted for. An old interest's failure preserves a newer ready result; the healthy replacement retires normally. This covers post-cleanup failure reporting, not uncertain deletion, device loss, or general GPU recovery. The original missing deferred diagnostic remains a separate negative.

Normal-stop checks leave the workflow, editor, and submitted preview live. Release completes and both tracked ledgers reach zero by the renderer-close return; a passive JVM epilogue confirms closed scopes and unchanged settings. Passing requires a normal process exit and matching final receipts. Crashes, device loss, and the historical full-pack shutdown stall remain outside this result.

Thirty-two built-in cases per target cover all four Default/Vanilla combinations of Models and Artwork, both ordinary and trapped-chest appearances, and both barrel appearances in each physical state. Forty-eight further cases cover disabled styling with retained default or missing IDs, missing Models, missing Artwork, both missing, and recovery. Requested/effective/source facts remain distinct. Enabled fallbacks match their corresponding built-in geometry, materials, and texture pixels; the two disabled variants match each other. Disabled styling is not equated with enabled inherited profiles using Fresh Auto.

All cases draw and retire without saving. Original AO requests remain visible alongside the deliberate reference-lighting policy; the generic renderer still refuses unsupported world AO. Fingerprints compare copied render inputs within the same client run. They do not prove animation, shader, world-lighting, or final production-layout parity. Final production acceptance on all four targets remains open.

### Preparation and diagnostic limits

Resolution tests distinguish selected, missing, refused, and disabled profiles independently for Models and Artwork. Complete compiler diagnostics are grouped within the shared fact and text limits; oversized input refuses before adding partial facts. The maximum-length diagnostic negative and repair remain separately recorded.

CPU preparation checks run on all four targets with declared budgets. A 1,024-cuboid model retains all 6,144 faces through native baking and still preparation. Cancellation returns no partial result, releases tracked image ownership, and permits retry. A geometry allowance one byte below the complete mesh refuses explicitly. A 4,096×4,096 texture passes resource capture and native baking, then refuses a 32 MiB still-output allowance without retaining its temporary reservation. Resource acceptance does not imply admission under every preview budget. Five observations after a separate warmup describe those workloads on the test host, not portable latency, rendered frame time, or actual heap/GPU allocation peaks.

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
