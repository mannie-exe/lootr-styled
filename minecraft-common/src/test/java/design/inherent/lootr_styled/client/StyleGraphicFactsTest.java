package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.presentation.graphics.GraphicSceneData;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StyleGraphicFactsTest {
  @Test
  void oversizedOrUncontainableDiagnosticsRefuseBeforePublishingAPartialFactSet() {
    var facts = new ArrayList<GraphicSceneData.Fact>();
    var first =
        new GraphicSceneData.Fact(DocumentId.parse("example:first"), "Existing", "Retained");
    facts.add(first);
    assertThrows(
        design.inherent.reform.owner.PolicyRefusal.class,
        () ->
            StyleGraphicProvider.resourceFacts(
                facts, java.util.Collections.nCopies(257, "Detail")));
    assertEquals(List.of(first), facts);
    assertThrows(
        design.inherent.reform.owner.PolicyRefusal.class,
        () -> StyleGraphicProvider.resourceFacts(facts, List.of("x".repeat(4097))));
    assertEquals(List.of(first), facts);
    assertThrows(
        design.inherent.reform.owner.PolicyRefusal.class,
        () ->
            StyleGraphicProvider.resourceFacts(
                facts, java.util.Collections.nCopies(64, "x".repeat(4096))));
    assertEquals(List.of(first), facts);
    StyleGraphicProvider.resourceFacts(facts, List.of());
    assertEquals(List.of(first), facts);
  }

  @Test
  void completeCompilerDiagnosticEnvelopeFitsAlongsideProfileAndPoseFacts() {
    var facts = new ArrayList<GraphicSceneData.Fact>();
    for (int i = 0; i < 18; i++)
      facts.add(
          new GraphicSceneData.Fact(DocumentId.parse("example:fact/" + i), "Existing", "Retained"));
    var before = List.copyOf(facts);
    var diagnostics =
        IntStream.range(0, 256).mapToObj(i -> String.format("%03d", i) + "x".repeat(509)).toList();
    assertDoesNotThrow(() -> StyleGraphicProvider.resourceFacts(facts, diagnostics));
    assertEquals(before, facts.subList(0, before.size()));
    assertTrue(facts.size() <= 64);
    assertEquals(
        diagnostics,
        facts.subList(before.size(), facts.size()).stream()
            .flatMap(f -> f.value().lines())
            .toList());
    assertTrue(facts.stream().allMatch(f -> f.value().length() <= 4096));
    assertEquals(facts.size(), facts.stream().map(GraphicSceneData.Fact::id).distinct().count());
  }
}
