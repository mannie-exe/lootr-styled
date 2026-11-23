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

  record GraphicInput(
      design.inherent.reform.presentation.graphics.GraphicScene scene,
      boolean nativeLidHasGeometry,
      boolean nativeLockHasGeometry) {}

  static GraphicInput chestScene(
      ChestPreviewInputs input,
      GraphicLimits limits,
      design.inherent.reform.presentation.graphics.GraphicLedger ledger) {
    var prepared =
        invoke(
            type("NativeChestPreview"),
            "prepare",
            new Class<?>[] {ChestPreviewInputs.class, GraphicLimits.class, Runnable.class},
            null,
            input,
            limits,
            (Runnable) () -> {});
    var mesh = (GraphicMesh) invoke(prepared.getClass(), "mesh", new Class<?>[0], prepared);
    var bundle =
        invoke(
            type("StylePreviewMesh"),
            "chest",
            new Class<?>[] {
              ChestPreviewInputs.class,
              GraphicMesh.class,
              GraphicLimits.class,
              java.util.function.Function.class,
              Runnable.class
            },
            null,
            input,
            mesh,
            limits,
            (java.util.function.Function<
                    DocumentId, design.inherent.reform.presentation.graphics.GraphicMaterial>)
                PreviewProbeAccess::testMaterial,
            (Runnable) () -> {});
    return new GraphicInput(
        prepareScene(bundle, ledger),
        (boolean) invoke(prepared.getClass(), "nativeLidHasGeometry", new Class<?>[0], prepared),
        (boolean) invoke(prepared.getClass(), "nativeLockHasGeometry", new Class<?>[0], prepared));
  }

  static GraphicInput barrelScene(
      design.inherent.lootr_styled.resources.BarrelPreviewInputs input,
      GraphicLimits limits,
      design.inherent.reform.presentation.graphics.GraphicLedger ledger) {
    var baked =
        invoke(
            type("NativeBarrelPreview"),
            "bake",
            new Class<?>[] {
              design.inherent.lootr_styled.resources.BarrelPreviewInputs.class,
              design.inherent.reform.presentation.graphics.GraphicLedger.class,
              Runnable.class
            },
            null,
            input,
            ledger,
            (Runnable) () -> {});
    var bundle =
        invoke(
            type("StylePreviewMesh"),
            "convert",
            new Class<?>[] {
              design.inherent.lootr_styled.resources.BarrelPreviewMesh.class,
              GraphicLimits.class,
              java.util.function.Function.class,
              Runnable.class
            },
            null,
            baked,
            limits,
            (java.util.function.Function<
                    DocumentId, design.inherent.reform.presentation.graphics.GraphicMaterial>)
                PreviewProbeAccess::testMaterial,
            (Runnable) () -> {});
    return new GraphicInput(prepareScene(bundle, ledger), false, false);
  }

  private static design.inherent.reform.presentation.graphics.GraphicScene prepareScene(
      Object bundle, design.inherent.reform.presentation.graphics.GraphicLedger ledger) {
    return (design.inherent.reform.presentation.graphics.GraphicScene)
        invoke(
            bundle.getClass(),
            "prepare",
            new Class<?>[] {
              design.inherent.reform.presentation.graphics.GraphicLedger.class,
              design.inherent.reform.serialization.JsonLimits.class,
              Runnable.class
            },
            bundle,
            ledger,
            new design.inherent.reform.serialization.JsonLimits(16384, 32, 4096, 8192, 256, 128),
            (Runnable) () -> {});
  }

  // The fixture declares this exact material; it does not establish automatic native policy.
  private static design.inherent.reform.presentation.graphics.GraphicMaterial testMaterial(
      DocumentId id) {
    return new design.inherent.reform.presentation.graphics.GraphicMaterial(
        id,
        design.inherent.reform.presentation.graphics.GraphicMaterial.Alpha.CUTOUT,
        0.1f,
        design.inherent.reform.presentation.graphics.GraphicMaterial.Cull.BACK,
        design.inherent.reform.presentation.graphics.GraphicMaterial.Filter.NEAREST,
        design.inherent.reform.presentation.graphics.GraphicMaterial.Wrap.CLAMP);
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
