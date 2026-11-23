package design.inherent.lootr_styled.verification;

import design.inherent.reform.minecraft.client.ClientMeshGraphic;

/** Client-thread native draw outcomes; no graphics lease or scene payload is retained. */
public final class GraphicDrawAudit {
  private static long rendered, waiting, empty;

  private GraphicDrawAudit() {}

  public record State(long rendered, long waiting, long empty) {}

  public static State state() {
    return new State(rendered, waiting, empty);
  }

  public static void observed(ClientMeshGraphic.DrawOutcome outcome) {
    switch (outcome) {
      case RENDERED -> rendered++;
      case WAITING -> waiting++;
      case EMPTY -> empty++;
    }
  }
}
