package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import design.inherent.reform.minecraft.client.ClientGraphicHost;
import design.inherent.reform.presentation.graphics.RenderLifetime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Passive shutdown evidence: cached completion/accounting only, with no native cleanup calls. */
public final class RendererShutdownEvidence {
  private static final AtomicReference<RendererShutdownEvidence> active = new AtomicReference<>();
  private final CompletableFuture<RenderLifetime.State> release;
  private final ClientGraphicHost host;
  private final Path settings;
  private final byte[] originalSettings;
  private final JsonObject request;
  private final Consumer<JsonObject> complete;
  private final BooleanSupplier workflowOpen, editorOpen;
  private final AtomicReference<JsonObject> boundary = new AtomicReference<>();

  private RendererShutdownEvidence(
      ClientGraphicHost host,
      java.util.concurrent.CompletionStage<RenderLifetime.State> released,
      Path settings,
      byte[] originalSettings,
      JsonObject request,
      BooleanSupplier workflowOpen,
      BooleanSupplier editorOpen,
      Consumer<JsonObject> complete) {
    this.host = host;
    release = released.toCompletableFuture();
    this.settings = settings;
    this.originalSettings = originalSettings.clone();
    this.request = request.deepCopy();
    this.complete = complete;
    this.workflowOpen = workflowOpen;
    this.editorOpen = editorOpen;
  }

  static void arm(
      ClientGraphicHost host,
      java.util.concurrent.CompletionStage<RenderLifetime.State> released,
      Path settings,
      byte[] originalSettings,
      JsonObject request,
      BooleanSupplier workflowOpen,
      BooleanSupplier editorOpen,
      Consumer<JsonObject> complete) {
    var evidence =
        new RendererShutdownEvidence(
            host,
            released,
            settings,
            originalSettings,
            request,
            workflowOpen,
            editorOpen,
            complete);
    if (!active.compareAndSet(null, evidence))
      throw new IllegalStateException("Renderer shutdown evidence is already armed");
    Runtime.getRuntime()
        .addShutdownHook(new Thread(evidence::recordJvmShutdown, "lootr-styled-shutdown-evidence"));
  }

  /** Called after the actual per-game renderer-close method returns; no renderer is invoked. */
  public static void rendererReturned(String name) {
    var evidence = active.get();
    if (evidence == null || evidence.boundary.get() != null) return;
    var sample = evidence.sample();
    sample.addProperty("boundary", name);
    evidence.boundary.compareAndSet(null, sample);
  }

  private JsonObject sample() {
    var result = new JsonObject();
    boolean released = false;
    result.addProperty("releaseCompleted", release.isDone());
    if (release.isDone()) {
      try {
        var state = release.join();
        released = state.released() && state.outstandingUses() == 0;
        result.addProperty("outstandingUses", state.outstandingUses());
        result.addProperty("released", state.released());
        state
            .releaseFailure()
            .ifPresent(error -> result.addProperty("releaseError", error.toString()));
      } catch (RuntimeException failed) {
        result.addProperty("releaseError", failed.toString());
      }
    }
    var nativeState = host.nativeOwnership();
    var sceneState = host.retainedOutput();
    result.addProperty("nativeBytes", nativeState.bytes());
    result.addProperty("nativeResources", nativeState.resources());
    result.addProperty("sceneBytes", sceneState.bytes());
    result.addProperty("sceneResources", sceneState.resources());
    boolean workflowClosed = !workflowOpen.getAsBoolean();
    boolean editorClosed = !editorOpen.getAsBoolean();
    result.addProperty("workflowClosed", workflowClosed);
    result.addProperty("editorClosed", editorClosed);
    result.addProperty(
        "passed",
        released
            && nativeState.bytes() == 0
            && nativeState.resources() == 0
            && sceneState.bytes() == 0
            && sceneState.resources() == 0);
    return result;
  }

  private void recordJvmShutdown() {
    var result = request.deepCopy();
    try {
      var returned = boundary.get();
      result.addProperty("rendererReturned", returned != null);
      if (returned != null) result.add("atRendererReturn", returned);
      var after = sample();
      result.add("afterShutdown", after);
      boolean unchanged = Arrays.equals(originalSettings, Files.readAllBytes(settings));
      result.addProperty("settingsUnchanged", unchanged);
      result.addProperty(
          "passed",
          unchanged
              && returned != null
              && returned.get("passed").getAsBoolean()
              && after.get("passed").getAsBoolean()
              && after.get("workflowClosed").getAsBoolean()
              && after.get("editorClosed").getAsBoolean());
    } catch (RuntimeException | java.io.IOException failed) {
      result.addProperty("passed", false);
      result.addProperty("error", failed.toString());
    }
    complete.accept(result);
  }
}
