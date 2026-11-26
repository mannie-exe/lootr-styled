# Interface writing

Use short, familiar labels that describe the action: Save, Defaults, Reload resources, Open profiles folder, and Export artwork. Defaults loads the declared appearance defaults; Revert restores the editor's observed starting values. Explain an action's effect in its tooltip when the label needs context. Use sentence case and readable state names such as Auto, On, and Off. Save and reload resources names both effects; Save alone must not imply live application.

Readable value captions belong to presentation. Keep the saved Fresh Objects values `AUTO`, `ON`, and `OFF` unchanged, and use the same caption declaration in editing and review. Unknown or invalid input must remain visible and repairable; a friendly caption must not replace the underlying draft value. Request a shared presentation API when needed rather than changing enum names, codec choices, or field order to alter a label.

Show a profile's optional `title` in selectors. Keep its ID unchanged in settings and available in tooltips; use the ID as the label only when no title is available. Built-in titles are Default and Vanilla (Inherited). Custom profiles follow the same rule.

Use re:form's shared layout declarations for the Appearance page. Styling groups the enable switch and Fresh Objects adaptation; Profile selection groups Models and Artwork. Those selectors may share a row when their measured content fits and stack in order when it does not. Their values and validation remain independent. Keep field help to its purpose. Put fallback rules, source precedence, application details, and adapter diagnostics in contextual information.

Navigation should follow the rendered groups and their responsive stacking order. re:form owns chooser structure, manual-ID containment, and action presentation; Lootr: Styled supplies grouping, profile metadata, wording, and effects. Record a missing shared API rather than adding a private renderer or changing serialization order. [Verification](verification.md#development-settings-interface) records tested navigation behavior and remaining layout limits.

The shared chooser separates search and choices from inspected-value details when both panes fit. Narrow layouts keep the complete task in one pane. Reflow must retain the query, inspected ID, caret, and focus. Inspecting a profile does not change Models or Artwork; Use selection stages the chosen field. Manual ID editing stays inside a labelled group, and collapsing it retains the draft and returns focus to the live disclosure control.

In development, Appearance uses inline Models and Artwork lists with one shared complete-draft preview. Accepting a row changes only that field in the retained draft. Manual ID editing and source-bound completion use the original field buffer; unknown input remains editable. Search and keyboard focus do not stage a profile.

Profiles browses the same catalogue with an explicit Models or Artwork audition role. Inspecting a resolved profile tries it in a read-only candidate and leaves both draft selections unchanged. Use for Models and Use for Artwork remain explicit draft edits. Keep the inspected profile, audition candidate, draft selections, and effective resources distinct. The preview names its basis as Current draft or Trying a profile for the selected role.

Container, Looted, and Open are separate preview controls. Their accepted changes use re:form's bounded automatic lane; camera changes redraw without preparing another scene. Keep native pose limits visible, including custom rigs without a movable native lid. The viewport is keyboard reachable, and there is no separate Show preview action. Hiding the body retires its graphic interest while retaining local subject and camera state; returning alone does not prepare. Explicitly discarding the settings owner retires its task views and binds a fresh owner on reopening.

Document actions stays in the workspace and uses the same document, draft, and history. Defaults changes the retained draft; Undo and Redo move through complete document edits, including fields hidden by the current view. These actions leave the file unchanged until an explicit save. Reload saved reads the source; when the draft has unsaved edits, retain them for conflict review rather than replacing them silently. Resolve draft changes the draft, and saving remains separate. Returning from that view retains the actual editor. Save and reload resources and profile exports remain separate guarded operations.

Review and operation results stay in the settings window. Navigation history restores an earlier view; a task’s return action names its parent, such as Back to settings. Neither should start another effect.

Closing with unsaved changes keeps the decision in the workspace. Review changes opens the guarded review chooser; canceling it retains the original view, and inspection uses the same unsaved draft. Keep editing returns to the draft; Discard draft ends the editing session and retains the saved settings. Returning after the graphic retires requires Prepare preview or another admitted preview gesture; navigation alone does not restart preparation.

Name immediate operation buttons for their effects. Inspect result opens the retained result without starting that effect again. Use the same field captions in editing and review; keep backend terms, raw identities, and revision details behind Details unless they help explain an actionable state.

Report routine outcomes briefly, such as Saved or Reloaded. Keep errors specific and provide a recovery action. Put paths, resource decisions, and diagnostic detail behind Details rather than opening a report after every successful action. Distinguish a draft reset, a saved setting, a resource reload, and an exported editing copy.

Lead feedback with the outcome and what it affected. “Settings saved; resource reload failed” preserves useful information that “Something went wrong” discards. Saving, requesting a reload, applying resources, and verifying the result are separate facts. A cancellation request does not establish that work stopped or that a save was undone. If a write is uncertain, guide the user to inspect the saved file before retrying.

Use the same typed results for controls, Details, command output, and logs. Keep diagnostic codes stable when editing human wording. Describe the useful cause, expected and observed state, known effects, and a safe next action. Use readable labels alongside exact identities. Keep paths and copyable instructions available when they help recovery, with the original context preserved.

Keep problems available near the affected control after transient notifications disappear. Preserve the draft, selected profile ID, and keyboard focus while showing a failure. Missing, invalid, unavailable, and stale inputs need distinct descriptions. Inspection grants no write authority, and an unavailable input must not be described as verified or silently replaced with an enabled default.

Retain the actual result independently of progress notifications or an open result view. A late result must not replace newer edits. Keep resource and operation identities available for inspection.

While a resource reload is pending, disable Reload resources, Retry reload, and Save and reload resources using the same application state. Do not describe an earlier successful reload as the current pending result.

Keep save and reload outcomes, causes, and recovery instructions visible. Mark raw status codes, saved revisions, and resource-generation IDs as technical details; disclosure changes presentation without changing the retained result or starting another effect.

Routine reload results keep the resource trace under technical details. An explicit Inspect resources request presents that trace directly. Export destinations and editing instructions remain ordinary result details. Declare these roles where the result is constructed, without classifying strings by their wording.

Say each thing once. Remove filler, marketing claims, dramatic fragments, repeated caveats, and implementation-first explanations. Prefer complete, direct sentences in prose. Use ordinary punctuation rather than em-dash asides. Keep essential limits and source information available without making them the normal path through the interface.

This applies the [inherent.design writing reference](https://github.com/inherent-design/inherent.design/blob/main/packages/docs/knowledge/process-notes/prose-and-communication-reference.md) to a Minecraft settings screen.

The outcome, inspection, and recovery rules also follow empack's [public outcomes and diagnostics](https://github.com/inherent-design/empack/blob/main/docs/design/api.md#outcomes-and-diagnostics), [workspace inspection](https://github.com/inherent-design/empack/blob/main/docs/design/status.md), and [retained operation results](https://github.com/inherent-design/empack/blob/main/docs/design/runtime.md#status-is-not-a-queue-of-indispensable-events).

[Compat’s configuration binding contract](https://github.com/mannie-exe/skyweave-compat/blob/main/docs/development/configuration.md) also informs these rules: one owner across views, explicit application timing, complete control domains, distinct unavailable and empty sources, and native consumer checks. Its server and terrain policies do not apply to this client addon.
