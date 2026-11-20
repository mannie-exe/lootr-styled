package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Correct the recognized legacy native wrapper's two modern physical-open leaf references. */
final class BarrelWrapper {
  private static final ResourceId OPEN =
      ResourceId.parse("lootr:models/block/lootr_barrel_open.json");

  private BarrelWrapper() {}

  static void adapt(
      ContainerTargets targets,
      ResourceInputs inputs,
      Map<ResourceId, byte[]> output,
      List<String> status) {
    if (targets != ContainerTargets.LEGACY) return;
    var resource = inputs.find(OPEN);
    if (resource.isEmpty()
        || resource.orElseThrow().source().origin() != ResourceAccess.Origin.LOOTR) return;
    var model = JsonDocuments.object(resource.orElseThrow().bytes());
    if (!model.has("loader")
        || !model.get("loader").isJsonPrimitive()
        || !model.get("loader").getAsString().equals("lootr:barrel")) return;
    boolean changed = false;
    for (String branch : List.of("unopened", "opened")) {
      String leaf = branch.equals("unopened") ? "lootr_barrel_unopened" : "lootr_opened_barrel";
      var target = ResourceId.parse("lootr:models/block/" + leaf + "_open.json");
      var child = model.get(branch);
      if (!output.containsKey(target) || child == null || !child.isJsonObject()) continue;
      var object = child.getAsJsonObject();
      if (object.size() == 1
          && object.has("parent")
          && object.get("parent").isJsonPrimitive()
          && object.get("parent").getAsString().equals("lootr:block/" + leaf)) {
        object.addProperty("parent", "lootr:block/" + leaf + "_open");
        changed = true;
      }
    }
    if (changed) {
      output.put(OPEN, model.toString().getBytes(StandardCharsets.UTF_8));
      status.add(OPEN + ": native physical-open references follow generated open leaves");
    }
  }
}
