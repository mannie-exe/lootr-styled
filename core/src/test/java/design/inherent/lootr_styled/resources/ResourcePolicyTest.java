package design.inherent.lootr_styled.resources;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResourcePolicyTest {
  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static com.google.gson.JsonObject json(String text) {
    return JsonDocuments.object(bytes(text));
  }

  @Test
  void strictJsonRefusesAmbiguousOrUnboundedDocuments() {
    for (String bad :
        new String[] {
          "{\"x\":1,\"x\":2}",
          "{}{}",
          "{\"x\":NaN}",
          "{\"x\":1e99999}",
          "{x:1}",
          "{\"x\":/*comment*/1}",
          "{\"x\":\"\\ud800\"}",
          "{\"x\":" + "[".repeat(65) + "0" + "]".repeat(65) + "}"
        }) {
      assertThrows(IllegalArgumentException.class, () -> json(bad), bad);
    }
    assertThrows(
        IllegalArgumentException.class, () -> JsonDocuments.object(new byte[] {(byte) 0xff}));
    assertEquals("1", json("{\"x\":1}").get("x").toString());
  }

  @Test
  void settingsKeepRequestedIdentityAndRejectCoercion() {
    var parsed =
        ClientSettings.parse(
            bytes(ClientSettings.STARTER.replace("lootr_styled:default", "missing:requested")));
    assertEquals("missing:requested", parsed.models().toString());
    assertEquals(ClientSettings.Fresh.AUTO, parsed.fresh());
    for (String bad :
        new String[] {
          "{\"format\":1,\"enabled\":\"true\"}",
          "{\"format\":1,\"models\":1}",
          "{\"format\":1,\"freshObjects\":\"on\"}",
          "{\"format\":1,\"extra\":false}",
          "{\"format\":1.0}"
        }) {
      assertThrows(IllegalArgumentException.class, () -> ClientSettings.parse(bytes(bad)));
    }
  }

  @Test
  void inputCaptureReadsOnceAndReturnsDefensiveBytes() {
    var calls = new AtomicInteger();
    var source =
        new ResourceAccess() {
          @Override
          public Optional<Source> find(ResourceId id) {
            return Optional.of(
                new Source(
                    Origin.OTHER,
                    "fixture",
                    () -> {
                      calls.incrementAndGet();
                      return new ByteArrayInputStream(bytes("data"));
                    }));
          }

          @Override
          public Map<ResourceId, Source> list(String directory) {
            return Map.of();
          }
        };
    var inputs = new ResourceInputs(source);
    var id = ResourceId.parse("fixture:data.json");
    var captured = inputs.require(id);
    captured.bytes()[0] = 0;
    assertEquals("data", new String(inputs.require(id).bytes(), StandardCharsets.UTF_8));
    assertEquals(1, calls.get());
  }

  @Test
  void missingInputCountAndSingleFileBytesAreBounded() {
    var empty = new Memory();
    var inputs = new ResourceInputs(empty);
    for (int i = 0; i < ResourceInputs.MAX_FILES; i++)
      assertTrue(inputs.find(ResourceId.parse("fixture:missing_" + i)).isEmpty());
    assertThrows(
        IllegalArgumentException.class, () -> inputs.find(ResourceId.parse("fixture:overflow")));
    empty.entries.put(
        ResourceId.parse("fixture:large"), new byte[ResourceInputs.MAX_FILE_BYTES + 1]);
    assertThrows(
        IllegalArgumentException.class,
        () -> new ResourceInputs(empty).require(ResourceId.parse("fixture:large")));
  }

  @Test
  void parentTextureVariablesAreResolvedAfterChildBinding() {
    var source = new Memory();
    source.put(
        "minecraft:models/block/parent.json",
        "{\"textures\":{\"particle\":\"#side\"},\"elements\":[]}");
    source.put(
        "example:models/child.json",
        "{\"parent\":\"minecraft:block/parent\",\"textures\":{\"side\":\"example:block/side\"}}");
    var model =
        new BlockModels(new ResourceInputs(source))
            .resolve(ResourceId.parse("example:models/child.json"));
    assertEquals("#side", model.getAsJsonObject("textures").get("particle").getAsString());
    assertEquals("example:block/side", model.getAsJsonObject("textures").get("side").getAsString());
  }

  @Test
  void cyclicParentsAndTextureAliasesAreRefused() {
    var source = new Memory();
    source.put("example:models/a.json", "{\"parent\":\"example:b\"}");
    source.put("example:models/b.json", "{\"parent\":\"example:a\"}");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BlockModels(new ResourceInputs(source))
                .resolve(ResourceId.parse("example:models/a.json")));
    assertThrows(
        IllegalArgumentException.class,
        () -> BlockModels.validate(json("{\"textures\":{\"a\":\"#b\",\"b\":\"#a\"}}")));
  }

  @Test
  void executableLoadersAndNativeGeometryOverflowAreRefused() {
    var source = new Memory();
    source.put("example:models/a.json", "{\"loader\":\"other:executable\"}");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BlockModels(new ResourceInputs(source))
                .resolve(ResourceId.parse("example:models/a.json")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BlockModels.validate(json("{\"elements\":[{\"from\":[-17,0,0],\"to\":[16,16,16]}]}")));
  }

  private static final class Memory implements ResourceAccess {
    private final Map<ResourceId, byte[]> entries = new HashMap<>();

    void put(String id, String value) {
      entries.put(ResourceId.parse(id), bytes(value));
    }

    @Override
    public Optional<Source> find(ResourceId id) {
      return Optional.ofNullable(entries.get(id))
          .map(value -> new Source(Origin.OTHER, "fixture", () -> new ByteArrayInputStream(value)));
    }

    @Override
    public Map<ResourceId, Source> list(String directory) {
      return Map.of();
    }
  }
}
