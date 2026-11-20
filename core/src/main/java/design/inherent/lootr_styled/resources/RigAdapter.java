package design.inherent.lootr_styled.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Reload-time declarative CEM adaptation. No optional classes or live native model instances. */
public final class RigAdapter {
  private static final Pattern BASE = Pattern.compile("(?<![A-Za-z0-9_])base\\.");
  private static final Pattern KNOB = Pattern.compile("(?<![A-Za-z0-9_])knob\\.");
  private final ResourceInputs inputs;
  private final ResourceId target;
  private final boolean vanilla;
  private final Map<ResourceId, byte[]> output = new HashMap<>();
  private final Map<ResourceId, ResourceId> translated = new HashMap<>();
  private final Set<ResourceId> visiting = new HashSet<>();
  private final Map<ResourceId, Integer> costs = new HashMap<>();
  private int expandedNodes;
  private int cuboids;

  private RigAdapter(ResourceInputs inputs, ResourceId target, boolean vanilla) {
    this.inputs = inputs;
    this.target = target;
    this.vanilla = vanilla;
  }

  public static Map<ResourceId, byte[]> compile(
      ResourceInputs inputs, ResourceId source, ResourceId target, boolean vanilla) {
    var adapter = new RigAdapter(inputs, target, vanilla);
    adapter.root(source);
    return Map.copyOf(adapter.output);
  }

  private void root(ResourceId source) {
    var root = JsonDocuments.object(inputs.require(source).bytes());
    if (!root.has("models")
        || !root.get("models").isJsonArray()
        || root.getAsJsonArray("models").size() > 16) {
      throw new IllegalArgumentException("Chest rig requires a bounded model-part list");
    }
    requireTextureSize(root);
    var parts = new HashSet<String>();
    for (var entry : root.getAsJsonArray("models")) {
      if (!entry.isJsonObject()) throw new IllegalArgumentException("Invalid chest part");
      var part = entry.getAsJsonObject();
      String name = string(part.get("part"));
      if (!parts.add(name)) throw new IllegalArgumentException("Duplicate chest attachment");
      String mapped =
          vanilla
              ? switch (name) {
                case "base" -> "bottom";
                case "knob" -> "lock";
                default -> name;
              }
              : name;
      if (!Set.of("root", "bottom", "lid", "lock").contains(mapped))
        throw new IllegalArgumentException("Unsupported chest attachment " + name);
      part.addProperty("part", mapped);
    }
    if (!parts.containsAll(
        vanilla ? Set.of("base", "lid", "knob") : Set.of("bottom", "lid", "lock"))) {
      throw new IllegalArgumentException("Chest rig lacks native lid/body/lock attachments");
    }
    visiting.add(source);
    rewrite(root, source);
    visiting.remove(source);
    output.put(target, bytes(root));
  }

  private void rewrite(JsonElement value, ResourceId source) {
    if (value.isJsonPrimitive()
        && value.getAsJsonPrimitive().isNumber()
        && (!Float.isFinite(value.getAsFloat()) || Math.abs(value.getAsDouble()) > 65536)) {
      throw new IllegalArgumentException("Chest rig numeric data outside admitted envelope");
    }
    if (value.isJsonArray()) {
      for (var child : value.getAsJsonArray()) rewrite(child, source);
      return;
    }
    if (!value.isJsonObject()) return;
    charge(1);
    var node = value.getAsJsonObject();
    if (node.has("texture"))
      throw new IllegalArgumentException(
          "Chest rig texture replacement belongs in artwork mappings");
    if (node.has("textureSize")) requireTextureSize(node);
    if (node.has("id") && !string(node.get("id")).matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
      throw new IllegalArgumentException("Invalid model-part identity");
    if (node.has("coordinates")) {
      if (!node.get("coordinates").isJsonArray()
          || node.getAsJsonArray("coordinates").size() != 6
          || ++cuboids > 2048)
        throw new IllegalArgumentException("Invalid or excessive chest cuboids");
      for (int i = 0; i < 6; i++) {
        var item = node.getAsJsonArray("coordinates").get(i);
        if (!item.isJsonPrimitive()
            || !item.getAsJsonPrimitive().isNumber()
            || !Float.isFinite(item.getAsFloat())
            || Math.abs(item.getAsFloat()) > 512
            || i >= 3 && item.getAsFloat() < 0)
          throw new IllegalArgumentException("Chest cuboid outside admitted envelope");
      }
    }
    if (vanilla && node.has("animations"))
      node.add("animations", animation(node.get("animations")));
    if (node.has("model")) {
      String reference = string(node.get("model"));
      ResourceId resolved = reference(source, reference);
      ResourceId result = dependency(resolved);
      node.addProperty("model", result.path().substring(result.path().lastIndexOf('/') + 1));
    }
    for (var entry : node.entrySet()) {
      if (entry.getKey().equals("animations") || entry.getKey().equals("model")) continue;
      rewrite(entry.getValue(), source);
    }
  }

  private ResourceId dependency(ResourceId source) {
    if (visiting.contains(source)) throw new IllegalArgumentException("Cyclic chest rig reference");
    if (translated.containsKey(source)) {
      charge(costs.get(source));
      return translated.get(source);
    }
    if (visiting.size() >= 32 || translated.size() >= 64)
      throw new IllegalArgumentException("Chest rig reference budget exceeded");
    var root = JsonDocuments.object(inputs.require(source).bytes());
    String suffix = TextureImages.hash((target + "|" + source).getBytes(StandardCharsets.UTF_8));
    var name = new ResourceId(target.namespace(), "emf/cem/lootr_styled_" + suffix + ".jpm");
    visiting.add(source);
    int before = expandedNodes;
    rewrite(root, source);
    visiting.remove(source);
    translated.put(source, name);
    costs.put(source, expandedNodes - before);
    output.put(name, bytes(root));
    return name;
  }

  private void charge(int nodes) {
    if (nodes < 0 || nodes > 4096 - expandedNodes)
      throw new IllegalArgumentException("Expanded chest rig node budget exceeded");
    expandedNodes += nodes;
  }

  private static ResourceId reference(ResourceId owner, String path) {
    if (!path.endsWith(".jpm") || path.startsWith("/") || path.contains(":"))
      throw new IllegalArgumentException("Only local relative JPM references are admitted");
    return new ResourceId(
        owner.namespace(), owner.path().substring(0, owner.path().lastIndexOf('/') + 1) + path);
  }

  private static JsonElement animation(JsonElement value) {
    if (value.isJsonObject()) {
      var result = new JsonObject();
      for (var entry : value.getAsJsonObject().entrySet()) {
        String key = renamed(entry.getKey());
        if (result.has(key))
          throw new IllegalArgumentException("Attachment remapping collides in animation");
        result.add(key, animation(entry.getValue()));
      }
      return result;
    }
    if (value.isJsonArray()) {
      var result = new com.google.gson.JsonArray();
      for (var item : value.getAsJsonArray()) result.add(animation(item));
      return result;
    }
    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
      return new com.google.gson.JsonPrimitive(renamed(value.getAsString()));
    return value.deepCopy();
  }

  private static String renamed(String text) {
    return KNOB.matcher(BASE.matcher(text).replaceAll("bottom.")).replaceAll("lock.");
  }

  private static void requireTextureSize(JsonObject node) {
    var size = node.get("textureSize");
    if (size == null
        || !size.isJsonArray()
        || size.getAsJsonArray().size() != 2
        || !size.getAsJsonArray().get(0).toString().equals("64")
        || !size.getAsJsonArray().get(1).toString().equals("64"))
      throw new IllegalArgumentException(
          "Chest rig requires the qualified 64x64 Lootr material layout");
  }

  private static String string(JsonElement value) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Expected chest rig string");
    return value.getAsString();
  }

  private static byte[] bytes(JsonObject value) {
    return value.toString().getBytes(StandardCharsets.UTF_8);
  }
}
