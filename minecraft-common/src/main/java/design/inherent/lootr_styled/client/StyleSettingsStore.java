package design.inherent.lootr_styled.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import design.inherent.lootr_styled.ResourceId;
import design.inherent.lootr_styled.profile.ClientSettings;
import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldKey;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.definition.RecordType;
import design.inherent.reform.edit.Candidate;
import design.inherent.reform.edit.DraftLimits;
import design.inherent.reform.edit.DraftTree;
import design.inherent.reform.presentation.ChoiceDisplay;
import design.inherent.reform.serialization.JsonLimits;
import design.inherent.reform.storage.OwnedFile;
import design.inherent.reform.storage.StorageBinding;
import design.inherent.reform.storage.TextStore;
import design.inherent.reform.validation.Issue;
import design.inherent.reform.validation.ValidationReport;
import design.inherent.reform.value.SnapshotPolicy;
import design.inherent.reform.value.ValueType;
import design.inherent.reform.value.ValueTypes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** The existing format over re:form's single bounded, revision-aware file binding. */
public final class StyleSettingsStore extends TextStore<ClientSettings> {
  public static final DocumentId PROFILES = DocumentId.parse("lootr_styled:profiles");
  public static final int MAXIMUM_BYTES = 16384;
  public static final JsonLimits LIMITS = new JsonLimits(MAXIMUM_BYTES, 16, 64, 512, 32, 512);
  private static final ValueType<ResourceId> PROFILE =
      ValueTypes.reference(
          ValueType.of(
              Codec.STRING.comapFlatMap(StyleSettingsStore::profile, ResourceId::toString),
              ValueTypes.text(256).shape(),
              SnapshotPolicy.immutable()),
          PROFILES);
  private static final FieldKey<Boolean> ENABLED = FieldKey.of("enabled", ValueTypes.bool());
  private static final FieldKey<ResourceId> MODELS = FieldKey.of("models", PROFILE);
  private static final FieldKey<ResourceId> ARTWORK = FieldKey.of("artwork", PROFILE);
  private static final FieldKey<ClientSettings.Fresh> FRESH =
      FieldKey.of(
          "freshObjects",
          ValueTypes.choiceDisplay(
              ValueTypes.enumeration(ClientSettings.Fresh.class),
              new ChoiceDisplay(
                  List.of(
                      new ChoiceDisplay.Caption("AUTO", "lootr_styled.settings.fresh.auto", "Auto"),
                      new ChoiceDisplay.Caption("ON", "lootr_styled.settings.fresh.on", "On"),
                      new ChoiceDisplay.Caption(
                          "OFF", "lootr_styled.settings.fresh.off", "Off")))));
  public static final DocumentDefinition<ClientSettings> DEFINITION =
      new DocumentDefinition<>(
          DocumentId.parse("lootr_styled:settings"),
          1,
          RecordType.<ClientSettings>builder(
                  values ->
                      new ClientSettings(
                          values.get(ENABLED),
                          values.get(MODELS),
                          values.get(ARTWORK),
                          values.get(FRESH)))
              .field(ENABLED, ClientSettings::enabled)
              .field(MODELS, ClientSettings::models)
              .field(ARTWORK, ClientSettings::artwork)
              .field(FRESH, ClientSettings::fresh)
              .build(),
          () -> ClientSettings.DEFAULTS);

  public StyleSettingsStore(Path configDirectory) {
    super(
        DEFINITION,
        new OwnedFile(
            StorageBinding.relative(
                configDirectory.toAbsolutePath(), "lootr_styled", "settings.json"),
            MAXIMUM_BYTES),
        LIMITS,
        DraftLimits.CONFIGURATION,
        "lootr-styled-settings",
        "Local appearance settings");
  }

  private static DataResult<ResourceId> profile(String value) {
    try {
      return DataResult.success(ResourceId.parse(value));
    } catch (IllegalArgumentException invalid) {
      return DataResult.error(() -> "Enter a complete profile ID, such as lootr_styled:default");
    }
  }

  @Override
  protected DraftTree decode(String text) {
    try {
      return DraftTree.from(
          definition.type(),
          ClientSettings.parse(text.getBytes(StandardCharsets.UTF_8)),
          draftLimits);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidData(
          new ValidationReport(
              List.of(
                  Issue.error(
                      "settings.format", FieldPath.ROOT, "Check the format 1 settings file")),
              true));
    }
  }

  @Override
  protected String encode(Read<ClientSettings> baseline, Candidate<ClientSettings> candidate) {
    return candidate.value().toJson();
  }

  @Override
  protected void validateEncoded(String text) {
    decode(text);
  }
}
