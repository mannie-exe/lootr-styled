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
    var presentation = StyleWorkspace.presentation(StyleSettingsStore.DEFINITION.id());
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
    var task = presentation.referenceTasks().get(new DestinationId(StyleWorkspace.PROFILES));
    assertNotNull(task);
    assertSame(StyleSettingsStore.DEFINITION, task.definition());
    assertEquals(form.route(), task.route());
    assertEquals(StyleSettingsStore.PROFILES, task.source());
    assertEquals(
        List.of(FieldPath.ROOT.field("models"), FieldPath.ROOT.field("artwork")),
        task.selections().stream().map(selection -> selection.field()).toList());
    assertEquals(
        List.of("Use for Models", "Use for Artwork"),
        task.selections().stream().map(selection -> selection.label().getString()).toList());
    assertEquals(1, task.graphic().orElseThrow().maximumViews());
    assertEquals(StyleWorkspace.PREVIEW_LIMITS, task.graphic().orElseThrow().aggregateLimits());
    var missingProfiles = new ClientWorkflowLayout(List.of(layout.groups().getFirst()), List.of());
    assertFalse(presentation.validate(List.of(form), missingProfiles).valid());
  }

  @Test
  void previewSubjectsKeepAppearanceIndependentOfPhysicalPose() {
    var task =
        StyleWorkspace.presentation(StyleSettingsStore.DEFINITION.id())
            .referenceTasks()
            .get(new DestinationId(StyleWorkspace.PROFILES));
    var graphic = task.graphic().orElseThrow();
    assertSame(StyleGraphicSubject.TYPE, graphic.subjects());
    var subjects =
        graphic.choices().stream()
            .map(choice -> StyleGraphicSubject.class.cast(choice.subject()))
            .toList();
    assertEquals(12, subjects.size());
    for (var container : StyleGraphicSubject.Container.values()) {
      var states = subjects.stream().filter(subject -> subject.container() == container).toList();
      assertEquals(4, states.size());
      for (boolean looted : List.of(false, true)) {
        var poses = states.stream().filter(subject -> subject.looted() == looted).toList();
        assertEquals(2, poses.size());
        if (container == StyleGraphicSubject.Container.BARREL) {
          assertTrue(poses.stream().anyMatch(StyleGraphicSubject::open));
          assertTrue(poses.stream().anyMatch(subject -> !subject.open()));
        } else {
          assertTrue(poses.stream().anyMatch(subject -> subject.lid() == 0));
          assertTrue(poses.stream().anyMatch(subject -> subject.lid() == 1));
        }
      }
    }
  }
}
