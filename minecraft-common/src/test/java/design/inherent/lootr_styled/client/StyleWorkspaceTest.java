package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.minecraft.client.ClientForm;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.minecraft.client.HeaderComposition;
import design.inherent.reform.presentation.DestinationId;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class StyleWorkspaceTest {
  @Test
  void homeTargetsStableCategoriesWithoutOpeningASettingsOwner() {
    var presentation =
        StyleWorkspace.presentation(
            StyleSettingsStore.DEFINITION.id(),
            () -> {
              throw new AssertionError("Metadata admission opened graphics");
            });
    var form =
        ClientForm.of(
            StyleSettingsStore.DEFINITION.id(),
            Component.literal("Appearance"),
            () -> {
              throw new AssertionError("Metadata admission read availability");
            },
            ignored -> {
              throw new AssertionError("Metadata admission opened the editor");
            },
            Optional.empty());
    var placement =
        new ClientWorkflowLayout.Placement(
            DocumentId.parse("lootr_styled:appearance"),
            form.route(),
            Component.literal("Appearance"),
            Optional.empty());
    var layout =
        new ClientWorkflowLayout(
            List.of(
                new ClientWorkflowLayout.Group(
                    List.of(StyleWorkspace.settings()), List.of(placement), List.of()),
                new ClientWorkflowLayout.Group(
                    List.of(StyleWorkspace.profiles()), List.of(), List.of())),
            List.of());
    assertTrue(presentation.validate(List.of(form), layout).valid());
    assertEquals(
        List.of(
            new DestinationId(StyleWorkspace.SETTINGS), new DestinationId(StyleWorkspace.PROFILES)),
        presentation.home().orElseThrow().featured());
    assertEquals(DestinationId.parse("lootr_styled:home"), presentation.home().orElseThrow().id());
    assertEquals("Lootr: Styled", presentation.identity().title().getString());
    assertEquals("Container appearance", presentation.identity().subtitle().getString());
    assertTrue(presentation.initial().isEmpty());
    assertEquals(
        List.of(HeaderComposition.Slot.IDENTITY, HeaderComposition.Slot.LOCATION),
        presentation.header().rows().getFirst().slots());
    assertTrue(presentation.header().canvas().isEmpty());
    var view = presentation.views().get(new DestinationId(StyleWorkspace.PROFILES));
    assertNotNull(view);
    assertEquals(new DestinationId(StyleWorkspace.PROFILES), view.key().id());
    assertTrue(presentation.referenceTasks().isEmpty());
    assertEquals(FieldPath.ROOT.field("models"), StyleProfileViews.MODELS.field());
    assertEquals(FieldPath.ROOT.field("artwork"), StyleProfileViews.ARTWORK.field());
    var missingProfiles = new ClientWorkflowLayout(List.of(layout.groups().getFirst()), List.of());
    assertFalse(presentation.validate(List.of(form), missingProfiles).valid());
  }

  @Test
  @SuppressWarnings("unchecked")
  void previewSubjectControlsKeepAppearanceIndependentOfPhysicalPose() {
    var controls = StyleProfileViews.subjectControls();
    assertEquals(
        List.of("container", "looted", "open"),
        controls.stream().map(control -> control.key()).toList());
    var container =
        (design.inherent.reform.minecraft.client.ui.SubjectForms.Select<
                StyleGraphicSubject, StyleGraphicSubject.Container>)
            controls.get(0);
    var looted =
        (design.inherent.reform.minecraft.client.ui.SubjectForms.Toggle<StyleGraphicSubject>)
            controls.get(1);
    var open =
        (design.inherent.reform.minecraft.client.ui.SubjectForms.Toggle<StyleGraphicSubject>)
            controls.get(2);
    assertEquals(3, container.visibleRows());
    assertEquals(
        java.util.Set.of(StyleGraphicSubject.Container.values()),
        container.choices().stream()
            .map(choice -> choice.value())
            .collect(java.util.stream.Collectors.toSet()));
    var initial = new StyleGraphicSubject(StyleGraphicSubject.Container.BARREL, false, false, 0);
    var combinations = new java.util.HashSet<StyleGraphicSubject>();
    for (var choice : container.choices())
      for (boolean used : List.of(false, true))
        for (boolean opened : List.of(false, true)) {
          var value = container.lens().replace().apply(initial, choice.value());
          value = looted.lens().replace().apply(value, used);
          value = open.lens().replace().apply(value, opened);
          assertEquals(choice.value(), value.container());
          assertEquals(used, value.looted());
          assertEquals(opened, value.opened());
          combinations.add(value);
        }
    assertEquals(12, combinations.size());
    var partial = new StyleGraphicSubject(StyleGraphicSubject.Container.CHEST, false, false, 0.5f);
    assertEquals(0.5f, looted.lens().replace().apply(partial, true).lid());
  }
}
