package design.inherent.lootr_styled.resources;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;

/** Native material binding for the four ordinary Lootr barrel presentation states. */
public final class BarrelCompiler {
  public static final ResourceId DEFAULT_MODEL =
      ResourceId.parse("lootr_styled:models/block/barrel_clasps.json");

  private BarrelCompiler() {}

  static void retarget(JsonObject model, ContainerTargets.Barrel barrel) {
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
