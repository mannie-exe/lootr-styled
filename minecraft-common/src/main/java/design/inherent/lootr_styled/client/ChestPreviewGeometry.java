package design.inherent.lootr_styled.client;

import design.inherent.lootr_styled.resources.ChestPreviewInputs;
import design.inherent.reform.presentation.graphics.GraphicMesh;
import java.util.Objects;

/** Static reference geometry and actual native-control coverage, not evaluated rig animation. */
record ChestPreviewGeometry(
    GraphicMesh mesh,
    ChestPreviewInputs.PoseFacts pose,
    boolean nativeLidHasGeometry,
    boolean nativeLockHasGeometry) {
  ChestPreviewGeometry {
    Objects.requireNonNull(mesh);
    Objects.requireNonNull(pose);
  }

  boolean nativeControlHasGeometry() {
    return nativeLidHasGeometry || nativeLockHasGeometry;
  }
}
