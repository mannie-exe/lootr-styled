package design.inherent.lootr_styled;

import java.util.Objects;
import java.util.regex.Pattern;

/** A bounded resource identity; filesystem containment remains the input adapter's duty. */
public record ResourceId(String namespace, String path) {
  public static final int MAX_LENGTH = 256;
  private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
  private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

  public ResourceId {
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(path, "path");
    if (namespace.length() + 1L + path.length() > MAX_LENGTH
        || !NAMESPACE.matcher(namespace).matches()
        || namespace.equals(".")
        || namespace.equals("..")
        || !PATH.matcher(path).matches()) {
      throw new IllegalArgumentException("Invalid or oversized resource identity");
    }
    for (String segment : path.split("/", -1)) {
      if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
        throw new IllegalArgumentException("Resource path contains an empty or traversing segment");
      }
    }
  }

  public static ResourceId parse(String value) {
    Objects.requireNonNull(value, "value");
    int split = value.indexOf(':');
    if (split < 1 || split != value.lastIndexOf(':')) {
      throw new IllegalArgumentException("Resource identity requires an explicit namespace");
    }
    return new ResourceId(value.substring(0, split), value.substring(split + 1));
  }

  @Override
  public String toString() {
    return namespace + ":" + path;
  }
}
