package design.inherent.lootr_styled.resources;

import java.util.Optional;
import java.util.regex.Pattern;

/** Metadata admission only; resource shape is independently validated before adaptation. */
public record EmfSupport(boolean installed, boolean admitted, String reason) {
  private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");
  public static final EmfSupport ABSENT =
      new EmfSupport(false, false, "EMF absent; native chest rendering");

  public static EmfSupport inspect(Optional<String> version) {
    if (version.isEmpty()) return ABSENT;
    String value = version.orElseThrow();
    if (value.length() > 160)
      return new EmfSupport(true, false, "Unsupported EMF version metadata");
    var match = VERSION.matcher(value);
    if (match.matches()) {
      try {
        int major = Integer.parseInt(match.group(1)),
            minor = Integer.parseInt(match.group(2)),
            patch = Integer.parseInt(match.group(3));
        if (major == 3 && (minor > 3 || minor == 3 && patch >= 12))
          return new EmfSupport(
              true, true, "EMF 3 resource adapter; effective rig admission required");
      } catch (NumberFormatException ignored) {
        /* Unknown metadata is not capability. */
      }
    }
    return new EmfSupport(true, false, "EMF version is outside the admitted 3.x resource contract");
  }
}
