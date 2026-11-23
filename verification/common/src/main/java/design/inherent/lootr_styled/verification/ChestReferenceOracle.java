package design.inherent.lootr_styled.verification;

import design.inherent.reform.presentation.graphics.GraphicMesh;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Read-only fixture oracle; copies loaded part poses/structure, never renders or changes them. */
final class ChestReferenceOracle {
  private ChestReferenceOracle() {}

  static ModelPart copy(boolean trapped) throws Exception {
    var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
    var field = dispatcher.getClass().getDeclaredField("renderers");
    field.setAccessible(true);
    for (var entry : ((Map<?, ?>) field.get(dispatcher)).entrySet()) {
      if (!(entry.getKey() instanceof BlockEntityType<?> type)) continue;
      String id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString();
      if (!id.startsWith("lootr:")
          || !id.endsWith(trapped ? "trapped_chest" : "chest")
          || (!trapped && id.contains("trapped"))) continue;
      Object renderer = entry.getValue();
      var parts = new HashMap<String, ModelPart>();
      for (Class<?> owner = renderer.getClass();
          owner != Object.class;
          owner = owner.getSuperclass())
        for (var member : owner.getDeclaredFields()) {
          if (Modifier.isStatic(member.getModifiers())) continue;
          member.setAccessible(true);
          Object value = member.get(renderer);
          if (value instanceof ModelPart part
              && List.of("bottom", "lid", "lock").contains(member.getName()))
            parts.putIfAbsent(member.getName(), clone(part, new int[1]));
          if (member.getName().equals("singleModel") && value != null)
            for (Class<?> model = value.getClass();
                model != Object.class;
                model = model.getSuperclass())
              for (Field candidate : model.getDeclaredFields())
                if (candidate.getType() == ModelPart.class && candidate.getName().equals("root")) {
                  candidate.setAccessible(true);
                  return clone((ModelPart) candidate.get(value), new int[1]);
                }
        }
      if (!parts.keySet().containsAll(List.of("bottom", "lid", "lock")))
        throw new IllegalStateException("Loaded chest reference parts are incomplete: " + id);
      return new ModelPart(List.of(), parts);
    }
    throw new IllegalStateException("Loaded chest renderer was not found");
  }

  private static ModelPart clone(ModelPart source, int[] count) throws Exception {
    if (++count[0] > 8192) throw new IllegalStateException("Loaded reference exceeds node budget");
    var cubes = ModelPart.class.getDeclaredField("cubes");
    var children = ModelPart.class.getDeclaredField("children");
    cubes.setAccessible(true);
    children.setAccessible(true);
    var copyCubes = new ArrayList<ModelPart.Cube>();
    for (Object cube : (List<?>) cubes.get(source)) copyCubes.add((ModelPart.Cube) cube);
    var copyChildren = new HashMap<String, ModelPart>();
    for (var child : ((Map<?, ?>) children.get(source)).entrySet())
      copyChildren.put((String) child.getKey(), clone((ModelPart) child.getValue(), count));
    var result = new ModelPart(copyCubes, copyChildren);
    result.loadPose(source.storePose());
    result.setInitialPose(source.getInitialPose());
    result.visible = source.visible;
    result.skipDraw = source.skipDraw;
    return result;
  }

  static List<String> vertices(GraphicMesh mesh) {
    return mesh.vertices().stream()
        .map(
            vertex ->
                String.format(
                    Locale.ROOT,
                    "%.6f/%.6f/%.6f/%.6f/%.6f/%.6f/%.6f/%.6f",
                    clean(vertex.x()),
                    clean(vertex.y()),
                    clean(vertex.z()),
                    clean(vertex.u()),
                    clean(vertex.v()),
                    clean(vertex.nx()),
                    clean(vertex.ny()),
                    clean(vertex.nz())))
        .sorted()
        .toList();
  }

  private static float clean(float value) {
    return Math.abs(value) < 0.0000005f ? 0 : value;
  }
}
