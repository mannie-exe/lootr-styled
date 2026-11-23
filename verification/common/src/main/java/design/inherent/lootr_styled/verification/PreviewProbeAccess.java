package design.inherent.lootr_styled.verification;

import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.lootr_styled.resources.ResourceAccess;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelPart;

/** Observer-only access; the fixture keeps its own JPMS package and adds no production API. */
final class PreviewProbeAccess {
  private static final String PREFIX = "design.inherent.lootr_styled.client.";

  private PreviewProbeAccess() {}

  static Object activePack() {
    return ((Optional<?>) invoke(NativeUi.class, "activePack", new Class<?>[0], null))
        .orElseThrow();
  }

  static UUID generation(Object pack) {
    return (UUID) invoke(pack.getClass(), "id", new Class<?>[0], pack);
  }

  static ResourceAccess lower(Object pack) {
    return (ResourceAccess) invoke(pack.getClass(), "lowerSources", new Class<?>[0], pack);
  }

  static void validateModel(byte[] bytes) {
    invoke(NativeUi.class, "validateModel", new Class<?>[] {byte[].class}, null, bytes);
  }

  static boolean ordinaryChestIsolated() {
    return (boolean) invoke(NativeUi.class, "ordinaryChestIsolated", new Class<?>[0], null);
  }

  static GraphicMesh bake(ChestPreviewInputs input, GraphicLimits limits, Runnable checkpoint) {
    return (GraphicMesh)
        invoke(
            type("NativeChestPreview"),
            "bake",
            new Class<?>[] {ChestPreviewInputs.class, GraphicLimits.class, Runnable.class},
            null,
            input,
            limits,
            checkpoint);
  }

  static void pose(ModelPart root, float lid) {
    invoke(
        type("NativeChestPreview"),
        "pose",
        new Class<?>[] {ModelPart.class, float.class},
        null,
        root,
        lid);
  }

  static GraphicMesh geometry(ModelPart root, GraphicLimits limits) throws Exception {
    var field = type("NativeChestPreview").getDeclaredField("MATERIAL");
    field.setAccessible(true);
    return (GraphicMesh)
        invoke(
            type("EntityPreviewGeometry"),
            "capture",
            new Class<?>[] {ModelPart.class, DocumentId.class, GraphicLimits.class, Runnable.class},
            null,
            root,
            field.get(null),
            limits,
            (Runnable) () -> {});
  }

  private static Class<?> type(String name) {
    try {
      return Class.forName(PREFIX + name);
    } catch (ClassNotFoundException failed) {
      throw new IllegalStateException(failed);
    }
  }

  private static Object invoke(
      Class<?> owner, String name, Class<?>[] types, Object receiver, Object... arguments) {
    try {
      var method = owner.getDeclaredMethod(name, types);
      method.setAccessible(true);
      return method.invoke(receiver, arguments);
    } catch (InvocationTargetException failed) {
      if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
      if (failed.getCause() instanceof Error error) throw error;
      throw new IllegalStateException(failed.getCause());
    } catch (ReflectiveOperationException failed) {
      throw new IllegalStateException(failed);
    }
  }
}
