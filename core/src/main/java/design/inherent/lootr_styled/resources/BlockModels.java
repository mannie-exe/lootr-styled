package design.inherent.lootr_styled.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import java.util.HashSet;
import java.util.Set;

/** Resolves declarative cuboids only; custom executable model loaders keep native ownership. */
public final class BlockModels {
  private final ResourceInputs inputs;

  public BlockModels(ResourceInputs inputs) {
    this.inputs = inputs;
  }

  public JsonObject resolve(ResourceId id) {
    var result = resolve(id, new HashSet<>());
    validate(result);
    return result;
  }

  private JsonObject resolve(ResourceId id, Set<ResourceId> visiting) {
    if (visiting.size() >= 32 || !visiting.add(id)) {
      throw new IllegalArgumentException("Cyclic or deep model parent at " + id);
    }
    try {
      var child = JsonDocuments.object(inputs.require(id).bytes());
      if (child.has("loader") || child.has("fabric:type") || child.has("type")) {
        throw new IllegalArgumentException("Custom block-model loader is not admitted: " + id);
      }
      JsonObject result = new JsonObject();
      if (child.has("parent")) {
        String name = string(child.get("parent"));
        var parent = resourceName(name);
        result =
            resolve(
                new ResourceId(parent.namespace(), "models/" + parent.path() + ".json"), visiting);
      }
      for (var entry : child.entrySet()) {
        if (entry.getKey().equals("parent")) continue;
        if (entry.getKey().equals("textures") && result.has("textures")) {
          if (!entry.getValue().isJsonObject())
            throw new IllegalArgumentException("Invalid textures");
          for (var texture : entry.getValue().getAsJsonObject().entrySet()) {
            result.getAsJsonObject("textures").add(texture.getKey(), texture.getValue().deepCopy());
          }
        } else {
          result.add(entry.getKey(), entry.getValue().deepCopy());
        }
      }
      return result;
    } finally {
      visiting.remove(id);
    }
  }

  public static void validate(JsonObject model) {
    if (model.has("elements")) {
      if (!model.get("elements").isJsonArray() || model.getAsJsonArray("elements").size() > 1024) {
        throw new IllegalArgumentException("Block model exceeds 1024 cuboids");
      }
      for (var entry : model.getAsJsonArray("elements")) {
        if (!entry.isJsonObject()) throw new IllegalArgumentException("Invalid cuboid");
        for (String corner : new String[] {"from", "to"}) {
          var coordinate = entry.getAsJsonObject().get(corner);
          if (coordinate == null
              || !coordinate.isJsonArray()
              || coordinate.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("Cuboid requires three " + corner + " coordinates");
          }
          for (var value : coordinate.getAsJsonArray()) {
            if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble())
                || value.getAsDouble() < -16
                || value.getAsDouble() > 32) {
              throw new IllegalArgumentException("Cuboid coordinate outside native envelope");
            }
          }
        }
      }
    }
    if (model.has("textures")) {
      if (!model.get("textures").isJsonObject())
        throw new IllegalArgumentException("Invalid texture map");
      var textures = model.getAsJsonObject("textures");
      if (textures.size() > 128) throw new IllegalArgumentException("Too many model textures");
      for (String key : textures.keySet()) {
        var seen = new HashSet<String>();
        String value = "#" + key;
        while (value.startsWith("#")) {
          String name = value.substring(1);
          if (!seen.add(name) || !textures.has(name)) {
            throw new IllegalArgumentException("Cyclic or missing texture variable " + name);
          }
          value = string(textures.get(name));
        }
        resourceName(value);
      }
    }
  }

  public static ResourceId resourceName(String name) {
    return ResourceId.parse(name.indexOf(':') < 0 ? "minecraft:" + name : name);
  }

  private static String string(JsonElement value) {
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("Expected resource string");
    }
    return value.getAsString();
  }
}
