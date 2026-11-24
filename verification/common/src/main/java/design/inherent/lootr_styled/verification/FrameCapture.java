package design.inherent.lootr_styled.verification;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** One bounded image request after four actual root draws, separate from GPU presentation proof. */
final class FrameCapture {
  private final Screen expected;
  private long baseline = -1, requestedLoop, requestedDraw;
  private volatile long completedLoop = -1;
  private boolean requested;

  FrameCapture(Screen expected) {
    this.expected = expected;
  }

  boolean poll(Minecraft mc, String name) throws Exception {
    long drawn = FrameAudit.rootSequence(expected);
    if (NativeBindings.screen(mc) != expected || !NativeBindings.unobscured(mc) || drawn < 0) {
      if (requested) throw new IllegalStateException("Capture lost its rendered root: " + name);
      baseline = -1;
      return false;
    }
    if (baseline < 0) baseline = drawn;
    if (drawn - baseline < 4) return false;
    if (!requested) {
      requested = true;
      requestedLoop = FrameAudit.sequence();
      requestedDraw = drawn;
      NativeWorld.screenshot(mc, name, () -> completedLoop = FrameAudit.sequence());
    }
    return completedLoop >= requestedLoop;
  }

  JsonObject receipt(String name) {
    if (!requested || completedLoop < requestedLoop)
      throw new IllegalStateException("Capture has not completed: " + name);
    var result = new JsonObject();
    result.addProperty("name", name);
    result.addProperty("expectedScreen", expected.getClass().getName());
    result.addProperty("expectedRootDrawn", true);
    result.addProperty("baselineRootDraw", baseline);
    result.addProperty("requestedRootDraw", requestedDraw);
    result.addProperty("requestedLoop", requestedLoop);
    result.addProperty("completedLoop", completedLoop);
    return result;
  }
}
