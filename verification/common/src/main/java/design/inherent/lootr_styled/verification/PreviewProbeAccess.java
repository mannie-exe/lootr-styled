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

  record HostProvider(
      design.inherent.reform.minecraft.client.ClientGraphicProvider<
              design.inherent.lootr_styled.profile.ClientSettings, Object>
          provider,
      java.util.Set<String> policyThreads,
      java.util.concurrent.atomic.AtomicInteger materialCalls,
      java.util.concurrent.atomic.AtomicInteger clientCalls,
      java.util.concurrent.atomic.AtomicInteger verifiedImages) {}

  @SuppressWarnings("unchecked") // Observer checks the actual bounded subject codec before use.
  static HostProvider hostProvider(GraphicLimits limits) throws Exception {
    var threads = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var clientCalls = new java.util.concurrent.atomic.AtomicInteger();
    var client = Thread.currentThread();
    java.util.function.Function<
            DocumentId, design.inherent.reform.presentation.graphics.GraphicMaterial>
        policy =
            id -> {
              threads.add(Thread.currentThread().getName());
              calls.incrementAndGet();
              if (Thread.currentThread() == client) clientCalls.incrementAndGet();
              return testMaterial(id);
            };
    java.util.function.BiFunction<
            design.inherent.lootr_styled.resources.BarrelPreviewMesh.Face,
            DocumentId,
            design.inherent.reform.presentation.graphics.GraphicMaterial>
        barrel = (face, id) -> policy.apply(id);
    var constructor =
        type("StyleGraphicProvider")
            .getDeclaredConstructor(
                GraphicLimits.class,
                java.util.function.Function.class,
                java.util.function.BiFunction.class);
    constructor.setAccessible(true);
    var provider =
        (design.inherent.reform.minecraft.client.ClientGraphicProvider<
                design.inherent.lootr_styled.profile.ClientSettings, Object>)
            constructor.newInstance(limits, policy, barrel);
    var verifiedImages = new java.util.concurrent.atomic.AtomicInteger();
    var lower = lower(activePack());
    var originals =
        new java.util.HashMap<
            design.inherent.lootr_styled.ResourceId, java.awt.image.BufferedImage>();
    for (var target : design.inherent.lootr_styled.client.ClientRuntime.targets().artwork())
      try (var stream = lower.find(target).orElseThrow().opener().open()) {
        originals.put(target, java.util.Objects.requireNonNull(javax.imageio.ImageIO.read(stream)));
      }
    var observed =
        new design.inherent.reform.minecraft.client.ClientGraphicProvider<
            design.inherent.lootr_styled.profile.ClientSettings, Object>() {
          public DocumentId id() {
            return provider.id();
          }

          public design.inherent.reform.value.ValueType<Object> subjectType() {
            return provider.subjectType();
          }

          public GraphicLimits limits() {
            return provider.limits();
          }

          public design.inherent.reform.reference.ReferenceStamp resourceStamp() {
            return provider.resourceStamp();
          }

          public java.util.concurrent.CompletionStage<
                  design.inherent.reform.presentation.graphics.GraphicScenePreparation>
              prepare(
                  design.inherent.reform.presentation.graphics.GraphicRequest<
                          design.inherent.lootr_styled.profile.ClientSettings, Object>
                      request,
                  design.inherent.reform.minecraft.client.ClientGraphicWork work) {
            return provider
                .prepare(request, work)
                .thenApply(
                    result -> {
                      if (!(result
                          instanceof
                          design.inherent.reform.presentation.graphics.GraphicScenePreparation.Ready
                              ready)) return result;
                      if (!request
                          .input()
                          .candidate()
                          .value()
                          .artwork()
                          .equals(design.inherent.lootr_styled.profile.ClientSettings.VANILLA))
                        throw new IllegalStateException(
                            "Host failed to capture the unsaved artwork identity");
                      var subject =
                          subjectType()
                              .codec()
                              .encodeStart(
                                  com.mojang.serialization.JsonOps.INSTANCE, request.subject())
                              .getOrThrow()
                              .getAsJsonObject();
                      var chest =
                          design.inherent.lootr_styled.client.ClientRuntime.targets()
                              .chestArtwork(
                                  subject.get("container").getAsString().equals("TRAPPED_CHEST"),
                                  subject.get("looted").getAsBoolean());
                      for (var entry : ready.graphic().scene().textures().entrySet()) {
                        var id = entry.getKey();
                        var target =
                            id.toString().equals("lootr_styled:preview/chest")
                                ? chest
                                : originals.keySet().stream()
                                    .filter(
                                        t ->
                                            id.toString()
                                                .equals(
                                                    "lootr_styled:preview/"
                                                        + design.inherent.lootr_styled.resources
                                                            .TextureImages.hash(
                                                            t.toString()
                                                                .getBytes(
                                                                    java.nio.charset
                                                                        .StandardCharsets.UTF_8))))
                                    .findFirst()
                                    .orElseThrow();
                        var expected = originals.get(target);
                        var pixels = entry.getValue();
                        if (expected.getWidth() != pixels.width()
                            || expected.getHeight() != pixels.height()
                            || !java.util.Arrays.equals(
                                expected.getRGB(
                                    0,
                                    0,
                                    expected.getWidth(),
                                    expected.getHeight(),
                                    null,
                                    0,
                                    expected.getWidth()),
                                pixels.argb()))
                          throw new IllegalStateException(
                              "Actual provider scene ignored the unsaved inherited artwork: "
                                  + target);
                        verifiedImages.incrementAndGet();
                      }
                      verifyStillMaterials(ready.graphic().scene());
                      return result;
                    });
          }
        };
    return new HostProvider(observed, threads, calls, clientCalls, verifiedImages);
  }

  private static void verifyStillMaterials(
      design.inherent.reform.presentation.graphics.GraphicScene scene) {
    for (var triangle : scene.mesh().triangles()) {
      var material = scene.materials().get(triangle.material());
      var pixels = scene.textures().get(material.texture());
      var vertices = scene.mesh().vertices();
      var a = vertices.get(triangle.a());
      var b = vertices.get(triangle.b());
      var c = vertices.get(triangle.c());
      double u0 = Math.min(a.u(), Math.min(b.u(), c.u())),
          u1 = Math.max(a.u(), Math.max(b.u(), c.u()));
      double v0 = Math.min(a.v(), Math.min(b.v(), c.v())),
          v1 = Math.max(a.v(), Math.max(b.v(), c.v()));
      int left = (int) Math.max(0, Math.min(pixels.width() - 1, Math.floor(u0 * pixels.width())));
      int top = (int) Math.max(0, Math.min(pixels.height() - 1, Math.floor(v0 * pixels.height())));
      int right =
          (int) Math.max(left + 1, Math.min(pixels.width(), Math.ceil(u1 * pixels.width())));
      int bottom =
          (int) Math.max(top + 1, Math.min(pixels.height(), Math.ceil(v1 * pixels.height())));
      boolean clear = false, partial = false;
      for (int y = top; y < bottom; y++)
        for (int x = left; x < right; x++) {
          int alpha = pixels.pixel(x, y) >>> 24;
          clear |= alpha == 0;
          partial |= alpha > 0 && alpha < 255;
        }
      var expected =
          partial
              ? design.inherent.reform.presentation.graphics.GraphicMaterial.Alpha.TRANSLUCENT
              : clear
                  ? design.inherent.reform.presentation.graphics.GraphicMaterial.Alpha.CUTOUT
                  : design.inherent.reform.presentation.graphics.GraphicMaterial.Alpha.OPAQUE;
      if (material.alpha() != expected)
        throw new IllegalStateException(
            "The actual provider did not classify its displayed still: "
                + material
                + " expected "
                + expected);
    }
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
