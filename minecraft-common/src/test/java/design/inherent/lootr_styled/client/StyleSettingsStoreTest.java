package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.FieldKey;
import design.inherent.reform.edit.DraftNode;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.owner.FileOwner;
import design.inherent.reform.owner.OwnerContext;
import design.inherent.reform.presentation.ChoicePresentation;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.TextStore;
import design.inherent.reform.value.ValueShape;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StyleSettingsStoreTest {
  @TempDir Path config;

  private Path source(String text) throws Exception {
    Path file = config.resolve("lootr_styled/settings.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
    return file;
  }

  @Test
  void freshCaptionsPreserveCanonicalFieldsChoicesAndBothJsonRepresentations() throws Exception {
    var shape = (ValueShape.ObjectValue) StyleSettingsStore.DEFINITION.type().shape();
    assertEquals(
        List.of("enabled", "models", "artwork", "freshObjects"),
        shape.fields().stream().map(FieldKey::id).toList());
    var fresh = shape.fields().get(3).type();
    var scalar = (ValueShape.Scalar) fresh.shape();
    assertEquals(List.of("AUTO", "ON", "OFF"), scalar.choices());
    assertEquals("lootr_styled:settings", StyleSettingsStore.DEFINITION.id().toString());
    var labels = List.of("Auto", "On", "Off");
    for (var mode : ClientSettings.Fresh.values()) {
      var value = new ClientSettings(true, ClientSettings.DEFAULT, ClientSettings.DEFAULT, mode);
      var json =
          StyleSettingsStore.DEFINITION
              .type()
              .codec()
              .encodeStart(JsonOps.INSTANCE, value)
              .getOrThrow()
              .getAsJsonObject();
      assertEquals(mode.name(), json.get("freshObjects").getAsString());
      assertEquals(
          List.of("enabled", "models", "artwork", "freshObjects"), List.copyOf(json.keySet()));
      var caption =
          ChoicePresentation.caption(fresh, new DraftNode.Scalar(mode.name())).orElseThrow();
      assertEquals(mode.name(), caption.value());
      assertEquals(labels.get(mode.ordinal()), caption.fallback());
      var text = value.toJson();
      var file = source(text);
      var store = new StyleSettingsStore(config);
      var observed = store.read();
      assertEquals(value, observed.candidate().orElseThrow().value());
      store.write(observed, observed.candidate().orElseThrow());
      assertEquals(text, Files.readString(file));
      assertEquals(value, ClientSettings.parse(Files.readAllBytes(file)));
    }
  }

  @Test
  void friendlyAndUnknownSpellingsRemainInvalidRawInputWithoutCaptionsOrRewrites()
      throws Exception {
    var shape = (ValueShape.ObjectValue) StyleSettingsStore.DEFINITION.type().shape();
    var fresh = shape.fields().get(3).type();
    for (var raw : List.of("Auto", "On", "Off", "auto", "unknown mode")) {
      assertTrue(ChoicePresentation.caption(fresh, new DraftNode.Scalar(raw)).isEmpty());
      assertTrue(fresh.codec().parse(JsonOps.INSTANCE, new JsonPrimitive(raw)).error().isPresent());
      var text = "{\"format\":1,\"freshObjects\":\"" + raw + "\"}";
      var file = source(text);
      var observed = new StyleSettingsStore(config).read();
      assertNotEquals(TextStore.State.READY, observed.state());
      assertTrue(observed.candidate().isEmpty());
      assertEquals(text, Files.readString(file));
    }
  }

  @Test
  void existingDefaultsDisabledAndMissingIdentitiesRetainTheirMeaning() throws Exception {
    Path file = source("{\"format\":1,\"enabled\":false,\"models\":\"missing:kept\"}");
    var observation = new StyleSettingsStore(config).read();
    assertEquals(TextStore.State.READY, observation.state());
    assertEquals(
        new ClientSettings(
            false,
            ResourceId.parse("missing:kept"),
            ClientSettings.DEFAULT,
            ClientSettings.Fresh.AUTO),
        observation.candidate().orElseThrow().value());
    assertEquals(
        "{\"format\":1,\"enabled\":false,\"models\":\"missing:kept\"}", Files.readString(file));
  }

  @Test
  void missingSourceReadDoesNotInitializeOrInventAnEffectiveValue() throws Exception {
    var observed = new StyleSettingsStore(config).read();
    assertEquals(TextStore.State.MISSING, observed.state());
    assertTrue(observed.candidate().isEmpty());
    assertFalse(Files.exists(config.resolve("lootr_styled/settings.json")));
  }

  @Test
  void strictExistingFormatRefusesUnknownInvalidAndOversizedDataWithoutRewriting()
      throws Exception {
    for (String text :
        new String[] {
          "invalid user draft",
          "{\"format\":2}",
          "{\"format\":1,\"unknown\":true}",
          "{\"format\":1,\"enabled\":null}",
          "{\"format\":1,\"models\":\"bad id\"}",
          "{\"format\":1,\"freshObjects\":\"bad\"}",
          "{\"format\":1,\"enabled\":false,\"enabled\":true}",
          " ".repeat(StyleSettingsStore.MAXIMUM_BYTES + 1)
        }) {
      Path file = source(text);
      try {
        var read = new StyleSettingsStore(config).read();
        assertNotEquals(TextStore.State.READY, read.state());
        assertTrue(read.candidate().isEmpty());
      } catch (java.io.IOException refused) {
        assertEquals(text, Files.readString(file));
      }
      assertEquals(text, Files.readString(file));
    }
  }

  @Test
  void noOpPreservesExactBytesAndEditedSaveKeepsFormatOne() throws Exception {
    Path file = source("{\"format\":1, \"enabled\":true}\n");
    var store = new StyleSettingsStore(config);
    var before = store.read();
    store.write(before, before.candidate().orElseThrow());
    assertEquals("{\"format\":1, \"enabled\":true}\n", Files.readString(file));
    var changed =
        new ClientSettings(
            false, ClientSettings.VANILLA, ClientSettings.DEFAULT, ClientSettings.Fresh.OFF);
    var draft = DraftTree.from(StyleSettingsStore.DEFINITION.type(), changed, store.draftLimits());
    var candidate = store.inspectDraft(draft).candidate().orElseThrow();
    var write = store.write(store.read(), candidate);
    assertEquals(OwnedFile.Persistence.SAVED, write.persistence().state());
    assertEquals(changed, ClientSettings.parse(Files.readAllBytes(file)));
    assertFalse(Files.readString(file).contains("schemaVersion"));
  }

  @Test
  void externalEditsRefuseAStaleWriteAndRetainInvalidDraft() throws Exception {
    Path file = source(ClientSettings.STARTER);
    var store = new StyleSettingsStore(config);
    var before = store.read();
    Files.writeString(file, "my invalid draft");
    var result = store.write(before, before.candidate().orElseThrow());
    assertNotEquals(OwnedFile.Persistence.SAVED, result.persistence().state());
    assertEquals("my invalid draft", Files.readString(file));
  }

  @Test
  void actualSharedOwnerInitializesOnlyAbsentFilesAndPreservesInvalidSources() throws Exception {
    var store = new StyleSettingsStore(config);
    try (var context =
        new OwnerContext(Runnable::run, Runnable::run, () -> {}, OwnerContext.Limits.STANDARD)) {
      var owner = new FileOwner<>(context, store, 16);
      var access = context.access(() -> true, () -> true);
      try (var session = owner.open(access)) {
        session.initialize().toCompletableFuture().join();
      }
      Path file = config.resolve("lootr_styled/settings.json");
      assertEquals(ClientSettings.DEFAULTS, ClientSettings.parse(Files.readAllBytes(file)));
      Files.writeString(file, "broken source");
      try (var session = owner.open(access)) {
        session.initialize().toCompletableFuture().join();
      }
      assertEquals("broken source", Files.readString(file));
    }
  }

  @Test
  void symlinkedRootCannotRedirectSettingsReadsOrInitialization() throws Exception {
    Path outside = Files.createTempDirectory(config, "outside-");
    Files.createSymbolicLink(config.resolve("lootr_styled"), outside);
    var store = new StyleSettingsStore(config);
    assertThrows(java.io.IOException.class, store::read);
    assertFalse(Files.exists(outside.resolve("settings.json")));
  }
}
