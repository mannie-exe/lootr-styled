package design.inherent.lootr_styled.profile;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.resources.BarrelCompiler;
import design.inherent.lootr_styled.resources.BlockModels;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.JsonDocuments;
import design.inherent.lootr_styled.resources.ResourceAccess;
import design.inherent.lootr_styled.resources.ResourceInputs;
import design.inherent.lootr_styled.resources.TextureImages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;

/** Local editing copies. Input assets are captured before metadata is published. */
public final class ProfileExporter {
  private ProfileExporter() {}

  public static Path exportDefault(ProfileDirectory configuration, ContainerTargets targets)
      throws IOException {
    return exportDefault(configuration, targets, () -> {});
  }

  public static Path exportDefault(
      ProfileDirectory configuration, ContainerTargets targets, Runnable current)
      throws IOException {
    current.run();
    configuration.seed();
    Path root = configuration.root();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Invalid profile root");
    var captured = new LinkedHashMap<ContainerTargets.Barrel, JsonObject>();
    for (var barrel : targets.barrels()) {
      var parsed = JsonDocuments.object(BarrelCompiler.bundledModel(barrel));
      BlockModels.validate(parsed);
      captured.put(barrel, parsed);
    }
    Path destination = root.resolve("exported-default");
    current.run();
    Files.createDirectory(destination);
    var mappings = new JsonObject();
    for (var barrel : targets.barrels()) {
      current.run();
      var copy = captured.get(barrel).deepCopy();
      BarrelCompiler.retarget(copy, barrel);
      String leaf = barrel.target().path().substring(barrel.target().path().lastIndexOf('/') + 1);
      Path output = destination.resolve("assets/lootr_styled_export/models/block/" + leaf);
      Files.createDirectories(output.getParent());
      write(output, copy);
      mappings.addProperty(barrel.target().toString(), "lootr_styled_export:models/block/" + leaf);
    }
    var profile = new JsonObject();
    profile.addProperty("format", 1);
    profile.addProperty("title", "Barrel model copy");
    profile.add("models", mappings);
    profile.add("artwork", new JsonObject());
    // Publish metadata last: incomplete export data is never an admitted catalogue entry.
    current.run();
    write(destination.resolve("profile.json"), profile);
    return destination;
  }

  /** The source adapter must preserve the native image/metadata ownership relationship. */
  public static Path exportArtwork(
      ProfileDirectory configuration, ContainerTargets targets, ResourceAccess sources)
      throws IOException {
    return exportArtwork(configuration, targets, sources, () -> {});
  }

  public static Path exportArtwork(
      ProfileDirectory configuration,
      ContainerTargets targets,
      ResourceAccess sources,
      Runnable current)
      throws IOException {
    current.run();
    var inputs = new ResourceInputs(sources);
    var images = new TextureImages();
    var assets = new LinkedHashMap<ResourceId, byte[]>();
    var mappings = new JsonObject();
    var models = new JsonObject();
    var origins = new JsonObject();
    for (var id :
        targets.artwork().stream()
            .sorted(java.util.Comparator.comparing(ResourceId::toString))
            .toList()) {
      var captured = inputs.require(id);
      byte[] bytes = captured.bytes();
      var size = images.validate(bytes);
      assets.put(id, bytes);
      mappings.addProperty(id.toString(), id.toString());
      origin(origins, id, captured);
      var metaId = new ResourceId(id.namespace(), id.path() + ".mcmeta");
      var meta = inputs.find(metaId);
      if (meta.isPresent()) {
        byte[] metadata = meta.orElseThrow().bytes();
        TextureImages.validateMetadata(metadata, size);
        assets.put(metaId, metadata);
        mappings.addProperty(metaId.toString(), metaId.toString());
        origin(origins, metaId, meta.orElseThrow());
      }
    }
    // Keep bindings for auxiliary barrel textures, such as direction-specific shading.
    var blockModels = new BlockModels(inputs);
    for (var barrel : targets.barrels()) {
      if (inputs.find(barrel.target()).isEmpty()) continue;
      var model = blockModels.resolve(barrel.target());
      if (!model.has("textures")) continue;
      boolean auxiliary = false;
      for (var entry : model.getAsJsonObject("textures").entrySet()) {
        String name = entry.getValue().getAsString();
        if (name.startsWith("#")) continue;
        var texture = BlockModels.resourceName(name);
        var id = new ResourceId(texture.namespace(), "textures/" + texture.path() + ".png");
        if (targets.artwork().contains(id)) continue;
        auxiliary = true;
        var captured = inputs.require(id);
        var size = images.validate(captured.bytes());
        assets.put(id, captured.bytes());
        origin(origins, id, captured);
        var metaId = new ResourceId(id.namespace(), id.path() + ".mcmeta");
        var meta = inputs.find(metaId);
        if (meta.isPresent()) {
          TextureImages.validateMetadata(meta.orElseThrow().bytes(), size);
          assets.put(metaId, meta.orElseThrow().bytes());
          origin(origins, metaId, meta.orElseThrow());
        }
      }
      if (auxiliary) {
        var id = new ResourceId("lootr_styled_export", barrel.target().path());
        assets.put(id, model.toString().getBytes(StandardCharsets.UTF_8));
        models.addProperty(barrel.target().toString(), id.toString());
        origin(origins, barrel.target(), inputs.require(barrel.target()));
      }
    }
    configuration.seed();
    Path root = configuration.root();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Invalid profile root");
    current.run();
    Path destination = copyDirectory(root, "artwork-copy");
    for (var entry : assets.entrySet()) {
      current.run();
      var id = entry.getKey();
      Path output = destination.resolve(Path.of("assets", id.namespace(), id.path()));
      Files.createDirectories(output.getParent());
      Files.write(
          output, entry.getValue(), StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS);
    }
    var provenance = new JsonObject();
    provenance.addProperty("note", "Local editing copy. Original artwork licenses still apply.");
    provenance.add("resources", origins);
    write(destination.resolve("SOURCES.json"), provenance);
    var profile = new JsonObject();
    profile.addProperty("format", 1);
    String suffix = destination.getFileName().toString().substring("artwork-copy".length());
    profile.addProperty("title", "Artwork copy" + suffix.replace('-', ' '));
    profile.add("models", models);
    profile.add("artwork", mappings);
    current.run();
    write(destination.resolve("profile.json"), profile);
    return destination;
  }

  private static void origin(JsonObject origins, ResourceId id, ResourceInputs.Captured captured) {
    var entry = new JsonObject();
    entry.addProperty("source", captured.source().description());
    entry.addProperty("sha256", TextureImages.hash(captured.bytes()));
    origins.add(id.toString(), entry);
  }

  private static Path copyDirectory(Path root, String base) throws IOException {
    for (int copy = 1; copy <= 64; copy++) {
      Path destination = root.resolve(base + (copy == 1 ? "" : "-" + copy));
      try {
        return Files.createDirectory(destination);
      } catch (java.nio.file.FileAlreadyExistsException existing) {
        // Keep previous copies and unfinished exports.
      }
    }
    throw new IOException("Too many artwork copies. Rename or remove an older copy.");
  }

  private static void write(Path file, JsonObject value) throws IOException {
    Files.writeString(
        file,
        new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value) + "\n",
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        LinkOption.NOFOLLOW_LINKS);
  }
}
