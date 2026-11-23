package design.inherent.lootr_styled.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.lootr_styled.resources.JsonDocuments;
import design.inherent.reform.owner.PolicyRefusal;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Calls the optional EMF data/cuboid factories against frozen inputs. No EMF root, renderer,
 * animation evaluation, global resource lookup, model registration, or live vanilla model is used.
 */
final class DetachedEmfChest {
  private static final String PREFIX = "traben.entity_model_features.models.";

  private DetachedEmfChest() {}

  static ModelPart prepare(ChestPreviewInputs input, ModelPart ownedNative, Runnable checkpoint) {
    checkpoint.run();
    try {
      return new Adapter(input, checkpoint).prepare(ownedNative);
    } catch (ReflectiveOperationException | IOException | LinkageError failed) {
      Throwable cause =
          failed instanceof InvocationTargetException invocation ? invocation.getCause() : failed;
      if (cause instanceof PolicyRefusal refused) throw refused;
      if (cause instanceof java.util.concurrent.CancellationException cancelled) throw cancelled;
      var refusal =
          new PolicyRefusal(
              "lootr_styled.preview.rig_unavailable",
              "The installed EMF adapter could not prepare this isolated chest rig");
      refusal.initCause(cause);
      throw refusal;
    } catch (RuntimeException failed) {
      if (failed instanceof PolicyRefusal
          || failed instanceof java.util.concurrent.CancellationException) throw failed;
      var refusal =
          new PolicyRefusal(
              "lootr_styled.preview.rig_unsupported",
              "The chest rig data could not be prepared without native fallback");
      refusal.initCause(failed);
      throw refusal;
    }
  }

  private static final class Adapter {
    final ChestPreviewInputs input;
    final Runnable checkpoint;
    final Gson gson = new Gson();
    final Class<?> data = Class.forName(PREFIX + "jem_objects.EMFPartData");
    final Class<?> jem = Class.forName(PREFIX + "jem_objects.EMFJemData");
    final Class<?> custom = Class.forName(PREFIX + "parts.EMFModelPartCustom");
    final Class<?> root = Class.forName(PREFIX + "parts.EMFModelPartRoot");
    final Method copy = data.getDeclaredMethod("copyFrom", data);
    final Method prepare = data.getMethod("prepare", int[].class, jem);
    final Set<ResourceId> resolving = new HashSet<>();
    int nodes;

    Adapter(ChestPreviewInputs input, Runnable checkpoint) throws ReflectiveOperationException {
      this.input = input;
      this.checkpoint = checkpoint;
      if (!copy.trySetAccessible())
        throw new IllegalAccessException("EMF copyFrom is inaccessible");
    }

    ModelPart prepare(ModelPart nativeRoot) throws ReflectiveOperationException, IOException {
      var rig = input.rig().orElseThrow();
      var json = JsonDocuments.object(bytes(rig));
      var attachments = new HashMap<String, List<ModelPart>>();
      var replaced = new HashSet<String>();
      Object parent = jem.getConstructor().newInstance();
      int index = 0;
      for (var element : json.getAsJsonArray("models")) {
        checkpoint.run();
        Object part = gson.fromJson(element, data);
        resolve(part, rig, 0);
        String target = (String) data.getField("part").get(part);
        if (!Set.of("root", "bottom", "lid", "lock").contains(target))
          throw unsupported("This chest attachment has no isolated native binding: " + target);
        if (target.equals("root") && !data.getField("attach").getBoolean(part))
          throw unsupported("Replacing the root chest hierarchy is not isolated");
        prepare.invoke(part, new int[] {64, 64}, parent);
        validateTransforms(part);
        var model =
            (ModelPart)
                custom
                    .getConstructor(data, int.class, String.class, String.class, root)
                    .newInstance(part, index++, target, "lootr_styled_preview_" + index, null);
        attachments.computeIfAbsent(target, ignored -> new ArrayList<>()).add(model);
        if (!data.getField("attach").getBoolean(part)) replaced.add(target);
      }
      var children = new HashMap<String, ModelPart>();
      for (String name : List.of("bottom", "lid", "lock")) {
        var source = nativeRoot.getChild(name);
        var cubes = new ArrayList<ModelPart.Cube>();
        if (!replaced.contains(name))
          source.visit(
              new PoseStack(),
              (pose, path, cubeIndex, cube) -> {
                if (!path.isEmpty())
                  throw unsupported("The native chest attachment has unknown children");
                cubes.add(cube);
              });
        var added = new HashMap<String, ModelPart>();
        var parts = attachments.getOrDefault(name, List.of());
        for (int i = 0; i < parts.size(); i++) added.put("lootr_styled_preview_" + i, parts.get(i));
        var owned = new ModelPart(cubes, added);
        owned.loadPose(source.storePose());
        owned.setInitialPose(source.getInitialPose());
        children.put(name, owned);
      }
      var roots = attachments.getOrDefault("root", List.of());
      for (int i = 0; i < roots.size(); i++)
        children.put("lootr_styled_preview_root_" + i, roots.get(i));
      checkpoint.run();
      return new ModelPart(List.of(), children);
    }

    byte[] bytes(ResourceId id) throws IOException {
      checkpoint.run();
      try (var stream =
          input
              .resources()
              .open(id)
              .orElseThrow(() -> unsupported("A frozen chest rig dependency is missing: " + id))) {
        return stream.readAllBytes();
      }
    }

    void resolve(Object part, ResourceId owner, int depth)
        throws ReflectiveOperationException, IOException {
      checkpoint.run();
      if (depth > 32 || ++nodes > 4096) throw unsupported("Chest rig expansion exceeds its budget");
      String reference = (String) data.getField("model").get(part);
      if (!reference.isEmpty()) {
        if (!reference.endsWith(".jpm") || reference.contains(":") || reference.startsWith("/"))
          throw unsupported("The chest rig dependency is not local");
        var id =
            new ResourceId(
                owner.namespace(),
                owner.path().substring(0, owner.path().lastIndexOf('/') + 1) + reference);
        if (!resolving.add(id)) throw unsupported("The chest rig dependency is cyclic");
        Object inherited = gson.fromJson(JsonDocuments.object(bytes(id)), data);
        resolve(inherited, id, depth + 1);
        copy.invoke(part, inherited);
        resolving.remove(id);
        data.getField("model").set(part, "");
      }
      String texture = (String) data.getField("texture").get(part);
      if (texture != null && !texture.isBlank())
        throw unsupported("The chest rig texture is outside the captured artwork mapping");
      String baseId = (String) data.getField("baseId").get(part);
      if (baseId != null && !baseId.isBlank())
        throw unsupported("This chest rig needs model inheritance that is not isolated");
      validateTransforms(part);
      Object single = data.getField("submodel").get(part);
      if (single != null) resolve(single, owner, depth + 1);
      for (Object child : (Iterable<?>) data.getField("submodels").get(part))
        resolve(child, owner, depth + 1);
    }

    void validateTransforms(Object part) throws ReflectiveOperationException {
      for (String field : List.of("translate", "rotate")) {
        float[] values = (float[]) data.getField(field).get(part);
        if (values == null) continue;
        if (values.length != 3) throw unsupported("The chest rig has an invalid " + field);
        for (float value : values)
          if (!Float.isFinite(value)) throw unsupported("The chest rig has a nonfinite " + field);
      }
    }
  }

  private static PolicyRefusal unsupported(String message) {
    return new PolicyRefusal("lootr_styled.preview.rig_unsupported", message);
  }
}
