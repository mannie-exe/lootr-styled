package design.inherent.lootr_styled.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

class NativeScopeAdmissionTest {
  private static ClassNode read(String type) throws Exception {
    try (var input = NativeScopeAdmissionTest.class.getResourceAsStream("/" + type + ".class")) {
      assertNotNull(input, type);
      var result = new ClassNode();
      new ClassReader(input.readAllBytes()).accept(result, 0);
      return result;
    }
  }

  private static List<ClassNode> nodes() throws Exception {
    return List.of(
        read(NativeScopeCapabilities.RENDERER),
        read(NativeScopeCapabilities.STATE),
        read(NativeScopeCapabilities.CONTEXT),
        read(NativeScopeCapabilities.SET));
  }

  private static boolean admitted(List<ClassNode> nodes) {
    return NativeScopePlugin.admitted(nodes.get(0), nodes.get(1), nodes.get(2), nodes.get(3));
  }

  @Test
  void pinnedNativeClassesMatchTheActualScopeHook() throws Exception {
    assertTrue(admitted(nodes()));
  }

  @Test
  void missingOrStaticModelFieldRefuses() throws Exception {
    var missing = nodes();
    missing.getFirst().fields.removeIf(f -> f.name.equals("singleModel"));
    assertFalse(admitted(missing));
    var changed = nodes();
    changed.getFirst().fields.stream()
        .filter(f -> f.name.equals("singleModel"))
        .forEach(f -> f.access |= Opcodes.ACC_STATIC);
    assertFalse(admitted(changed));
  }

  @Test
  void changedStateOrContextRefuses() throws Exception {
    var state = nodes();
    state.get(1).fields.stream()
        .filter(f -> f.name.equals("chestType"))
        .forEach(f -> f.desc = "Ljava/lang/Object;");
    assertFalse(admitted(state));
    var context = nodes();
    context.get(2).methods.removeIf(m -> m.name.equals("entityModelSet"));
    assertFalse(admitted(context));
  }

  @Test
  void changedRootRepresentationRefuses() throws Exception {
    var roots = nodes();
    roots.get(3).fields.stream()
        .filter(f -> f.name.equals("roots"))
        .forEach(f -> f.desc = "Ljava/lang/Object;");
    assertFalse(admitted(roots));
  }

  @Test
  void changedConstructorOrSubmitDescriptorRefuses() throws Exception {
    var ctor = nodes();
    ctor.getFirst().methods.removeIf(m -> m.name.equals("<init>"));
    assertFalse(admitted(ctor));
    var method = nodes();
    method.getFirst().methods.stream()
        .filter(m -> m.name.equals("submit") && m.desc.equals(NativeScopeCapabilities.SUBMIT))
        .forEach(m -> m.desc = "()V");
    assertFalse(admitted(method));
  }

  @Test
  void fewerAndExtraFieldReadsRefuseRatherThanPartiallyHooking() throws Exception {
    var fewer = nodes();
    var method =
        fewer.getFirst().methods.stream()
            .filter(m -> m.name.equals("submit") && m.desc.equals(NativeScopeCapabilities.SUBMIT))
            .findFirst()
            .orElseThrow();
    for (var instruction : method.instructions)
      if (instruction instanceof FieldInsnNode field
          && field.getOpcode() == Opcodes.GETFIELD
          && field.name.equals("singleModel")) {
        method.instructions.remove(field);
        break;
      }
    assertFalse(admitted(fewer));
    var extra = nodes();
    extra.getFirst().methods.stream()
        .filter(m -> m.name.equals("submit") && m.desc.equals(NativeScopeCapabilities.SUBMIT))
        .findFirst()
        .orElseThrow()
        .instructions
        .add(
            new FieldInsnNode(
                Opcodes.GETFIELD,
                NativeScopeCapabilities.RENDERER,
                "singleModel",
                NativeScopeCapabilities.MODEL));
    assertFalse(admitted(extra));
  }
}
