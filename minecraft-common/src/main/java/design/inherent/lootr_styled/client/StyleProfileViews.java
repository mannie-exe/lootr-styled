package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.minecraft.client.ClientSectionParts;
import design.inherent.reform.minecraft.client.PreviewController;
import design.inherent.reform.minecraft.client.ui.CompletionParts;
import design.inherent.reform.minecraft.client.ui.ReferencePickerView;
import design.inherent.reform.minecraft.client.ui.SubjectForms;
import design.inherent.reform.minecraft.client.ui.UiText;
import design.inherent.reform.minecraft.client.ui.ViewSpec;
import design.inherent.reform.minecraft.client.ui.Views;
import design.inherent.reform.presentation.UiLayout;
import design.inherent.reform.presentation.ViewStateKey;
import design.inherent.reform.presentation.ui.*;
import design.inherent.reform.value.ValueTypes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Domain declarations over shared pickers, canonical fields, and the existing preview lane. */
final class StyleProfileViews {
  static final PickerRole<ClientSettings> MODELS =
      new PickerRole<>("models", FieldPath.ROOT.field("models"));
  static final PickerRole<ClientSettings> ARTWORK =
      new PickerRole<>("artwork", FieldPath.ROOT.field("artwork"));

  private static final StyleGraphicSubject INITIAL_SUBJECT =
      new StyleGraphicSubject(StyleGraphicSubject.Container.BARREL, false, false, 0);

  private StyleProfileViews() {}

  static ViewSpec appearance(UiScope scope, ClientSectionParts<ClientSettings> parts) {
    var models = inlinePicker(scope, parts, MODELS);
    var artwork = inlinePicker(scope, parts, ARTWORK);
    var preview =
        preview(
            scope, parts.editor().orElseThrow(), parts.graphics(StyleWorkspace.PREVIEW_LIMITS, 1));
    preview.activateAfterSelection(List.of(models, artwork));
    return Views.column(
        "appearance-profiles",
        List.of(
            Views.row(
                "selectors",
                new UiLayout.Row(12, 12),
                List.of(
                    frame("models", inline(scope, parts, models, MODELS, "Models")),
                    frame("artwork", inline(scope, parts, artwork, ARTWORK, "Artwork")))),
            preview.content()));
  }

  static ViewSpec profiles(
      UiScope scope, ClientEditor<ClientSettings> editor, ClientGraphicHost host) {
    var picker =
        ReferencePicker.open(
            scope,
            editor.actions(),
            StyleSettingsStore.PROFILES,
            StylePreferences::profileReferences,
            List.of(MODELS, ARTWORK),
            Map.of(),
            ReferencePicker.Policy.STANDARD,
            System::nanoTime);
    var preview = preview(scope, editor, host);
    preview.follow(picker, MODELS, Component.literal("Models"));
    var browser =
        ReferencePickerView.build(
            scope,
            picker,
            new ReferencePickerView.Presentation<>(
                text("Profiles"),
                text(
                    "Try container shapes or textures without changing settings. Use a profile to add it to the draft."),
                text("Available profiles"),
                text("Search profiles"),
                List.of(
                    new ReferencePickerView.RoleAction<>(MODELS, text("Use for Models")),
                    new ReferencePickerView.RoleAction<>(ARTWORK, text("Use for Artwork"))),
                4,
                2),
            Optional.empty());
    var roleActions =
        Views.actions(
            "audition-role",
            List.of(
                Views.action(
                    "models",
                    text("Try for Models"),
                    text("Preview the inspected profile's shapes without editing the draft."),
                    command(
                        preview,
                        () -> preview.follow(picker, MODELS, Component.literal("Models")))),
                Views.action(
                    "artwork",
                    text("Try for Artwork"),
                    text("Preview the inspected profile's textures without editing the draft."),
                    command(
                        preview,
                        () -> preview.follow(picker, ARTWORK, Component.literal("Artwork")))),
                Views.action(
                    "draft",
                    text("Preview current draft"),
                    text("Return to the complete draft's Models and Artwork selections."),
                    command(preview, preview::draft))));
    return Views.section(
        "profiles",
        text("Profiles"),
        List.of(
            roleActions,
            Views.row(
                "browser-preview",
                new UiLayout.Row(12, 12),
                List.of(frame("browser", browser), frame("preview", preview.content())))));
  }

  private static ReferencePicker<ClientSettings, String> inlinePicker(
      UiScope scope, ClientSectionParts<ClientSettings> parts, PickerRole<ClientSettings> role) {
    var field = parts.field(role.field(), StyleSettingsStore.PROFILE);
    return ReferencePicker.open(
        scope,
        field.binding(),
        StyleSettingsStore.DEFINITION,
        StyleSettingsStore.PROFILES,
        StylePreferences::profileReferences,
        List.of(role),
        Map.of(role.id(), field.frontend()),
        ReferencePicker.Policy.STANDARD,
        System::nanoTime);
  }

  private static ViewSpec inline(
      UiScope scope,
      ClientSectionParts<ClientSettings> parts,
      ReferencePicker<ClientSettings, String> picker,
      PickerRole<ClientSettings> role,
      String caption) {
    var field = parts.field(role.field(), StyleSettingsStore.PROFILE);
    var expanded =
        scope.local(
            new ViewStateKey<>(
                DocumentId.parse("lootr_styled:manual_" + role.id()), ValueTypes.bool()),
            false);
    var browser =
        ReferencePickerView.build(
            scope,
            picker,
            new ReferencePickerView.Presentation<>(
                text("Choose a profile"),
                text("Choosing a row changes " + caption + " in this draft."),
                text("Available profiles"),
                text("Search profiles"),
                List.of(new ReferencePickerView.RoleAction<>(role, text("Use for " + caption))),
                4,
                2),
            Optional.of(role));
    return Views.column(
        role.id(),
        List.of(
            field.label("label"),
            field.description("description"),
            field.controlSurface(
                "control",
                browser,
                UiBindings.local(expanded),
                text("Edit " + caption + " ID"),
                new CompletionParts(
                    picker.suggestions(ReferenceSuggestions.Policy.STANDARD),
                    text("Profile ID suggestions"),
                    4)),
            field.feedback("feedback")));
  }

  static List<SubjectForms.Control<StyleGraphicSubject>> subjectControls() {
    return List.of(
        new SubjectForms.Select<>(
            "container",
            text("Container"),
            text(""),
            ValueTypes.enumeration(StyleGraphicSubject.Container.class),
            new UiLens<>(StyleGraphicSubject::container, StyleGraphicSubject::withContainer),
            List.of(
                new SubjectForms.Choice<>(
                    "barrel", text("Barrel"), StyleGraphicSubject.Container.BARREL),
                new SubjectForms.Choice<>(
                    "chest", text("Chest"), StyleGraphicSubject.Container.CHEST),
                new SubjectForms.Choice<>(
                    "trapped", text("Trapped chest"), StyleGraphicSubject.Container.TRAPPED_CHEST)),
            3),
        new SubjectForms.Toggle<>(
            "looted",
            text("Looted"),
            text(""),
            new UiLens<>(StyleGraphicSubject::looted, StyleGraphicSubject::withLooted)),
        new SubjectForms.Toggle<>(
            "open",
            text("Open"),
            text("Static pose; custom rigs may not contain a movable native lid."),
            new UiLens<>(StyleGraphicSubject::opened, StyleGraphicSubject::withOpened)));
  }

  private static PreviewController<ClientSettings, StyleGraphicSubject> preview(
      UiScope scope, ClientEditor<ClientSettings> editor, ClientGraphicHost host) {
    var subjects =
        SubjectForms.of(
            scope,
            DocumentId.parse("lootr_styled:preview_subject"),
            StyleGraphicSubject.TYPE,
            INITIAL_SUBJECT,
            subjectControls());
    return PreviewController.open(
        scope,
        editor,
        host,
        new StyleGraphicProvider(StyleWorkspace.PREVIEW_LIMITS),
        subjects,
        Component.literal("Appearance preview"),
        PreviewController.Automatic.ACCEPTED_CHANGES);
  }

  private static UiCommand<Void> command(
      PreviewController<ClientSettings, StyleGraphicSubject> preview,
      Supplier<UiActionResult> action) {
    return new UiCommand<>() {
      public List<UiRead<?>> dependencies() {
        return List.of(preview.state());
      }

      public UiAvailability availability() {
        return UiAvailability.READY;
      }

      public UiActionResult invoke(Void ignored) {
        return action.get();
      }
    };
  }

  private static ViewSpec frame(String key, ViewSpec content) {
    return Views.frame(
        key,
        new UiLayout.Frame(UiLayout.Offer.flexible(220, 340), 4, UiLayout.Alignment.START),
        content);
  }

  private static UiText text(String value) {
    return UiText.of(Component.literal(value));
  }
}
