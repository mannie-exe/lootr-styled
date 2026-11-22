package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

class BarrelScopeAdmissionTest {
  private static ClassNode nativeWrapper() throws Exception {
    for (String name :
        List.of(
            "noobanidus/mods/lootr/neoforge/client/block/BarrelModel$BarrelBakedModel",
            "noobanidus/mods/lootr/fabric/client/block/BarrelModel$BakedBarrelModel")) {
      try (var stream =
          BarrelScopeAdmissionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
        if (stream == null) continue;
        var node = new ClassNode();
        new ClassReader(stream.readAllBytes())
            .accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
      }
    }
    throw new IllegalStateException("Actual target Lootr wrapper unavailable");
  }

  @Test
  void admitsTheActualPinnedWrapperWithoutLoadingIt() throws Exception {
    assertTrue(BarrelScopePlugin.admitted(nativeWrapper()));
  }

  @Test
  void refusesMissingStaticOrWronglyTypedStateModels() throws Exception {
    var missing = nativeWrapper();
    missing.fields.removeIf(f -> f.name.equals("opened"));
    assertFalse(BarrelScopePlugin.admitted(missing));
    var staticField = nativeWrapper();
    staticField.fields.stream()
        .filter(f -> f.name.equals("unopened"))
        .forEach(f -> f.access |= Opcodes.ACC_STATIC);
    assertFalse(BarrelScopePlugin.admitted(staticField));
    var changedType = nativeWrapper();
    changedType.fields.stream()
        .filter(f -> f.name.equals("vanilla"))
        .forEach(f -> f.desc = "Ljava/lang/Object;");
    assertFalse(BarrelScopePlugin.admitted(changedType));
  }

  @Test
  void refusesForeignWrappersAndChangedSelectionSeams() throws Exception {
    var foreign = nativeWrapper();
    foreign.name += "$Foreign";
    assertFalse(BarrelScopePlugin.admitted(foreign));
    var changed = nativeWrapper();
    if (changed.name.contains("/neoforge/")) {
      changed.methods.add(
          new MethodNode(Opcodes.ACC_PUBLIC, "useAmbientOcclusion", "(Z)Z", null, null));
    } else {
      changed.methods.removeIf(m -> m.name.equals("emitBlockQuads"));
    }
    assertFalse(BarrelScopePlugin.admitted(changed));
  }
}
