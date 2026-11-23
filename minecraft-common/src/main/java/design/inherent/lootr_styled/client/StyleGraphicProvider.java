package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.lootr_styled.profile.LocalFiles;
import design.inherent.lootr_styled.resources.BarrelPreviewInputs;
import design.inherent.lootr_styled.resources.BarrelPreviewMesh;
import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.lootr_styled.resources.ContainerTargets;
import design.inherent.lootr_styled.resources.EmfSupport;
import design.inherent.lootr_styled.resources.ResourceAccess;
import design.inherent.lootr_styled.resources.ResourceInputs;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.minecraft.client.ClientGraphicProvider;
import design.inherent.reform.minecraft.client.ClientGraphicWork;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.reform.presentation.graphics.GraphicCamera;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMaterial;
import design.inherent.reform.presentation.graphics.GraphicRequest;
import design.inherent.reform.presentation.graphics.GraphicSceneData;
import design.inherent.reform.presentation.graphics.GraphicScenePreparation;
import design.inherent.reform.reference.ReferenceStamp;
import design.inherent.reform.serialization.JsonLimits;
import design.inherent.reform.value.ValueType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Domain stages only. The shared host owns READ capture, jobs, visibility, upload, and retirement.
 */
final class StyleGraphicProvider
    implements ClientGraphicProvider<ClientSettings, StyleGraphicSubject> {
  private static final DocumentId ID = DocumentId.parse("lootr_styled:appearance_preview");
  private static final DocumentId RESOURCES = DocumentId.parse("lootr_styled:preview_resources");
  // Match the existing bounded resource JSON contract, including accepted long frame lists.
  static final JsonLimits METADATA =
      new JsonLimits(ResourceInputs.MAX_FILE_BYTES, 64, 65536, 32768, 64, Integer.MAX_VALUE);
  private final UUID resourceContext = UUID.randomUUID();
  private final UUID absentSnapshot = UUID.randomUUID();
  private final GraphicLimits limits;
  private final Function<DocumentId, GraphicMaterial> chestMaterial;
  private final BiFunction<BarrelPreviewMesh.Face, DocumentId, GraphicMaterial> barrelMaterial;

  /** Material functions must be frozen domain policy, with no live renderer or resource handles. */
  StyleGraphicProvider(
      GraphicLimits limits,
      Function<DocumentId, GraphicMaterial> chestMaterial,
      BiFunction<BarrelPreviewMesh.Face, DocumentId, GraphicMaterial> barrelMaterial) {
    this.limits = Objects.requireNonNull(limits);
    this.chestMaterial = Objects.requireNonNull(chestMaterial);
    this.barrelMaterial = Objects.requireNonNull(barrelMaterial);
  }

  public DocumentId id() {
    return ID;
  }

  public ValueType<StyleGraphicSubject> subjectType() {
    return StyleGraphicSubject.TYPE;
  }

  public GraphicLimits limits() {
    return limits;
  }

  public ReferenceStamp resourceStamp() {
    StylePreferences.requireClient();
    var pack = NativeUi.activePack().filter(value -> value.preparation().isPresent());
    var snapshot = pack.map(GeneratedPack::id).orElse(absentSnapshot);
    return new ReferenceStamp(
        resourceContext,
        RESOURCES,
        snapshot,
        pack.isPresent() ? snapshot.toString() : "unavailable");
  }

  public CompletionStage<GraphicScenePreparation> prepare(
      GraphicRequest<ClientSettings, StyleGraphicSubject> request, ClientGraphicWork work) {
    return work.onClient(() -> captureSource(request))
        .thenCompose(
            source ->
                work.onWorker(
                    () -> {
                      var checkpoint = (Runnable) work::checkpoint;
                      checkpoint.run();
                      var facts = new ArrayList<GraphicSceneData.Fact>();
                      var settings = request.input().candidate().value();
                      var subject = request.subject();
                      StylePreviewMesh mesh;
                      List<String> diagnostics;
                      // Read-only local scope. Never use ClientRuntime's reload wrapper: it seeds
                      // files and
                      // reads saved settings instead of the complete captured candidate.
                      try (var local = LocalFiles.open(source.configuration())) {
                        if (subject.container() == StyleGraphicSubject.Container.BARREL) {
                          var input =
                              BarrelPreviewInputs.capture(
                                  source.targets(),
                                  new BarrelPreviewInputs.Subject(
                                      settings, subject.looted(), subject.open()),
                                  source.access().guarded(checkpoint),
                                  local,
                                  NativeUi::validateModel,
                                  source.emf(),
                                  source.ordinaryChestIsolated(),
                                  checkpoint);
                          var baked = NativeBarrelPreview.bake(input, work.ledger(), checkpoint);
                          mesh =
                              StylePreviewMesh.convert(
                                  baked, request.limits(), barrelMaterial, checkpoint);
                          diagnostics = input.diagnostics();
                          facts.add(
                              fact(
                                  "pose",
                                  "Pose",
                                  subject.open() ? "Open barrel" : "Closed barrel"));
                        } else {
                          var input =
                              ChestPreviewInputs.capture(
                                  source.targets(),
                                  new ChestPreviewInputs.Subject(
                                      settings,
                                      subject.container()
                                          == StyleGraphicSubject.Container.TRAPPED_CHEST,
                                      subject.looted(),
                                      subject.lid()),
                                  source.access().guarded(checkpoint),
                                  local,
                                  NativeUi::validateModel,
                                  source.emf(),
                                  source.ordinaryChestIsolated(),
                                  checkpoint);
                          var baked =
                              NativeChestPreview.prepare(input, request.limits(), checkpoint);
                          mesh =
                              StylePreviewMesh.chest(
                                  input, baked.mesh(), request.limits(), chestMaterial, checkpoint);
                          diagnostics = input.diagnostics();
                          facts.add(
                              fact(
                                  "pose",
                                  "Pose",
                                  "Static reference; native lid progress " + subject.lid()));
                          facts.add(
                              fact(
                                  "lid_control",
                                  "Native lid geometry",
                                  Boolean.toString(baked.nativeControlHasGeometry())));
                          facts.add(fact("rig_animation", "Rig animations", "Not evaluated"));
                        }
                        local.validateCurrent();
                      }
                      facts.add(fact("world", "World effects", "Not simulated"));
                      facts.add(fact("models", "Requested models", settings.models().toString()));
                      facts.add(
                          fact("artwork", "Requested artwork", settings.artwork().toString()));
                      if (diagnostics.size() + facts.size() > 64)
                        throw new PolicyRefusal(
                            "preview.budget",
                            "The preview resource diagnostics exceed their output allowance");
                      for (int i = 0; i < diagnostics.size(); i++)
                        facts.add(fact("resource/" + i, "Resource", diagnostics.get(i)));
                      var scene = mesh.prepare(work.ledger(), METADATA, checkpoint);
                      checkpoint.run();
                      return (GraphicScenePreparation)
                          new GraphicScenePreparation.Ready(
                              new GraphicSceneData(
                                  request.stamp(), scene, GraphicCamera.STANDARD, facts));
                    }));
  }

  private Source captureSource(GraphicRequest<ClientSettings, StyleGraphicSubject> request) {
    StylePreferences.requireClient();
    if (request.input().candidate().definition() != StyleSettingsStore.DEFINITION)
      throw new PolicyRefusal(
          "lootr_styled.preview.wrong_document",
          "Choose Lootr: Styled settings before preparing a preview");
    var pack =
        NativeUi.activePack()
            .filter(value -> value.preparation().isPresent())
            .orElseThrow(
                () ->
                    new PolicyRefusal(
                        "preview.provider_unavailable",
                        "Reload resources before preparing a preview"));
    if (!pack.id().equals(request.stamp().resources().snapshot()))
      throw new PolicyRefusal("preview.stale", "Resources changed; prepare the preview again");
    var profiles = request.input().observedReferences().get(StyleSettingsStore.PROFILES);
    if (profiles == null || !profiles.revision().equals(pack.id().toString()))
      throw new PolicyRefusal(
          "preview.stale",
          "The profile catalogue changed; wait for resource reload, then prepare again");
    return new Source(
        pack.lowerSources(),
        NativeUi.targets(),
        ClientRuntime.configuration().root(),
        ClientRuntime.emfSupport(),
        NativeUi.ordinaryChestIsolated());
  }

  private record Source(
      ResourceAccess access,
      ContainerTargets targets,
      Path configuration,
      EmfSupport emf,
      boolean ordinaryChestIsolated) {}

  private static GraphicSceneData.Fact fact(String id, String title, String value) {
    return new GraphicSceneData.Fact(DocumentId.parse("lootr_styled:preview/" + id), title, value);
  }
}
