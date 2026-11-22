package design.inherent.lootr_styled.client;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Admit only the inspected native Lootr wrapper; never resolve it on a server. */
public final class BarrelScopePlugin implements IMixinConfigPlugin {
  private static final String NEO =
      "noobanidus/mods/lootr/neoforge/client/block/BarrelModel$BarrelBakedModel";
  private static final String FABRIC =
      "noobanidus/mods/lootr/fabric/client/block/BarrelModel$BakedBarrelModel";
  private boolean admitted;

  @Override
  public void onLoad(String mixinPackage) {
    if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.CLIENT) return;
    String target = mixinPackage.endsWith(".neoforge") ? NEO : FABRIC;
    try (var stream = getClass().getClassLoader().getResourceAsStream(target + ".class")) {
      if (stream == null) throw new IOException("Native wrapper unavailable");
      byte[] bytes = stream.readNBytes(131073);
      if (bytes.length > 131072) throw new IOException("Native wrapper exceeds inspection budget");
      var node = new ClassNode();
      new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      admitted = admitted(node);
    } catch (IOException | RuntimeException ex) {
      admitted = false;
    }
    if (!admitted)
      System.getLogger("lootr_styled")
          .log(
              System.Logger.Level.WARNING,
              "Barrel lighting adapter unavailable; native lighting retained");
  }

  static boolean admitted(ClassNode node) {
    boolean neo = node.name.equals(NEO);
    if (!neo && !node.name.equals(FABRIC)) return false;
    String model = "Lnet/minecraft/client/resources/model/BakedModel;";
    for (String name : List.of("opened", "unopened", "vanilla", "old_opened", "old_unopened")) {
      if (node.fields.stream()
          .noneMatch(
              f ->
                  f.name.equals(name)
                      && (f.desc.equals(model)
                          || !neo && f.desc.equals("Lnet/minecraft/class_1087;"))
                      && (f.access & Opcodes.ACC_STATIC) == 0
                      && (f.access & Opcodes.ACC_FINAL) != 0)) return false;
    }
    if (neo)
      return node.fields.stream()
              .anyMatch(
                  f ->
                      f.name.equals("ambientOcclusion")
                          && f.desc.equals("Z")
                          && (f.access & Opcodes.ACC_STATIC) == 0)
          && node.methods.stream()
              .anyMatch(m -> m.name.equals("useAmbientOcclusion") && m.desc.equals("()Z"))
          && node.methods.stream().anyMatch(m -> m.name.equals("getQuads"))
          && node.methods.stream()
              .noneMatch(m -> m.name.equals("useAmbientOcclusion") && !m.desc.equals("()Z"));
    var emit = node.methods.stream().filter(m -> m.name.equals("emitBlockQuads")).findFirst();
    if (emit.isEmpty()) return false;
    int materials = 0;
    for (var instruction : emit.orElseThrow().instructions)
      if (instruction instanceof MethodInsnNode call
          && call.owner.equals("net/fabricmc/fabric/api/renderer/v1/Renderer")
          && call.name.equals("materialById")) materials++;
    return materials == 1;
  }

  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    return admitted;
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
