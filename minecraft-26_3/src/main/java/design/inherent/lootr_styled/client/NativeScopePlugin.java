package design.inherent.lootr_styled.client;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Inspect required class bytes without resolving client classes on a dedicated server. */
public final class NativeScopePlugin implements IMixinConfigPlugin {
  @Override
  public void onLoad(String mixinPackage) {
    if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) return;
    // No CEM output is possible without EMF. Avoid installing renderer/accessor hooks
    // on that independent native path; resource lookup does not initialize foreign code.
    if (getClass().getClassLoader().getResource("traben/entity_model_features/EMF.class") == null)
      return;
    try {
      NativeScopeCapabilities.admit(
          admitted(
              read(NativeScopeCapabilities.RENDERER),
              read(NativeScopeCapabilities.STATE),
              read(NativeScopeCapabilities.CONTEXT),
              read(NativeScopeCapabilities.SET)));
    } catch (IOException | RuntimeException ex) {
      NativeScopeCapabilities.admit(false);
      System.getLogger("lootr_styled")
          .log(
              System.Logger.Level.WARNING,
              "Shared chest renderer ABI unavailable; ordinary CEM adaptation remains native");
    }
  }

  private ClassNode read(String name) throws IOException {
    try (var stream = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
      if (stream == null) throw new IOException("Missing native binding");
      byte[] bytes = stream.readNBytes(131073);
      if (bytes.length > 131072) throw new IOException("Native class inspection budget exceeded");
      var node = new ClassNode();
      new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return node;
    }
  }

  static boolean admitted(
      ClassNode renderer, ClassNode state, ClassNode context, ClassNode modelSet) {
    boolean field =
        renderer.fields.stream()
            .anyMatch(
                f ->
                    f.name.equals("singleModel")
                        && f.desc.equals(NativeScopeCapabilities.MODEL)
                        && (f.access & Opcodes.ACC_STATIC) == 0);
    boolean constructor =
        renderer.methods.stream()
            .anyMatch(
                m ->
                    m.name.equals("<init>")
                        && m.desc.equals("(L" + NativeScopeCapabilities.CONTEXT + ";)V"));
    var submit =
        renderer.methods.stream()
            .filter(m -> m.name.equals("submit") && m.desc.equals(NativeScopeCapabilities.SUBMIT))
            .findFirst();
    if (!field || !constructor || submit.isEmpty()) return false;
    int reads = 0;
    for (var instruction : submit.orElseThrow().instructions) {
      if (instruction instanceof FieldInsnNode f
          && f.getOpcode() == Opcodes.GETFIELD
          && f.owner.equals(NativeScopeCapabilities.RENDERER)
          && f.name.equals("singleModel")
          && f.desc.equals(NativeScopeCapabilities.MODEL)) reads++;
    }
    return reads == 3
        && state.fields.stream()
            .anyMatch(
                f ->
                    f.name.equals("chestType")
                        && f.desc.equals("Lnoobanidus/mods/lootr/common/api/LootrChestType;"))
        && context.methods.stream()
            .anyMatch(
                m ->
                    m.name.equals("entityModelSet")
                        && m.desc.equals("()L" + NativeScopeCapabilities.SET + ";"))
        && modelSet.fields.stream()
            .anyMatch(f -> f.name.equals("roots") && f.desc.equals("Ljava/util/Map;"));
  }

  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    return !mixin.endsWith("LootrChestScopeMixin") && !mixin.endsWith("EntityModelSetAccessor")
        || NativeScopeCapabilities.ordinaryIsolation();
  }

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public void acceptTargets(Set<String> own, Set<String> other) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

  @Override
  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
