# Interface writing

Use short, familiar labels that describe the action: Save, Defaults, Reload resources, Open profiles folder, and Export artwork. Defaults loads the declared appearance defaults; Revert restores the editor's observed starting values. Explain an action's effect in its tooltip when the label needs context. Use sentence case and readable state names such as Auto, On, and Off. Save and reload resources names both effects; Save alone must not imply live application.

Show a profile's optional `title` in selectors. Keep its ID unchanged in settings and available in tooltips; use the ID as the label only when no title is available. Built-in titles are Default and Vanilla (Inherited). Custom profiles follow the same rule.

Use re:form's shared layout declarations for the Appearance page. Styling groups the enable switch and Fresh Objects adaptation; Profile selection groups Models and Artwork. Those selectors may share a row when their measured content fits and stack in order when it does not. Their values and validation remain independent. Keep field help to its purpose. Put fallback rules, source precedence, application details, and adapter diagnostics in contextual information.

Navigation should follow the rendered groups and their responsive stacking order. re:form owns chooser structure, manual-ID containment, and action presentation; Lootr: Styled supplies grouping, profile metadata, wording, and effects. Record a missing shared API rather than adding a private renderer or changing serialization order. [Verification](verification.md#development-settings-interface) records tested navigation behavior and remaining layout limits.

The shared chooser separates search and choices from inspected-value details when both panes fit. Narrow layouts keep the complete task in one pane. Reflow must retain the query, inspected ID, caret, and focus. Inspecting a profile does not change Models or Artwork; Use selection stages the chosen field. Manual ID editing stays inside a labelled group, and collapsing it retains the draft and returns focus to the live disclosure control.

Profiles uses that same source in its own shared task. Use for Models and Use for Artwork stage independent fields in the retained settings draft. Keep the inspected profile, draft selections, and effective resources distinct. Prepare draft preview uses the complete draft and chosen subject; it leaves the file and installed resources unchanged. Switching subject or leaving the page retires the displayed interest without automatically preparing another preview.

Show preview reveals and focuses the existing viewport without preparing a new scene. Fit the viewport to its visible pane and narrate its identity and state. Keep preparation and camera actions separate from this navigation action. Explicitly discarding the settings owner must retire its task views; reopening must bind the new owner rather than reuse controls from the discarded draft.

Document actions opens a shared view of the same document, draft, and history. Defaults changes the retained draft; Undo restores the previous selections. Both leave the file unchanged until an explicit save. Returning from that view retains the actual editor. Save and reload resources and profile exports remain separate guarded operations.

Name immediate operation buttons for their effects. Inspect result opens the retained result without starting that effect again. Use the same field captions in editing and review; keep backend terms, raw identities, and revision details behind Details unless they help explain an actionable state.

Report routine outcomes briefly, such as Saved or Reloaded. Keep errors specific and provide a recovery action. Put paths, resource decisions, and diagnostic detail behind Details rather than opening a report after every successful action. Distinguish a draft reset, a saved setting, a resource reload, and an exported editing copy.

Lead feedback with the outcome and what it affected. “Settings saved; resource reload failed” preserves useful information that “Something went wrong” discards. Saving, requesting a reload, applying resources, and verifying the result are separate facts. A cancellation request does not establish that work stopped or that a save was undone. If a write is uncertain, guide the user to inspect the saved file before retrying.

Use the same typed results for controls, Details, command output, and logs. Keep diagnostic codes stable when editing human wording. Describe the useful cause, expected and observed state, known effects, and a safe next action. Use readable labels alongside exact identities. Keep paths and copyable instructions available when they help recovery, with the original context preserved.

Keep problems available near the affected control after transient notifications disappear. Preserve the draft, selected profile ID, and keyboard focus while showing a failure. Missing, invalid, unavailable, and stale inputs need distinct descriptions. Inspection grants no write authority, and an unavailable input must not be described as verified or silently replaced with an enabled default.

Retain the actual result independently of progress notifications or an open result view. A late result must not replace newer edits. Keep resource and operation identities available for inspection.

Say each thing once. Remove filler, marketing claims, dramatic fragments, repeated caveats, and implementation-first explanations. Prefer complete, direct sentences in prose. Use ordinary punctuation rather than em-dash asides. Keep essential limits and source information available without making them the normal path through the interface.

This applies the [inherent.design writing reference](https://github.com/inherent-design/inherent.design/blob/main/packages/docs/knowledge/process-notes/prose-and-communication-reference.md) to a Minecraft settings screen.

The outcome, inspection, and recovery rules also follow empack's [public outcomes and diagnostics](https://github.com/inherent-design/empack/blob/main/docs/design/api.md#outcomes-and-diagnostics), [workspace inspection](https://github.com/inherent-design/empack/blob/main/docs/design/status.md), and [retained operation results](https://github.com/inherent-design/empack/blob/main/docs/design/runtime.md#status-is-not-a-queue-of-indispensable-events).

[Compat’s configuration binding contract](https://github.com/mannie-exe/skyweave-compat/blob/main/docs/development/configuration.md) also informs these rules: one owner across views, explicit application timing, complete control domains, distinct unavailable and empty sources, and native consumer checks. Its server and terrain policies do not apply to this client addon.
