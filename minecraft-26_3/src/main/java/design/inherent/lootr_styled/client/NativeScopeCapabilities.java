package design.inherent.lootr_styled.client;

/** Startup ABI admission. This class deliberately has no Minecraft or Lootr linkage. */
public final class NativeScopeCapabilities {
  static final String RENDERER =
      "noobanidus/mods/lootr/common/client/block/LootrChestBlockRenderer";
  static final String STATE =
      "noobanidus/mods/lootr/common/client/state/LootrChestBlockRenderState";
  static final String MODEL = "Lnet/minecraft/client/model/object/chest/ChestModel;";
  static final String CONTEXT =
      "net/minecraft/client/renderer/blockentity/BlockEntityRendererProvider$Context";
  static final String SET = "net/minecraft/client/model/geom/EntityModelSet";
  static final String SUBMIT =
      "(L"
          + STATE
          + ";Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
  private static volatile boolean admitted;

  private NativeScopeCapabilities() {}

  static void admit(boolean value) {
    admitted = value;
  }

  public static boolean ordinaryIsolation() {
    return admitted;
  }
}
