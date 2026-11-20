package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.ClientConfiguration;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.LocalFiles;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.FrozenResources;
import design.inherent.lootr_styled.resources.PresentationCompiler;
import design.inherent.lootr_styled.resources.ResourceAccess;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BlockModel;

public final class ClientRuntime {
  private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
  private static volatile ClientConfiguration config;
  private static volatile java.util.function.Supplier<java.util.Optional<String>> emfVersion =
      java.util.Optional::empty;
  private static volatile List<String> status = List.of("Not reloaded");
  private static volatile List<PresentationCompiler.Choice> choices = List.of();

  private ClientRuntime() {}

  public static void initialize(Path configDirectory) {
    initialize(configDirectory, java.util.Optional::empty);
  }

  public static void initialize(
      Path configDirectory,
      java.util.function.Supplier<java.util.Optional<String>> optionalEmfVersion) {
    config = new ClientConfiguration(configDirectory);
    emfVersion = java.util.Objects.requireNonNull(optionalEmfVersion);
  }

  public static List<String> status() {
    return status;
  }

  public static List<PresentationCompiler.Choice> choices() {
    return choices;
  }

  public static ContainerTargets targets() {
    return ContainerTargets.LEGACY;
  }

  public static ClientConfiguration configuration() {
    return config;
  }

  static FrozenResources prepare(ResourceAccess access) {
    var notes = new ArrayList<String>();
    LocalFiles local = null;
    try {
      ClientConfiguration files = config;
      ClientSettings settings = files == null ? ClientSettings.DEFAULTS : files.load();
      if (files != null) {
        try {
          local = LocalFiles.open(files.root());
        } catch (IOException ex) {
          notes.add("Local profiles unavailable: " + ex.getMessage());
        }
      }
      var result =
          PresentationCompiler.compile(
              ContainerTargets.LEGACY,
              settings,
              access,
              local,
              ClientRuntime::validateModel,
              settings.enabled()
                  ? design.inherent.lootr_styled.resources.EmfSupport.inspect(emfVersion.get())
                  : design.inherent.lootr_styled.resources.EmfSupport.ABSENT);
      if (local != null) local.validateCurrent();
      notes.addAll(result.status());
      status = List.copyOf(notes);
      choices = result.choices();
      LOGGER.info(
          "Lootr: Styled prepared {} resources: {}", result.resources().ids().size(), status);
      return result.resources();
    } catch (IOException | IllegalArgumentException ex) {
      status =
          List.of(
              "Native fallback: invalid settings or inputs ("
                  + ex.getClass().getSimpleName()
                  + ")");
      choices = List.of();
      LOGGER.warn("Lootr: Styled uses native resources: {}", status);
      return FrozenResources.EMPTY;
    } finally {
      if (local != null) {
        try {
          local.close();
        } catch (IOException ex) {
          LOGGER.warn("Could not close local profile reader", ex);
        }
      }
    }
  }

  private static void validateModel(byte[] bytes) {
    try {
      BlockModel.fromString(new String(bytes, StandardCharsets.UTF_8));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Native block-model parser refused output", ex);
    }
  }
}
