package design.inherent.lootr_styled.client;

import design.inherent.reform.definition.FieldKey;
import design.inherent.reform.definition.RecordType;
import design.inherent.reform.value.ValueTypes;
import java.util.Objects;

/** View parameters only; the complete settings candidate is captured by the shared host. */
record StyleGraphicSubject(Container container, boolean looted, boolean open, float lid) {
  enum Container {
    CHEST,
    TRAPPED_CHEST,
    BARREL
  }

  private static final FieldKey<Container> CONTAINER =
      FieldKey.of("container", ValueTypes.enumeration(Container.class));
  private static final FieldKey<Boolean> LOOTED = FieldKey.of("looted", ValueTypes.bool());
  private static final FieldKey<Boolean> OPEN = FieldKey.of("open", ValueTypes.bool());
  private static final FieldKey<Float> LID = FieldKey.of("lid", ValueTypes.floating(0, 1));
  static final RecordType<StyleGraphicSubject> TYPE =
      RecordType.<StyleGraphicSubject>builder(
              v ->
                  new StyleGraphicSubject(v.get(CONTAINER), v.get(LOOTED), v.get(OPEN), v.get(LID)))
          .field(CONTAINER, StyleGraphicSubject::container)
          .field(LOOTED, StyleGraphicSubject::looted)
          .field(OPEN, StyleGraphicSubject::open)
          .field(LID, StyleGraphicSubject::lid)
          .build();

  StyleGraphicSubject {
    Objects.requireNonNull(container);
    if (!Float.isFinite(lid) || lid < 0 || lid > 1)
      throw new IllegalArgumentException("Lid pose must be between zero and one");
  }
}
