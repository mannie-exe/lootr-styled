package design.inherent.lootr_styled.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientCustomView;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.minecraft.client.ClientWorkspacePresentation;
import design.inherent.reform.minecraft.client.HomePage;
import design.inherent.reform.minecraft.client.WorkspaceIdentity;
import design.inherent.reform.presentation.DestinationId;
import design.inherent.reform.presentation.ViewKey;
import design.inherent.reform.presentation.ViewStateKey;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.ui.UiLimits;
import design.inherent.reform.value.ValueTypes;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Presentation identities do not change settings, source identities, or document ownership. */
final class StyleWorkspace {
  static final DocumentId SETTINGS = DocumentId.parse("lootr_styled:settings_group");
  static final DocumentId PROFILES = DocumentId.parse("lootr_styled:profiles_page");
  // Match the existing native-qualified domain envelope; oversized previews refuse explicitly.
  static final GraphicLimits PREVIEW_LIMITS =
      new GraphicLimits(
          16L * 1024 * 1024, 32L * 1024 * 1024, 16L * 1024 * 1024, 256, 128L * 1024 * 1024);

  private StyleWorkspace() {}

  static ClientWorkspacePresentation presentation(
      DocumentId route, Supplier<ClientGraphicHost> graphics) {
    var profiles =
        ClientCustomView.documentComponent(
            new ViewKey<>(
                new DestinationId(PROFILES),
                new ViewStateKey<>(
                    DocumentId.parse("lootr_styled:profiles_view"), ValueTypes.bool())),
            false,
            UiLimits.STANDARD,
            route,
            StyleSettingsStore.DEFINITION,
            (scope, editor, state) -> StyleProfileViews.profiles(scope, editor, graphics.get()));
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
        .withView(profiles);
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
        Component.literal(
            "Browse profiles, try their shapes or textures, and choose what to use."));
  }
}
