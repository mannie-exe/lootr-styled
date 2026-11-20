package design.inherent.lootr_styled.profile;

import static org.junit.jupiter.api.Assertions.*;

import design.inherent.lootr_styled.ResourceId;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class ProfileCodecTest {
  private static final ResourceId ID = ResourceId.parse("example:woodland");

  private static StyleProfile decode(String json) {
    return ProfileCodec.parse(ID, json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void emptyProfileInheritsWithoutChangingIdentity() {
    var profile = decode("{\"format\":1}");
    assertEquals(ID, profile.id());
    assertEquals(ID.toString(), profile.title());
    assertTrue(profile.models().isEmpty());
    assertTrue(profile.artwork().isEmpty());
  }

  @Test
  void decodesIndependentModelAndArtworkReferences() {
    var profile =
        decode(
            """
        {"format":1,"title":"箱子",
         "models":{"lootr:models/block/barrel_unopened.json":"example:models/barrel.json"},
         "artwork":{"lootr:textures/chest.png":"example:textures/chest.png"}}
        """);
    assertEquals("箱子", profile.title());
    assertEquals(
        ResourceId.parse("example:models/barrel.json"),
        profile.models().get(ResourceId.parse("lootr:models/block/barrel_unopened.json")));
    assertEquals(1, profile.artwork().size());
  }

  @Test
  void rejectsImplicitVersionsAndNumericCoercion() {
    for (String json :
        new String[] {
          "{}",
          "{\"format\":2}",
          "{\"format\":\"1\"}",
          "{\"format\":1.0}",
          "{\"format\":1e0}",
          "{\"format\":null}"
        }) {
      assertThrows(IllegalArgumentException.class, () -> decode(json), json);
    }
  }

  @Test
  void refusesDuplicatePropertiesAndTargets() {
    assertThrows(IllegalArgumentException.class, () -> decode("{\"format\":1,\"format\":1}"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            decode(
                """
        {"format":1,"models":{"lootr:models/a.json":"a:models/a.json",
                               "lootr:models/a.json":"b:models/b.json"}}
        """));
  }

  @Test
  void refusesUnknownMetadataRatherThanApplyingPartially() {
    for (String extra :
        new String[] {
          "\"id\":\"other:identity\"",
          "\"execute\":\"anything\"",
          "\"inherit\":\"lootr_styled:default\""
        }) {
      assertThrows(IllegalArgumentException.class, () -> decode("{\"format\":1," + extra + "}"));
    }
  }

  @Test
  void rejectsCommentsTrailingValuesAndWrongTypes() {
    for (String json :
        new String[] {
          "{format:1}",
          "{\"format\":1}//comment",
          "{\"format\":1}{}",
          "{\"format\":1,\"title\":42}",
          "{\"format\":1,\"models\":[]}",
          "{\"format\":1,\"models\":{\"a:b.json\":{}}}",
          "[{}]"
        }) {
      assertThrows(IllegalArgumentException.class, () -> decode(json), json);
    }
  }

  @Test
  void rejectsEscapingAndRemoteResourceReferences() {
    for (String source :
        new String[] {
          "a:../model.json", "https://host/model.json", "a:/absolute.json", "a:a%2fb.json"
        }) {
      assertThrows(
          IllegalArgumentException.class,
          () -> decode("{\"format\":1,\"models\":{\"lootr:models/a.json\":\"" + source + "\"}}"));
    }
  }

  @Test
  void onlyAdmitsDeclaredResourceKinds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> decode("{\"format\":1,\"models\":{\"a:b.class\":\"a:b.json\"}}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> decode("{\"format\":1,\"artwork\":{\"a:b.gif\":\"a:b.png\"}}"));
    assertEquals(
        1,
        decode("{\"format\":1,\"artwork\":{\"a:b.png.mcmeta\":\"b:c.png.mcmeta\"}}")
            .artwork()
            .size());
  }

  @Test
  void rejectsInvalidUtf8AndUnpairedTitleSurrogates() {
    byte[] invalid = new byte[] {'{', '"', (byte) 0xc0, (byte) 0xaf, '"', ':', '1', '}'};
    assertThrows(IllegalArgumentException.class, () -> ProfileCodec.parse(ID, invalid));
    assertThrows(
        IllegalArgumentException.class, () -> decode("{\"format\":1,\"title\":\"\\ud800\"}"));
  }

  @Test
  void refusesBlankOversizedAndControlTitles() {
    for (String title : new String[] {" ", "x".repeat(161), "bad\\nline"}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> decode("{\"format\":1,\"title\":\"" + title + "\"}"));
    }
  }

  @Test
  void honorsMetadataByteBoundaryAndCallerStreamLifetime() throws Exception {
    String minimal = "{\"format\":1}";
    byte[] exact =
        (minimal + " ".repeat(ProfileCodec.MAX_METADATA_BYTES - minimal.length()))
            .getBytes(StandardCharsets.UTF_8);
    assertEquals(ID, ProfileCodec.parse(ID, exact).id());
    class TrackingInput extends ByteArrayInputStream {
      boolean closed;

      TrackingInput() {
        super(new byte[ProfileCodec.MAX_METADATA_BYTES + 100]);
      }

      @Override
      public void close() {
        closed = true;
      }
    }
    var input = new TrackingInput();
    assertThrows(IllegalArgumentException.class, () -> ProfileCodec.read(ID, input));
    assertEquals(99, input.available());
    assertFalse(input.closed);
  }

  @Test
  void capturesMapsDefensivelyInStableOrder() {
    var input = new HashMap<ResourceId, ResourceId>();
    var a = ResourceId.parse("lootr:models/a.json");
    var b = ResourceId.parse("lootr:models/b.json");
    input.put(b, b);
    input.put(a, a);
    var profile = new StyleProfile(ID, "Stable", input, java.util.Map.of());
    input.clear();
    assertEquals(java.util.List.of(a, b), new ArrayList<>(profile.models().keySet()));
    assertThrows(UnsupportedOperationException.class, () -> profile.models().clear());
  }

  @Test
  void combinedMappingsHaveOneBudget() {
    var models = new HashMap<ResourceId, ResourceId>();
    var art = new HashMap<ResourceId, ResourceId>();
    for (int i = 0; i < StyleProfile.MAX_MAPPINGS; i++) {
      var key = ResourceId.parse("lootr:models/" + i + ".json");
      models.put(key, key);
    }
    assertEquals(128, new StyleProfile(ID, "Limit", models, art).models().size());
    var extra = ResourceId.parse("lootr:textures/extra.png");
    art.put(extra, extra);
    assertThrows(IllegalArgumentException.class, () -> new StyleProfile(ID, "Over", models, art));
  }
}
