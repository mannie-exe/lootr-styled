package design.inherent.lootr_styled.verification;

import design.inherent.reform.minecraft.client.ClientMeshGraphic;

/** Client-thread draw outcomes and one bounded control, cleared before its callback runs. */
public final class GraphicDrawAudit {
  private static long rendered, waiting, empty;
  private static Runnable nextRendered;

  private GraphicDrawAudit() {}

  public record State(long rendered, long waiting, long empty) {}

  public static State state() {
    return new State(rendered, waiting, empty);
  }

  static void afterNextRendered(Runnable action) {
    if (nextRendered != null) throw new IllegalStateException("A draw control is already armed");
    nextRendered = java.util.Objects.requireNonNull(action);
  }

  public static void observed(ClientMeshGraphic.DrawOutcome outcome) {
    switch (outcome) {
      case RENDERED -> rendered++;
      case WAITING -> waiting++;
      case EMPTY -> empty++;
    }
    if (outcome == ClientMeshGraphic.DrawOutcome.RENDERED) {
      var action = nextRendered;
      nextRendered = null;
      if (action != null) action.run();
    }
  }
}
