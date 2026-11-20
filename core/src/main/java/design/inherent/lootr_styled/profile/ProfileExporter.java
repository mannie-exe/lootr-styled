package design.inherent.lootr_styled.profile;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.resources.BarrelCompiler;
import design.inherent.lootr_styled.resources.BlockModels;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.JsonDocuments;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Explicit local export, without artwork, native overrides or replacement of existing drafts. */
public final class ProfileExporter {
  private ProfileExporter() {}

  public static Path exportDefault(
      ClientConfiguration configuration, ContainerTargets targets, byte[] model)
      throws IOException {
    configuration.seed();
    Path root = configuration.root();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Invalid profile root");
    var parsed = JsonDocuments.object(model);
    BlockModels.validate(parsed);
    Path destination = root.resolve("exported-default");
    Files.createDirectory(destination);
    var mappings = new JsonObject();
    for (var barrel : targets.barrels()) {
      var copy = parsed.deepCopy();
      BarrelCompiler.retarget(copy, barrel);
      String leaf = barrel.target().path().substring(barrel.target().path().lastIndexOf('/') + 1);
      Path output = destination.resolve("assets/lootr_styled_export/models/block/" + leaf);
      Files.createDirectories(output.getParent());
      write(output, copy);
      mappings.addProperty(barrel.target().toString(), "lootr_styled_export:models/block/" + leaf);
    }
    var profile = new JsonObject();
    profile.addProperty("format", 1);
    profile.addProperty("title", "Exported Default barrel geometry");
    profile.add("models", mappings);
    profile.add("artwork", new JsonObject());
    // Publish metadata last: incomplete export data is never an admitted catalogue entry.
    write(destination.resolve("profile.json"), profile);
    return destination;
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
