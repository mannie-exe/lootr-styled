package design.inherent.lootr_styled.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.minecraft.client.ClientWorkspacePresentation;
import design.inherent.reform.minecraft.client.HomePage;
import design.inherent.reform.minecraft.client.WorkspaceIdentity;
import design.inherent.reform.presentation.DestinationId;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Presentation identities do not change settings, source identities, or document ownership. */
final class StyleWorkspace {
  static final DocumentId SETTINGS = DocumentId.parse("lootr_styled:settings_group");
  static final DocumentId PROFILES = DocumentId.parse("lootr_styled:profiles_page");

  private StyleWorkspace() {}

  static ClientWorkspacePresentation presentation() {
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
                List.of(new DestinationId(SETTINGS), new DestinationId(PROFILES))));
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
            "Export resources, open the profiles folder, or inspect the last reload."));
  }
}
