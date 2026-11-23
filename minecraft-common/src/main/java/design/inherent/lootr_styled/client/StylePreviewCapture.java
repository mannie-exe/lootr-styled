package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.edit.DraftRevision;
import design.inherent.reform.minecraft.client.ClientEditor;
import design.inherent.reform.minecraft.client.ClientPreviewCapture;
import design.inherent.reform.operation.OperationDescriptor.EntryPoint;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.DraftPreviewInput;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Binds the library's actual READ capture to this domain's current resource generation. */
public final class StylePreviewCapture {
  private StylePreviewCapture() {}

  /** Frozen input only; native packs, editor actors and renderer leases are not retained here. */
  public record Snapshot(DraftPreviewInput<?> input, UUID resourceGeneration) {
    public Snapshot {
      Objects.requireNonNull(input);
      Objects.requireNonNull(resourceGeneration);
      if (input.candidate().definition() != StyleSettingsStore.DEFINITION
          || !(input.candidate().value() instanceof ClientSettings))
        throw new IllegalArgumentException(
            "Preview input is not the Lootr: Styled settings definition");
    }

    public ClientSettings settings() {
      return (ClientSettings) input.candidate().value();
    }
  }

  /** The source operation remains available even if later resource freshness refuses delivery. */
  public record Capture(ClientPreviewCapture<?> source, CompletionStage<Snapshot> completion) {
    public Capture {
      Objects.requireNonNull(source);
      Objects.requireNonNull(completion);
    }
  }

  public static Capture capture(ClientEditor<?> editor, EntryPoint entry, DraftRevision expected) {
    StylePreferences.requireClient();
    if (editor.actions().definition() != StyleSettingsStore.DEFINITION)
      throw new PolicyRefusal(
          "lootr_styled.preview.wrong_document",
          "Choose Lootr: Styled appearance settings before preparing a preview");
    var pack =
        NativeUi.activePack()
            .filter(value -> value.preparation().isPresent())
            .orElseThrow(
                () ->
                    new PolicyRefusal(
                        "preview.provider_unavailable",
                        "Resources are unavailable; reload resources before preparing a preview"));
    var generation = pack.id();
    var source = editor.capturePreview(entry, expected);
    var completion =
        source
            .completion()
            .thenApply(
                input -> {
                  StylePreferences.requireClient();
                  if (!resourceCurrent(generation))
                    throw new PolicyRefusal(
                        "preview.stale",
                        "Resources changed during capture; prepare the preview again");
                  var profiles = input.observedReferences().get(StyleSettingsStore.PROFILES);
                  if (profiles == null || !profiles.revision().equals(generation.toString()))
                    throw new PolicyRefusal(
                        "preview.stale",
                        "The profile catalogue changed; wait for resources to finish reloading, then prepare again");
                  return new Snapshot(input, generation);
                });
    return new Capture(source, completion);
  }

  public static boolean resourceCurrent(UUID generation) {
    StylePreferences.requireClient();
    return NativeUi.activePack()
        .filter(pack -> pack.id().equals(generation) && pack.preparation().isPresent())
        .isPresent();
  }
}
