package lang;

/**
 * A single member of an ENUM (e.g. Color.RED). Carries the enum type name, the
 * member name and its ordinal. Prints and concatenates as its bare name, and
 * compares by value so Color.RED == Color.RED is true.
 */
public record EnumValue(String type, String name, int ordinal) {
    @Override public String toString() { return name; }
}
