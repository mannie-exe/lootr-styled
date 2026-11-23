package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.LocalFiles;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Frozen effective artwork and declarative rig for one chest, with no native renderer handles. */
public final class ChestPreviewInputs {
  private final Subject subject;
  private final ContainerTargets.Chest chest;
  private final ResourceId texture;
  private final Optional<ResourceId> rig;
  private final FrozenResources resources;
  private final List<String> diagnostics;

  private ChestPreviewInputs(
      Subject subject,
      ContainerTargets.Chest chest,
      ResourceId texture,
      Optional<ResourceId> rig,
      FrozenResources resources,
      List<String> diagnostics) {
    this.subject = subject;
    this.chest = chest;
    this.texture = texture;
    this.rig = rig;
    this.resources = resources;
    this.diagnostics = List.copyOf(diagnostics);
  }

  /** Lid progress is an explicit static pose, not an animation clock or a live block entity. */
  public record Subject(ClientSettings settings, boolean trapped, boolean looted, float lid) {
    public Subject {
      java.util.Objects.requireNonNull(settings);
      if (!Float.isFinite(lid) || lid < 0 || lid > 1)
        throw new IllegalArgumentException("Chest lid progress must be between zero and one");
    }
  }

  public Subject subject() {
    return subject;
  }

  public ContainerTargets.Chest chest() {
    return chest;
  }

  public ResourceId texture() {
    return texture;
  }

  public Optional<ResourceId> rig() {
    return rig;
  }

  public FrozenResources resources() {
    return resources;
  }

  public List<String> diagnostics() {
    return diagnostics;
  }

  /** Facts for the shared provider's capability/pose disclosure, never a live-render claim. */
  public record PoseFacts(
      float lidProgress, boolean evaluatesRigAnimations, boolean usesWorldState) {}

  public PoseFacts poseFacts() {
    return new PoseFacts(subject.lid(), false, false);
  }

  /**
   * Compile the full supplied settings and freeze the selected effective chest dependencies. The
   * caller owns READ admission and source freshness. This does not evaluate rig animations, create
   * native models, seed profiles, read/write settings, or publish resources.
   */
  public static ChestPreviewInputs capture(
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
    if (emf.installed() && !emf.admitted())
      throw new IllegalArgumentException(
          "The installed chest renderer is not admitted for isolated preparation: " + emf.reason());
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
    var effective = PreviewResources.overlay(compiled.resources(), sources).guarded(checkpoint);
    var input = new ResourceInputs(effective);
    var chest = targets.chests().get(subject.trapped() ? 1 : 0);
    var texture = targets.chestArtwork(subject.trapped(), subject.looted());
    var captured = new HashMap<ResourceId, byte[]>();
    byte[] image = input.require(texture).bytes();
    var size = new TextureImages().validate(image);
    captured.put(texture, image);
    var metadata = new ResourceId(texture.namespace(), texture.path() + ".mcmeta");
    input
        .find(metadata)
        .ifPresent(
            source -> {
              TextureImages.validateMetadata(source.bytes(), size);
              captured.put(metadata, source.bytes());
            });
    Optional<ResourceId> rig = Optional.empty();
    if (emf.admitted()) {
      ResourceId selected = chest.target();
      if (input.find(selected).isEmpty())
        selected =
            new ResourceId(
                selected.namespace(), selected.path().replaceFirst("^emf/", "optifine/"));
      if (input.find(selected).isPresent()) {
        var json = JsonDocuments.object(input.require(selected).bytes());
        // The runtime's empty CEM declaration explicitly retains native geometry.
        if (!json.has("models") || !json.get("models").isJsonArray())
          throw new IllegalArgumentException("The effective chest rig has no model-part list");
        if (json.has("texture"))
          throw new IllegalArgumentException(
              "The effective chest rig replaces its texture outside the captured artwork mapping");
        if (!json.getAsJsonArray("models").isEmpty()) {
          captured.putAll(RigAdapter.compile(input, selected, chest.target(), false));
          rig = Optional.of(chest.target());
        }
      }
    }
    if (local != null) local.validateCurrent();
    checkpoint.run();
    return new ChestPreviewInputs(
        subject,
        chest,
        texture,
        rig,
        new FrozenResources(captured, FrozenResources.DEFAULT_LIMITS),
        compiled.status());
  }
}
