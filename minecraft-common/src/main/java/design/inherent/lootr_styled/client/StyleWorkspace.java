package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.minecraft.client.ClientReferenceTask;
import design.inherent.reform.minecraft.client.ClientTaskGraphic;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.minecraft.client.ClientWorkspacePresentation;
import design.inherent.reform.minecraft.client.HomePage;
import design.inherent.reform.minecraft.client.WorkspaceIdentity;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Presentation identities do not change settings, source identities, or document ownership. */
final class StyleWorkspace {
  static final DocumentId SETTINGS = DocumentId.parse("lootr_styled:settings_group");
  static final DocumentId PROFILES = DocumentId.parse("lootr_styled:profiles_page");
  // Matches the native-qualified domain envelope; oversized previews refuse explicitly.
  static final GraphicLimits PREVIEW_LIMITS =
      new GraphicLimits(
          16L * 1024 * 1024, 32L * 1024 * 1024, 16L * 1024 * 1024, 256, 128L * 1024 * 1024);

  private StyleWorkspace() {}

  static ClientWorkspacePresentation presentation(DocumentId route) {
    return ClientWorkspacePresentation.standard(
            new WorkspaceIdentity(
                DocumentId.parse("lootr_styled:workspace"),
                Component.literal("Lootr: Styled"),
                Component.literal("Container appearance")))
        .withHome(
            new HomePage(
                DestinationId.parse("lootr_styled:home"),
                Component.literal("Home"),
                Component.literal("Change how Lootr containers look."),
                List.of(new DestinationId(SETTINGS), new DestinationId(PROFILES))))
        .withReferenceTask(new DestinationId(PROFILES), profileTask(route));
  }

  private static ClientReferenceTask<ClientSettings, String> profileTask(DocumentId route) {
    var root = FieldPath.ROOT;
    return new ClientReferenceTask<ClientSettings, String>(
            route,
            StyleSettingsStore.DEFINITION,
            StyleSettingsStore.PROFILES,
            StylePreferences.REFERENCES,
            List.of(
                new ClientReferenceTask.Selection(
                    root.field("models"),
                    Component.literal("Models"),
                    Component.literal("Use for Models")),
                new ClientReferenceTask.Selection(
                    root.field("artwork"),
                    Component.literal("Artwork"),
                    Component.literal("Use for Artwork"))),
            (scope, editor) -> {
              scope.requireCurrent();
              return StylePreferences.profileReferences();
            })
        .withHelp(Component.literal("Preview uses a static pose with reference lighting."))
        .withGraphic(
            new ClientTaskGraphic<>(
                Component.literal("Draft preview"),
                PREVIEW_LIMITS,
                1,
                StyleGraphicSubject.TYPE,
                previewSubjects(),
                (scope, editor) -> {
                  scope.requireCurrent();
                  return new StyleGraphicProvider(PREVIEW_LIMITS);
                }));
  }

  private static List<ClientTaskGraphic.Choice<StyleGraphicSubject>> previewSubjects() {
    var choices = new ArrayList<ClientTaskGraphic.Choice<StyleGraphicSubject>>();
    for (var container :
        List.of(
            StyleGraphicSubject.Container.BARREL,
            StyleGraphicSubject.Container.CHEST,
            StyleGraphicSubject.Container.TRAPPED_CHEST)) {
      String id = container.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
      String label =
          switch (container) {
            case BARREL -> "Barrel";
            case CHEST -> "Chest";
            case TRAPPED_CHEST -> "Trapped chest";
          };
      for (boolean looted : List.of(false, true)) {
        for (boolean open : List.of(false, true)) {
          String appearance = looted ? "looted" : "unlooted";
          String pose = open ? "open" : "closed";
          choices.add(
              new ClientTaskGraphic.Choice<>(
                  id + "/" + appearance + "/" + pose,
                  Component.literal(label + ", " + appearance + ", " + pose),
                  new StyleGraphicSubject(
                      container,
                      looted,
                      container == StyleGraphicSubject.Container.BARREL && open,
                      container == StyleGraphicSubject.Container.BARREL ? 0 : open ? 1 : 0)));
        }
      }
    }
    return List.copyOf(choices);
  }

  static ClientWorkflowLayout.Category settings() {
    return new ClientWorkflowLayout.Category(
        SETTINGS,
        Component.literal("Settings"),
        Component.literal("Choose models, artwork, and Fresh Objects adaptation."));
  }

  static ClientWorkflowLayout.Category profiles() {
    return new ClientWorkflowLayout.Category(
        PROFILES,
        Component.literal("Profiles"),
        Component.literal("Choose models and artwork, preview the draft, or export resources."));
  }
}
