package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.LocalFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Captures one isolated barrel's plain native model and texture inputs, without publication. */
public final class BarrelPreviewInputs {
  private final Subject subject;
  private final ContainerTargets.Barrel barrel;
  private final FrozenResources resources;
  private final Map<ResourceId, TextureImages.Size> imageSizes;
  private final List<String> diagnostics;

  private BarrelPreviewInputs(
      Subject subject,
      ContainerTargets.Barrel barrel,
      FrozenResources resources,
      Map<ResourceId, TextureImages.Size> imageSizes,
      List<String> diagnostics) {
    this.subject = java.util.Objects.requireNonNull(subject);
    this.barrel = java.util.Objects.requireNonNull(barrel);
    this.resources = java.util.Objects.requireNonNull(resources);
    this.imageSizes = Map.copyOf(imageSizes);
    this.diagnostics = List.copyOf(diagnostics);
  }

  public Subject subject() {
    return subject;
  }

  public ContainerTargets.Barrel barrel() {
    return barrel;
  }

  public FrozenResources resources() {
    return resources;
  }

  public Map<ResourceId, TextureImages.Size> imageSizes() {
    return imageSizes;
  }

  public List<String> diagnostics() {
    return diagnostics;
  }

  public record Subject(ClientSettings settings, boolean looted, boolean open) {
    public Subject {
      java.util.Objects.requireNonNull(settings);
    }
  }

  /**
   * Compile the complete supplied settings, then own the selected barrel's transitive plain-model
   * inputs. The caller owns source freshness, READ admission, and the borrowed local reader. This
   * method does not seed profiles, read or write settings, reload resources, or prepare GPU
   * objects.
   */
  public static BarrelPreviewInputs capture(
      ContainerTargets targets,
      Subject subject,
      ResourceAccess lower,
      LocalFiles local,
      Consumer<byte[]> nativeModelValidator,
      EmfSupport emf,
      boolean ordinaryChestIsolated,
      Runnable checkpoint)
      throws IOException {
    checkpoint.run();
    var sources = lower.guarded(checkpoint);
    var compiled =
        PresentationCompiler.compile(
            targets,
            subject.settings(),
            sources,
            local,
            nativeModelValidator,
            emf,
            ordinaryChestIsolated);
    checkpoint.run();
    var barrel =
        targets.barrels().stream()
            .filter(
                entry ->
                    entry.open() == subject.open()
                        && entry.material().equals(subject.looted() ? "opened_barrel" : "barrel"))
            .findFirst()
            .orElseThrow();
    var inputs =
        new ResourceInputs(
            PreviewResources.overlay(compiled.resources(), sources).guarded(checkpoint));
    var model = new BlockModels(inputs).resolve(barrel.target());
    byte[] modelBytes = model.toString().getBytes(StandardCharsets.UTF_8);
    nativeModelValidator.accept(modelBytes);
    var captured = new HashMap<ResourceId, byte[]>();
    var sizes = new HashMap<ResourceId, TextureImages.Size>();
    captured.put(barrel.target(), modelBytes);
    var images = new TextureImages();
    if (model.has("textures")) {
      var textures = model.getAsJsonObject("textures");
      for (var entry : textures.entrySet()) {
        checkpoint.run();
        String value = entry.getValue().getAsString();
        while (value.startsWith("#")) value = textures.get(value.substring(1)).getAsString();
        var name = BlockModels.resourceName(value);
        var image = new ResourceId(name.namespace(), "textures/" + name.path() + ".png");
        if (captured.containsKey(image)) continue;
        byte[] bytes = inputs.require(image).bytes();
        var size = images.validate(bytes);
        sizes.put(image, size);
        captured.put(image, bytes);
        var metadata = new ResourceId(image.namespace(), image.path() + ".mcmeta");
        inputs
            .find(metadata)
            .ifPresent(
                source -> {
                  byte[] data = source.bytes();
                  TextureImages.validateMetadata(data, size);
                  captured.put(metadata, data);
                });
      }
    }
    if (local != null) local.validateCurrent();
    checkpoint.run();
    return new BarrelPreviewInputs(
        subject,
        barrel,
        new FrozenResources(captured, FrozenResources.DEFAULT_LIMITS),
        sizes,
        compiled.status());
  }
}
