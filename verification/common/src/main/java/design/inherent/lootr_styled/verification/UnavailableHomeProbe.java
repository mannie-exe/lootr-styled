package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreferences;
import design.inherent.reform.minecraft.client.ClientDestination;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientFileSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/** Actual invalid startup file and native Home; never edits, repairs, or admits its owner. */
final class UnavailableHomeProbe {
  private final Screen parent;
  private final ClientEditingWorkflow workflow;
  private final Path file;
  private final byte[] bytes;
  private final BasicFileAttributes attributes;
  private final long reads = UiCostAudit.settingsReads();
  private final long discoveries = UiCostAudit.discoveries();
  private final long preparations = UiCostAudit.preparations();
  private final JsonObject result = new JsonObject();
  private int stage;
  private boolean captured;

  UnavailableHomeProbe(Minecraft mc) throws Exception {
    file = ClientRuntime.configuration().root().resolve("settings.json");
    bytes = Files.readAllBytes(file);
    attributes = Files.readAttributes(file, BasicFileAttributes.class);
    var expected =
        Files.readString(mc.gameDirectory.toPath().resolve(".lootr-styled-unavailable-home"))
            .strip();
    if (!expected.equals(
            java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
        || !Arrays.equals(bytes, "{\"format\":".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
      throw new IllegalStateException("Unavailable fixture must retain its exact invalid file");
    requireUnavailable();
    if (reads < 1) throw new IllegalStateException("Settings read observer did not see startup");
    result.addProperty("startupSettingsReads", reads);
    result.addProperty("startupCatalogueDiscoveries", discoveries);
    result.addProperty("initialLifecycle", StylePreferences.settings().status().lifecycle().name());
    var codes = new JsonArray();
    StylePreferences.settings()
        .status()
        .report()
        .issues()
        .forEach(issue -> codes.add(issue.code()));
    result.add("initialIssueCodes", codes);
    parent = NativeUi.current();
    workflow = StylePreferences.open(parent);
    NativeUi.open(workflow.screen());
    UiCostAudit.watch(workflow.screen(), "unopened-home");
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (stage == 0) {
      if (UiCostAudit.samples() < 8 || !NativeBindings.unobscured(mc)) return null;
      if (NativeUi.current() != workflow.screen()
          || !(workflow.navigation().current().orElseThrow() instanceof ClientDestination.Home))
        throw new IllegalStateException("Unavailable source prevented the real Home destination");
      unchanged();
      var documents = workflow.documents();
      if (documents.size() != 1
          || documents.getFirst().opened()
          || documents.getFirst().available()
          || documents.getFirst().busy()
          || !documents.getFirst().summary().getString().equals("Settings unavailable"))
        throw new IllegalStateException("Home invented source readiness or admitted its owner");
      var buttons = new JsonArray();
      UiProbe.children(workflow.screen()).stream()
          .filter(Button.class::isInstance)
          .map(Button.class::cast)
          .forEach(button -> buttons.add(button.getMessage().getString()));
      if (!buttons.contains(new com.google.gson.JsonPrimitive("Settings"))
          || !buttons.contains(new com.google.gson.JsonPrimitive("Profiles")))
        throw new IllegalStateException("Home lost the actual Settings or Profiles destination");
      result.add("nativeButtons", buttons);
      result.addProperty("cachedUnavailableReason", documents.getFirst().summary().getString());
      result.addProperty("openedDocuments", 0);
      result.addProperty("availableDocuments", 0);
      var paint = UiCostAudit.finish();
      if (paint.get("otherResourceContentLookupsInCallback").getAsLong() != 0
          || paint.get("nativeMenuBackgroundLookupsInCallback").getAsLong() > 1
          || paint.get("providerEntriesInCallback").getAsLong() != 0)
        throw new IllegalStateException("Unavailable Home performed resource work: " + paint);
      result.add("paint", paint);
      NativeWorld.screenshot(mc, "unavailable-home", () -> captured = true);
      stage = 1;
    } else if (stage == 1) {
      if (!captured) return null;
      unchanged();
      NativeBindings.press(UiProbe.button(workflow.screen(), "Done"));
      stage = 2;
    } else {
      if (workflow.isOpen() || NativeUi.current() != parent) return null;
      unchanged();
      result.addProperty("closedCleanly", true);
      result.addProperty("sameInvalidFileAndIdentity", true);
      result.addProperty("settingsReadsDuringHome", UiCostAudit.settingsReads() - reads);
      result.addProperty("catalogueDiscoveriesDuringHome", UiCostAudit.discoveries() - discoveries);
      result.addProperty("providerEntriesDuringHome", UiCostAudit.preparations() - preparations);
      result.addProperty("finalLifecycle", StylePreferences.settings().status().lifecycle().name());
      return result;
    }
    return null;
  }

  JsonObject observations() {
    return result;
  }

  private static void requireUnavailable() {
    var status = StylePreferences.settings().status();
    if (status.lifecycle() != ClientFileSettings.Lifecycle.UNAVAILABLE
        || status.effective().isPresent()
        || status.report().valid())
      throw new IllegalStateException("The actual startup file did not remain unavailable");
  }

  private void unchanged() throws Exception {
    requireUnavailable();
    var now = Files.readAttributes(file, BasicFileAttributes.class);
    if (!Arrays.equals(bytes, Files.readAllBytes(file))
        || !attributes.lastModifiedTime().equals(now.lastModifiedTime())
        || !java.util.Objects.equals(attributes.fileKey(), now.fileKey())
        || UiCostAudit.settingsReads() != reads
        || UiCostAudit.discoveries() != discoveries
        || UiCostAudit.preparations() != preparations)
      throw new IllegalStateException(
          "Unavailable Home reread, discovered, prepared, or changed its file");
  }
}
