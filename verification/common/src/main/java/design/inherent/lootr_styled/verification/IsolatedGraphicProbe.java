package design.inherent.lootr_styled.verification;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.lootr_styled.client.ClientRuntime;
import design.inherent.lootr_styled.client.NativeUi;
import design.inherent.lootr_styled.client.StylePreviewCapture;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.LocalFiles;
import design.inherent.lootr_styled.resources.BarrelPreviewInputs;
import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.lootr_styled.resources.EmfSupport;
import design.inherent.reform.minecraft.client.ClientMeshGraphic;
import design.inherent.reform.presentation.graphics.GraphicCamera;
import design.inherent.reform.presentation.graphics.GraphicLedger;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import java.nio.file.Files;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Test-only domain draw witness, not the production provider, scheduler, or configuration UI. */
final class IsolatedGraphicProbe {
  private final ClientSettings settings;
  private final java.util.UUID generation;
  private final byte[] originalSettings;
  private final BooleanSupplier current;
  private final Screen parent;
  private final Object pack;
  private final GraphicLimits limits =
      new GraphicLimits(
          16 * 1024 * 1024, 16 * 1024 * 1024, 16 * 1024 * 1024, 256, 128 * 1024 * 1024);
  private final GraphicLedger ledger = new GraphicLedger(limits);
  private final JsonArray subjects = new JsonArray();
  private ClientMeshGraphic graphic;
  private JsonObject subject;
  private int index, ticks;
  private boolean closing, used;
  private volatile boolean screenshot;

  IsolatedGraphicProbe() throws Exception {
    this.pack = PreviewProbeAccess.activePack();
    this.generation = PreviewProbeAccess.generation(pack);
    this.originalSettings =
        Files.readAllBytes(ClientRuntime.configuration().root().resolve("settings.json"));
    this.settings = ClientSettings.parse(originalSettings);
    this.current = () -> StylePreviewCapture.resourceCurrent(generation);
    this.parent = NativeUi.current();
  }

  JsonObject tick(Minecraft mc) throws Exception {
    if (!current.getAsBoolean())
      throw new IllegalStateException("The fixture's captured source changed");
    if (graphic == null) {
      if (index == 4) {
        if (!java.util.Arrays.equals(
            originalSettings,
            Files.readAllBytes(ClientRuntime.configuration().root().resolve("settings.json"))))
          throw new IllegalStateException("The low-level domain witness changed settings");
        var result = new JsonObject();
        result.addProperty("settingsUnchanged", true);
        result.add("subjects", subjects);
        result.addProperty("peakKnownNativeBytes", ledger.state().peakBytes());
        result.addProperty("peakNativeObjects", ledger.state().peakResources());
        result.addProperty("remainingKnownNativeBytes", ledger.state().bytes());
        result.addProperty("remainingNativeObjects", ledger.state().resources());
        result.addProperty(
            "materialPolicy",
            "Fixture-declared cutout0.1/back/nearest/clamp; no automatic world-material policy claim");
        result.addProperty(
            "scope",
            "Actual packaged domain geometry/still textures in shared viewport; static rig reference only. No production host, native-input freshness, worker scheduling, animation, or world simulation qualification");
        return result;
      }
      prepare(mc);
      ticks = 0;
      closing = used = screenshot = false;
      return null;
    }
    if (graphic.failure().isPresent())
      throw new IllegalStateException(
          "The shared native viewport refused the domain scene", graphic.failure().orElseThrow());
    used |= graphic.state().outstandingUses() > 0;
    if (!closing) {
      if (++ticks < 24) return null;
      if (ticks == 24) {
        NativeWorld.screenshot(mc, "isolated-preview-" + index, () -> screenshot = true);
        return null;
      }
      if (!screenshot) return null;
      if (!used)
        throw new IllegalStateException("The domain viewport never acquired a native render use");
      graphic.close();
      NativeUi.open(parent);
      closing = true;
      ticks = 0;
      return null;
    }
    if (!graphic.state().released()) {
      if (++ticks > 120)
        throw new IllegalStateException(
            "The domain viewport did not retire its native uses: " + graphic.state());
      return null;
    }
    if (ledger.state().bytes() != 0 || ledger.state().resources() != 0)
      throw new IllegalStateException(
          "The domain viewport retained native allocations after confirmed release");
    subject.addProperty("nativeUseObserved", used);
    subject.addProperty("actualReleaseConfirmed", true);
    subjects.add(subject);
    graphic = null;
    index++;
    return null;
  }

  private void prepare(Minecraft mc) throws Exception {
    if (!PreviewProbeAccess.generation(pack).equals(generation))
      throw new IllegalStateException("The domain capture and resource generation differ");
    boolean chest = index < 2, open = index % 2 == 1;
    boolean emf =
        !Files.readString(mc.gameDirectory.toPath().resolve("lootr-styled-probe-mode.txt"))
            .strip()
            .equals("native");
    var support = emf ? EmfSupport.inspect(Optional.of("3.3.12")) : EmfSupport.ABSENT;
    long start = System.nanoTime();
    PreviewProbeAccess.GraphicInput prepared;
    try (var local = LocalFiles.open(ClientRuntime.configuration().root())) {
      if (chest) {
        var source =
            ChestPreviewInputs.capture(
                ClientRuntime.targets(),
                new ChestPreviewInputs.Subject(settings, false, open, open ? 1 : 0),
                PreviewProbeAccess.lower(pack),
                local,
                PreviewProbeAccess::validateModel,
                support,
                PreviewProbeAccess.ordinaryChestIsolated(),
                () -> {});
        prepared = PreviewProbeAccess.chestScene(source, limits, ledger);
      } else {
        var source =
            BarrelPreviewInputs.capture(
                ClientRuntime.targets(),
                new BarrelPreviewInputs.Subject(settings, open, open),
                PreviewProbeAccess.lower(pack),
                local,
                PreviewProbeAccess::validateModel,
                support,
                PreviewProbeAccess.ordinaryChestIsolated(),
                () -> {});
        prepared = PreviewProbeAccess.barrelScene(source, limits, ledger);
      }
    }
    subject = new JsonObject();
    subject.addProperty("container", chest ? "chest" : "barrel");
    subject.addProperty("looted", open);
    subject.addProperty("requestedOpen", open);
    subject.addProperty("nativeLidHasGeometry", prepared.nativeLidHasGeometry());
    subject.addProperty("nativeLockHasGeometry", prepared.nativeLockHasGeometry());
    subject.addProperty("triangles", prepared.scene().mesh().triangles().size());
    subject.addProperty("textures", prepared.scene().textures().size());
    subject.addProperty("cpuPreparationNanos", System.nanoTime() - start);
    graphic = ClientMeshGraphic.prepare(prepared.scene(), ledger, 8, current);
    var view =
        graphic.widget(
            Component.literal("Isolated " + (chest ? "chest" : "barrel") + " reference pose"),
            () -> GraphicCamera.STANDARD);
    NativeUi.open(
        new Screen(Component.literal("Packaged domain preview witness")) {
          @Override
          protected void init() {
            view.setPosition(12, 12);
            view.setWidth(Math.max(1, width - 24));
            view.setHeight(Math.max(1, height - 24));
            addRenderableWidget(view);
          }
        });
  }
}
