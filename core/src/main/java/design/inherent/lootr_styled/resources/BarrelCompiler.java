package design.inherent.lootr_styled.resources;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native material binding for the four ordinary Lootr barrel presentation states. */
public final class BarrelCompiler {
  /** Internal generated-model marker; never a block state or server authority. */
  public static final String GENERATED_MARKER = "lootr_styled:barrel";

  private BarrelCompiler() {}

  public static ResourceId defaultModel(ContainerTargets.Barrel barrel, boolean packed) {
    return ResourceId.parse(
        "lootr_styled:models/block/barrel/default/"
            + state(barrel)
            + (packed ? "" : "_native")
            + ".json");
  }

  static ResourceId defaultSurface(ContainerTargets.Barrel barrel) {
    return ResourceId.parse(
        "lootr_styled:textures/block/default/barrel/"
            + (barrel.material().equals("opened_barrel") ? "looted" : "unlooted")
            + ".png");
  }

  private static String state(ContainerTargets.Barrel barrel) {
    return (barrel.material().equals("opened_barrel") ? "looted" : "unlooted")
        + (barrel.open() ? "_open" : "_closed");
  }

  public static byte[] bundledModel(ContainerTargets.Barrel barrel) {
    return bundled(defaultModel(barrel, false));
  }

  /** Only the actually published matching artwork may use the prepainted surface atlas. */
  static boolean matchesDefaultArtwork(
      ContainerTargets.Barrel barrel, Map<ResourceId, byte[]> artwork) {
    for (String face : List.of("side", "top", "top_open", "bottom")) {
      var target =
          ResourceId.parse("lootr:textures/block/" + barrel.material() + "_" + face + ".png");
      byte[] applied = artwork.get(target), expected = Artwork.BYTES.get(target);
      if (applied == null || expected == null || !Arrays.equals(applied, expected)) return false;
    }
    return true;
  }

  private static byte[] bundled(ResourceId id) {
    try (var stream =
        BarrelCompiler.class.getResourceAsStream("/assets/" + id.namespace() + "/" + id.path())) {
      if (stream == null)
        throw new IllegalArgumentException("Bundled Default resource unavailable: " + id);
      byte[] bytes = stream.readNBytes(ResourceInputs.MAX_FILE_BYTES + 1);
      if (bytes.length > ResourceInputs.MAX_FILE_BYTES)
        throw new IllegalArgumentException("Bundled Default resource exceeds byte policy");
      return bytes;
    } catch (IOException ex) {
      throw new IllegalArgumentException("Bundled Default resource unreadable: " + id, ex);
    }
  }

  private static final class Artwork {
    private static final Map<ResourceId, byte[]> BYTES = load();

    private static Map<ResourceId, byte[]> load() {
      var result = new HashMap<ResourceId, byte[]>();
      try {
        for (var entry : ContainerTargets.LEGACY.defaultArtwork().entrySet()) {
          if (entry.getKey().path().startsWith("textures/block/"))
            result.put(entry.getKey(), bundled(entry.getValue()));
        }
      } catch (IllegalArgumentException ex) {
        return Map.of();
      }
      return Map.copyOf(result);
    }
  }

  public static void retarget(JsonObject model, ContainerTargets.Barrel barrel) {
    if (!model.has("textures")) return;
    var textures = model.getAsJsonObject("textures");
    for (var entry : textures.entrySet()) {
      if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
        continue;
      String value = entry.getValue().getAsString();
      if (value.startsWith("#")) continue;
      var name = BlockModels.resourceName(value);
      if ((name.namespace().equals("minecraft") || name.namespace().equals("lootr"))
          && name.path().matches("block/barrel_(side|top|top_open|bottom)")) {
        String face = name.path().substring("block/barrel_".length());
        if (barrel.open() && face.equals("top")) face = "top_open";
        textures.addProperty(entry.getKey(), "lootr:block/" + barrel.material() + "_" + face);
      }
    }
  }
}
