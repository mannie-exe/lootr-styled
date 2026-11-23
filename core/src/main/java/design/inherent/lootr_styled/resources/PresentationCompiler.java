package design.inherent.lootr_styled.resources;

import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.LocalFiles;
import design.inherent.lootr_styled.profile.ProfileCatalogue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Compiles selected contributions into bytes; the native adapter owns publication and retirement.
 */
public final class PresentationCompiler {
  private PresentationCompiler() {}

  public static Result compile(
      ContainerTargets targets,
      ClientSettings settings,
      ResourceAccess nativeSources,
      LocalFiles local,
      Consumer<byte[]> nativeModelValidator) {
    return compile(
        targets, settings, nativeSources, local, nativeModelValidator, EmfSupport.ABSENT);
  }

  public static Result compile(
      ContainerTargets targets,
      ClientSettings settings,
      ResourceAccess nativeSources,
      LocalFiles local,
      Consumer<byte[]> nativeModelValidator,
      EmfSupport emf) {
    return compile(targets, settings, nativeSources, local, nativeModelValidator, emf, false);
  }

  public static Result compile(
      ContainerTargets targets,
      ClientSettings settings,
      ResourceAccess nativeSources,
      LocalFiles local,
      Consumer<byte[]> nativeModelValidator,
      EmfSupport emf,
      boolean ordinaryChestIsolated) {
    var budget = new ResourceInputs.Budget();
    var nativeInputs = new ResourceInputs(nativeSources, budget);
    var catalogue = ProfileCatalogue.discover(targets, nativeSources, local, budget);
    var status = new ArrayList<>(catalogue.diagnostics());
    var choices =
        catalogue.entries().stream()
            .map(
                e ->
                    new Choice(
                        e.profile().id(),
                        e.profile().title(),
                        e.description(),
                        java.util.Optional.of(
                            new ContributionCounts(
                                e.profile().models().size(), e.profile().artwork().size()))))
            .toList();
    if (!settings.enabled()) {
      status.add("Disabled");
      return new Result(FrozenResources.EMPTY, status, choices);
    }
    var images = new TextureImages();
    var models = selected(catalogue, settings.models(), "Models", status);
    var artwork = selected(catalogue, settings.artwork(), "Artwork", status);
    Map<ResourceId, byte[]> artworkOutput = Map.of();
    try {
      artworkOutput = artwork(targets, artwork, budget, images, status);
    } catch (ResourceInputs.LimitExceeded ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      status.add("Artwork profile " + artwork.profile().id() + " refused: " + ex.getMessage());
      status.add("Artwork: effective " + ClientSettings.VANILLA + " after profile refusal");
    }
    var defaultArtwork =
        artwork.profile().id().equals(ClientSettings.DEFAULT)
            ? artworkOutput
            : Map.<ResourceId, byte[]>of();
    Map<ResourceId, byte[]> output;
    try {
      output =
          models(
              targets,
              models,
              nativeInputs,
              budget,
              images,
              nativeModelValidator,
              status,
              defaultArtwork);
    } catch (ResourceInputs.LimitExceeded ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      status.add("Model profile " + models.profile().id() + " refused: " + ex.getMessage());
      status.add("Models: effective " + ClientSettings.VANILLA + " after profile refusal");
      models = catalogue.find(ClientSettings.VANILLA).orElseThrow();
      output =
          models(
              targets,
              models,
              nativeInputs,
              budget,
              images,
              nativeModelValidator,
              status,
              defaultArtwork);
    }
    output.putAll(artworkOutput);
    output.putAll(
        ChestPresentation.compile(
            targets,
            settings.fresh(),
            emf,
            models,
            nativeInputs,
            budget,
            status,
            ordinaryChestIsolated));
    BarrelWrapper.adapt(targets, nativeInputs, output, status);
    status.add("Fresh Objects requested: " + settings.fresh());
    return new Result(new FrozenResources(output, FrozenResources.DEFAULT_LIMITS), status, choices);
  }

  private static ProfileCatalogue.Entry selected(
      ProfileCatalogue catalogue, ResourceId id, String family, List<String> status) {
    var requested = catalogue.find(id);
    var effective = requested.orElseGet(() -> catalogue.find(ClientSettings.VANILLA).orElseThrow());
    status.add(
        family
            + ": requested "
            + id
            + ", effective "
            + effective.profile().id()
            + ", source "
            + effective.description());
    return effective;
  }

  private static Map<ResourceId, byte[]> models(
      ContainerTargets targets,
      ProfileCatalogue.Entry selected,
      ResourceInputs nativeInputs,
      ResourceInputs.Budget budget,
      TextureImages images,
      Consumer<byte[]> validator,
      List<String> status,
      Map<ResourceId, byte[]> defaultArtwork) {
    Set<ResourceId> admitted =
        targets.barrels().stream().map(ContainerTargets.Barrel::target).collect(Collectors.toSet());
    targets.chests().forEach(chest -> admitted.add(chest.target()));
    if (!admitted.containsAll(selected.profile().models().keySet())) {
      throw new IllegalArgumentException(
          "Model target is not an admitted barrel JSON or Lootr JEM slot");
    }
    var output = new HashMap<ResourceId, byte[]>();
    var selectedInputs = new ResourceInputs(selected.sources(), budget);
    var explicitModels = new BlockModels(selectedInputs);
    var inherited = new BlockModels(nativeInputs);
    boolean builtin = selected.profile().id().equals(ClientSettings.DEFAULT);
    for (var barrel : targets.barrels()) {
      var source = selected.profile().models().get(barrel.target());
      boolean explicit = source != null && !builtin;
      boolean packed =
          builtin && packedArtwork(barrel, defaultArtwork, selectedInputs, images, status);
      if (packed) source = BarrelCompiler.defaultModel(barrel, true);
      var current = nativeInputs.find(barrel.target());
      if (!explicit
          && current.isPresent()
          && current.orElseThrow().source().origin() != ResourceAccess.Origin.LOOTR) {
        status.add(
            barrel.target()
                + ": explicit resource kept ("
                + current.orElseThrow().source().description()
                + ")");
        continue;
      }
      if (current.isEmpty()) {
        status.add(barrel.target() + ": target unavailable, native fallback");
        continue;
      }
      try {
        JsonObject model =
            source == null ? inherited.resolve(barrel.vanilla()) : explicitModels.resolve(source);
        if (!explicit) BarrelCompiler.retarget(model, barrel);
        bindLocalTextures(
            model, selected.profile().id(), selectedInputs, images, output, explicit || packed);
        if (targets == ContainerTargets.LEGACY)
          model.addProperty(BarrelCompiler.GENERATED_MARKER, true);
        byte[] bytes = model.toString().getBytes(StandardCharsets.UTF_8);
        validator.accept(bytes);
        output.put(barrel.target(), bytes);
        status.add(
            barrel.target()
                + (explicit
                    ? ": selected profile"
                    : builtin
                        ? ": default geometry (" + (packed ? "packed" : "native") + " UVs)"
                        : ": inherited geometry/UV"));
      } catch (ResourceInputs.LimitExceeded ex) {
        throw ex;
      } catch (IllegalArgumentException ex) {
        if (explicit) throw ex;
        status.add(barrel.target() + ": native fallback (" + ex.getMessage() + ")");
      }
    }
    return output;
  }

  private static boolean packedArtwork(
      ContainerTargets.Barrel barrel,
      Map<ResourceId, byte[]> artwork,
      ResourceInputs inputs,
      TextureImages images,
      List<String> status) {
    if (!BarrelCompiler.matchesDefaultArtwork(barrel, artwork)) return false;
    var source = BarrelCompiler.defaultSurface(barrel);
    try {
      var size = images.validate(inputs.require(source).bytes());
      var metadata = inputs.find(new ResourceId(source.namespace(), source.path() + ".mcmeta"));
      if (metadata.isPresent())
        TextureImages.validateMetadata(metadata.orElseThrow().bytes(), size);
      return true;
    } catch (ResourceInputs.LimitExceeded ex) {
      throw ex;
    } catch (IllegalArgumentException ex) {
      status.add(
          barrel.target() + ": Default surface unavailable; native UVs (" + ex.getMessage() + ")");
      return false;
    }
  }

  private static void bindLocalTextures(
      JsonObject model,
      ResourceId profile,
      ResourceInputs inputs,
      TextureImages images,
      Map<ResourceId, byte[]> output,
      boolean explicit) {
    if (!model.has("textures")) return;
    var textures = model.getAsJsonObject("textures");
    for (var entry : textures.entrySet()) {
      String value = entry.getValue().getAsString();
      if (value.startsWith("#")) continue;
      ResourceId reference = BlockModels.resourceName(value);
      var asset = new ResourceId(reference.namespace(), "textures/" + reference.path() + ".png");
      var captured = inputs.find(asset);
      if (captured.isEmpty()) {
        if (explicit) throw new IllegalArgumentException("Missing model texture " + asset);
        continue;
      }
      byte[] bytes = captured.orElseThrow().bytes();
      if (explicit) images.validate(bytes);
      if (captured.orElseThrow().source().origin() != ResourceAccess.Origin.LOCAL) continue;
      var size = images.validate(bytes);
      String path =
          "block/generated/"
              + TextureImages.hash(profile.toString().getBytes(StandardCharsets.UTF_8))
              + "/"
              + TextureImages.hash(asset.toString().getBytes(StandardCharsets.UTF_8));
      var target = new ResourceId("lootr_styled", "textures/" + path + ".png");
      output.put(target, bytes);
      var metadata = inputs.find(new ResourceId(asset.namespace(), asset.path() + ".mcmeta"));
      if (metadata.isPresent()) {
        byte[] meta = metadata.orElseThrow().bytes();
        TextureImages.validateMetadata(meta, size);
        output.put(new ResourceId(target.namespace(), target.path() + ".mcmeta"), meta);
      }
      textures.addProperty(entry.getKey(), "lootr_styled:" + path);
    }
  }

  private static Map<ResourceId, byte[]> artwork(
      ContainerTargets targets,
      ProfileCatalogue.Entry selected,
      ResourceInputs.Budget budget,
      TextureImages images,
      List<String> status) {
    var output = new HashMap<ResourceId, byte[]>();
    var inputs = new ResourceInputs(selected.sources(), budget);
    for (var entry : selected.profile().artwork().entrySet()) {
      var target = entry.getKey();
      if (!target.path().endsWith(".png")) continue;
      if (!targets.artwork().contains(target) || !entry.getValue().path().endsWith(".png"))
        throw new IllegalArgumentException("Unsupported artwork target or source");
      if (selected.profile().id().equals(ClientSettings.DEFAULT)) {
        var current = inputs.find(target);
        if (current.isPresent()
            && current.orElseThrow().source().origin() != ResourceAccess.Origin.LOOTR) {
          status.add(
              target
                  + ": explicit resource kept ("
                  + current.orElseThrow().source().description()
                  + ")");
          continue;
        }
      }
      byte[] bytes = inputs.require(entry.getValue()).bytes();
      var size = images.validate(bytes);
      output.put(target, bytes);
      var targetMeta = new ResourceId(target.namespace(), target.path() + ".mcmeta");
      var explicitMeta = selected.profile().artwork().get(targetMeta);
      var meta =
          inputs.find(
              explicitMeta != null
                  ? explicitMeta
                  : new ResourceId(
                      entry.getValue().namespace(), entry.getValue().path() + ".mcmeta"));
      if (explicitMeta != null && meta.isEmpty())
        throw new IllegalArgumentException("Missing artwork metadata");
      if (meta.isPresent()) {
        byte[] metadata = meta.orElseThrow().bytes();
        TextureImages.validateMetadata(metadata, size);
        output.put(targetMeta, metadata);
      }
    }
    for (var target : selected.profile().artwork().keySet()) {
      if (target.path().endsWith(".png")) continue;
      if (!target.path().endsWith(".png.mcmeta"))
        throw new IllegalArgumentException("Unsupported artwork metadata target");
      var image =
          new ResourceId(
              target.namespace(), target.path().substring(0, target.path().length() - 7));
      if (!output.containsKey(image))
        throw new IllegalArgumentException("Artwork metadata requires its PNG in the same profile");
    }
    return output;
  }

  /** Declared entries only; these counts do not assert resource validity or live application. */
  public record ContributionCounts(int models, int artwork) {
    public ContributionCounts {
      if (models < 0
          || artwork < 0
          || (long) models + artwork
              > design.inherent.lootr_styled.profile.StyleProfile.MAX_MAPPINGS)
        throw new IllegalArgumentException("Invalid declared profile contribution counts");
    }
  }

  public record Choice(
      ResourceId id,
      String title,
      String source,
      java.util.Optional<ContributionCounts> declarations) {
    /** Older producers may omit facts; unknown counts must never be presented as zero. */
    public Choice(ResourceId id, String title, String source) {
      this(id, title, source, java.util.Optional.empty());
    }

    public Choice {
      java.util.Objects.requireNonNull(id);
      java.util.Objects.requireNonNull(title);
      java.util.Objects.requireNonNull(source);
      java.util.Objects.requireNonNull(declarations);
    }
  }

  public record Result(FrozenResources resources, List<String> status, List<Choice> choices) {
    public Result {
      status = status.stream().limit(256).map(PresentationCompiler::safe).toList();
      choices = List.copyOf(choices);
    }
  }

  private static String safe(String text) {
    var out = new StringBuilder();
    for (int i = 0; i < text.length() && out.length() < 512; i++)
      if (!Character.isISOControl(text.charAt(i))) out.append(text.charAt(i));
    return out.toString();
  }
}
