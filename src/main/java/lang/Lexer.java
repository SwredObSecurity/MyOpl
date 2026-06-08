package lang;
import java.util.*;

public class Lexer {
    private final String fn, text;
    private Position pos;
    private Character currentChar;
    private static final List<String> KEYWORDS = List.of(
        "VAR", "CONST", "FUN", "INIT", "NEW", "RETURN", "IF", "THEN", "ELSE",
        "FOR", "TO", "STEP", "WHILE", "BEGIN", "END",
        "CLASS", "IMPORT", "TRUE", "FALSE"
    );

    public Lexer(String fn, String text) {
        this.fn = fn; this.text = text;
        this.pos = new Position(-1, 0, -1, fn, text);
        advance();
    }

    private void advance() {
        this.pos = this.pos.advance(this.currentChar != null ? this.currentChar : '\0');
        this.currentChar = (this.pos.idx() < this.text.length()) ? this.text.charAt(this.pos.idx()) : null;
    }

    public List<Token> makeTokens() {
        List<Token> tokens = new ArrayList<>();
        while (currentChar != null) {
            if (Character.isWhitespace(currentChar)) advance();
            else if (currentChar == '#') {
                advance();
                if (currentChar != null && currentChar == '*') {
                    advance();
                    while (currentChar != null) {
                        if (currentChar == '*') { advance(); if (currentChar != null && currentChar == '#') { advance(); break; } }
                        else advance();
                    }
                } else {
                    while (currentChar != null && currentChar != '\n') advance();
                }
            }
            else if (currentChar == '"') tokens.add(makeString());
            else if (currentChar == '\'') tokens.add(makeChar());
            else if (Character.isDigit(currentChar)) tokens.add(makeNumber());
            else if (Character.isLetter(currentChar) || currentChar == '_') tokens.add(makeIdentifier());
            else if (currentChar == '.') { tokens.add(new Token("DOT", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == '+') { tokens.add(new Token("PLUS", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == '-') {
                Position start = pos.copy(); advance();
                if (currentChar != null && currentChar == '>') { advance(); tokens.add(new Token("ARROW", start, pos.copy())); }
                else tokens.add(new Token("MINUS", start, pos.copy()));
            }
            else if (currentChar == '*') { tokens.add(new Token("MUL", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == '/') { tokens.add(new Token("DIV", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == '(') { tokens.add(new Token("LPAREN", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == ')') { tokens.add(new Token("RPAREN", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == '[') { tokens.add(new Token("LSQUARE", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == ']') { tokens.add(new Token("RSQUARE", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == ',') { tokens.add(new Token("COMMA", pos.copy(), pos.copy())); advance(); }
            else if (currentChar == '!') {
                Position start = pos.copy(); advance();
                if (currentChar != null && currentChar == '=') { advance(); tokens.add(new Token("NE", start, pos.copy())); }
            }
            else if (currentChar == '=') {
                Position start = pos.copy(); advance();
                if (currentChar != null && currentChar == '=') { advance(); tokens.add(new Token("EE", start, pos.copy())); }
                else tokens.add(new Token("EQ", start, pos.copy()));
            }
            else if (currentChar == '<') {
                Position start = pos.copy(); advance();
                if (currentChar != null && currentChar == '=') { advance(); tokens.add(new Token("LTE", start, pos.copy())); }
                else tokens.add(new Token("LT", start, pos.copy()));
            }
            else if (currentChar == '>') {
                Position start = pos.copy(); advance();
                if (currentChar != null && currentChar == '=') { advance(); tokens.add(new Token("GTE", start, pos.copy())); }
                else tokens.add(new Token("GT", start, pos.copy()));
            }
            else advance();
        }
        return tokens;
    }

    private Token makeString() {
        StringBuilder sb = new StringBuilder(); Position start = pos.copy(); advance();
        while (currentChar != null && currentChar != '"') {
            if (currentChar == '\\') {
                advance();
                if (currentChar == null) break;
                switch (currentChar) {
                    case 'n'  -> sb.append('\n');
                    case 't'  -> sb.append('\t');
                    case 'r'  -> sb.append('\r');
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default   -> { sb.append('\\'); sb.append(currentChar); }
                }
                advance();
            } else { sb.append(currentChar); advance(); }
        }
        advance(); return new Token("STRING", sb.toString(), start, pos.copy());
    }

    /** A character literal:  'x'  '\n'  '\''  — exactly one character between single quotes. */
    private Token makeChar() {
        Position start = pos.copy(); advance();           // consume opening '
        if (currentChar == null || currentChar == '\'')
            throw new RuntimeException("Empty character literal: '' must contain exactly one character");
        char c;
        if (currentChar == '\\') {
            advance();
            if (currentChar == null) throw new RuntimeException("Unterminated character literal");
            c = switch (currentChar) {
                case 'n'  -> '\n';
                case 't'  -> '\t';
                case 'r'  -> '\r';
                case '0'  -> '\0';
                case '\'' -> '\'';
                case '\\' -> '\\';
                default   -> currentChar;                 // unknown escape → the char itself
            };
            advance();
        } else { c = currentChar; advance(); }
        if (currentChar == null || currentChar != '\'')
            throw new RuntimeException("Character literal must be exactly one character, closed with '");
        advance();                                        // consume closing '
        return new Token("CHAR", String.valueOf(c), start, pos.copy());
    }

    private Token makeNumber() {
        StringBuilder sb = new StringBuilder(); Position start = pos.copy();
        while (currentChar != null && (Character.isDigit(currentChar) || currentChar == '.')) { sb.append(currentChar); advance(); }
        String s = sb.toString();
        return new Token(s.contains(".") ? "FLOAT" : "INT", Double.parseDouble(s), start, pos.copy());
    }

    private Token makeIdentifier() {
        StringBuilder sb = new StringBuilder(); Position start = pos.copy();
        while (currentChar != null && (Character.isLetterOrDigit(currentChar) || currentChar == '_')) { sb.append(currentChar); advance(); }
        String word = sb.toString();
        return new Token(KEYWORDS.contains(word) ? "KEYWORD" : "IDENTIFIER", word, start, pos.copy());
    }
}
