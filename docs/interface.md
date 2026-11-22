# Interface writing

Use short, familiar labels that describe the action: Save, Reset, Reload resources, Folder, and Export artwork. Explain an action's effect in its tooltip when the label needs context. Use sentence case and readable state names such as Auto, On, and Off. Save and reload resources names both effects; Save alone must not imply live application.

Show a profile's optional `title` in selectors. Keep its ID unchanged in settings and available in tooltips; use the ID as the label only when no title is available. Built-in titles are Default and Vanilla (Inherited). Custom profiles follow the same rule.

Report routine outcomes briefly, such as Saved or Reloaded. Keep errors specific and provide a recovery action. Put paths, resource decisions, and diagnostic detail behind Details rather than opening a report after every successful action. Distinguish a draft reset, a saved setting, a resource reload, and an exported editing copy.

Lead feedback with the outcome and what it affected. “Settings saved; resource reload failed” preserves useful information that “Something went wrong” discards. Saving, requesting a reload, applying resources, and verifying the result are separate facts. A cancellation request does not establish that work stopped or that a save was undone. If a write is uncertain, guide the user to inspect the saved file before retrying.

Use the same typed results for controls, Details, command output, and logs. Keep diagnostic codes stable; human wording is not the status contract. Describe the useful cause, expected and observed state, known effects, and a safe next action. Use readable labels alongside exact identities. Keep paths and copyable instructions available when they help recovery, with the original context preserved.

Keep problems available near the affected control after transient notifications disappear. Preserve the draft, selected profile ID, and keyboard focus while showing a failure. Missing, invalid, unavailable, and stale inputs need distinct descriptions. Inspection grants no write authority, and an unavailable input must not be described as verified or silently replaced with an enabled default.

Progress is a hint; retain the actual operation result independently of notifications or an open result view. A late result must not replace newer edits. Keep resource and operation identities available for inspection while routine feedback stays concise.

Say each thing once. Remove filler, marketing claims, dramatic fragments, repeated caveats, and implementation-first explanations. Prefer complete, direct sentences in prose. Use ordinary punctuation rather than em-dash asides. Keep essential limits and source information available without making them the normal path through the interface.

This applies the [inherent.design writing reference](https://github.com/inherent-design/inherent.design/blob/main/packages/docs/knowledge/process-notes/prose-and-communication-reference.md) to a Minecraft settings screen.

The outcome, inspection, and recovery rules also follow empack's [public outcomes and diagnostics](https://github.com/inherent-design/empack/blob/main/docs/design/api.md#outcomes-and-diagnostics), [workspace inspection](https://github.com/inherent-design/empack/blob/main/docs/design/status.md), and [retained operation results](https://github.com/inherent-design/empack/blob/main/docs/design/runtime.md#status-is-not-a-queue-of-indispensable-events). Its [browser-authoring proposal](https://github.com/inherent-design/empack/blob/main/docs/candidates/v0.9.0-beta-browser-authoring.md) reinforces draft preservation and revision-bound asynchronous results; it is design evidence rather than a claim of shipped browser behavior.

[Compat’s configuration binding contract](https://github.com/mannie-exe/skyweave-compat/blob/main/docs/development/configuration.md) also informs these rules: one owner across views, explicit application timing, complete control domains, distinct unavailable and empty sources, and native consumer checks. Its server and terrain policies do not apply to this client addon.
