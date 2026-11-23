package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.presentation.graphics.GraphicLimits;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.ChestRenderer;

/** Detached native chest geometry only; no block entity, renderer, texture atlas, or GPU state. */
final class NativeChestPreview {
  static final DocumentId MATERIAL = DocumentId.parse("lootr_styled:preview/chest");

  private NativeChestPreview() {}

  static ModelPart nativeRoot() {
    return ChestRenderer.createSingleBodyLayer().bakeRoot();
  }

  static void pose(ModelPart owned, float progress) {
    float remaining = 1 - progress;
    float eased = 1 - remaining * remaining * remaining;
    owned.getChild("lid").xRot = -eased * ((float) Math.PI / 2);
    owned.getChild("lock").xRot = owned.getChild("lid").xRot;
  }

  static GraphicMesh bake(ChestPreviewInputs inputs, GraphicLimits limits, Runnable checkpoint) {
    checkpoint.run();
    var owned = nativeRoot();
    if (inputs.rig().isPresent()) owned = DetachedEmfChest.prepare(inputs, owned, checkpoint);
    pose(owned, inputs.subject().lid());
    return EntityPreviewGeometry.capture(owned, MATERIAL, limits, checkpoint);
  }
}
