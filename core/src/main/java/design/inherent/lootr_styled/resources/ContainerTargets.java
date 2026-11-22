package design.inherent.lootr_styled.resources;

import design.inherent.lootr_styled.ResourceId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum ContainerTargets {
  LEGACY(
      "lootr_",
      "textures/",
      List.of("chest", "chest_opened", "chest_trapped", "chest_trapped_opened")),
  MODERN(
      "",
      "textures/entity/chest/",
      List.of("normal", "normal_opened", "trapped", "trapped_opened"));

  private final String prefix;
  private final String chestDirectory;
  private final List<String> chestNames;

  ContainerTargets(String prefix, String chestDirectory, List<String> chestNames) {
    this.prefix = prefix;
    this.chestDirectory = chestDirectory;
    this.chestNames = chestNames;
  }

  public List<Barrel> barrels() {
    return List.of(
        barrel(false, false), barrel(false, true), barrel(true, false), barrel(true, true));
  }

  private Barrel barrel(boolean looted, boolean open) {
    String name = looted ? prefix + "opened_barrel" : prefix + "barrel_unopened";
    String texture = looted ? "opened_barrel" : "barrel";
    return new Barrel(
        ResourceId.parse("lootr:models/block/" + name + (open ? "_open" : "") + ".json"),
        ResourceId.parse("minecraft:models/block/barrel" + (open ? "_open" : "") + ".json"),
        texture,
        open);
  }

  public Set<ResourceId> artwork() {
    var result =
        chestNames.stream()
            .map(n -> ResourceId.parse("lootr:" + chestDirectory + n + ".png"))
            .collect(Collectors.toCollection(java.util.HashSet::new));
    for (String state : List.of("barrel", "opened_barrel")) {
      for (String face : List.of("side", "top", "top_open", "bottom")) {
        result.add(ResourceId.parse("lootr:textures/block/" + state + "_" + face + ".png"));
      }
    }
    return Set.copyOf(result);
  }

  public Map<ResourceId, ResourceId> defaultArtwork() {
    var result = new HashMap<ResourceId, ResourceId>();
    for (int i = 0; i < chestNames.size(); i++) {
      result.put(
          ResourceId.parse("lootr:" + chestDirectory + chestNames.get(i) + ".png"),
          ResourceId.parse("lootr_styled:textures/default/" + LEGACY.chestNames.get(i) + ".png"));
    }
    for (var target : artwork()) {
      if (target.path().startsWith("textures/block/")) {
        result.put(
            target,
            ResourceId.parse(
                "lootr_styled:textures/default/" + target.path().substring("textures/".length())));
      }
    }
    return Map.copyOf(result);
  }

  public List<Chest> chests() {
    String prefix = this == LEGACY ? "lootr_lootr_" : "lootr_";
    return List.of(
        new Chest("chest", ResourceId.parse("minecraft:emf/cem/" + prefix + "chest.jem")),
        new Chest(
            "trapped_chest",
            ResourceId.parse("minecraft:emf/cem/" + prefix + "trapped_chest.jem")));
  }

  public record Chest(String vanillaName, ResourceId target) {}

  public record Barrel(ResourceId target, ResourceId vanilla, String material, boolean open) {}
}
