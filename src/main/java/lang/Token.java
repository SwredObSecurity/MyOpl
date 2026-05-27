package lang;

public record Token(String type, Object value, Position posStart, Position posEnd) {
    public Token(String type, Position posStart, Position posEnd) { this(type, null, posStart, posEnd); }
    @Override public String toString() { return (value != null) ? type + ":" + value : type; }
}
