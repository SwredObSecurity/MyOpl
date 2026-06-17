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
            if (currentChar == '\\') sb.append(readEscape(false));
            else { sb.append(currentChar); advance(); }
        }
        advance(); return new Token("STRING", sb.toString(), start, pos.copy());
    }

    /** A character literal:  'x'  '\n'  '\''  'A'  — exactly one character between single quotes. */
    private Token makeChar() {
        Position start = pos.copy(); advance();           // consume opening '
        if (currentChar == null || currentChar == '\'')
            throw new RuntimeException("Empty character literal: '' must contain exactly one character");
        String ch;
        if (currentChar == '\\') {
            ch = readEscape(true);
            if (ch == null) throw new RuntimeException("Unterminated character literal");
        } else { ch = String.valueOf(currentChar); advance(); }
        if (currentChar == null || currentChar != '\'')
            throw new RuntimeException("Character literal must be exactly one character, closed with '");
        advance();                                        // consume closing '
        return new Token("CHAR", ch, start, pos.copy());
    }

    /**
     * Reads a backslash escape sequence (currentChar must be the '\'), supporting
     * the full Java set:  \t \b \n \r \f \s \' \" \\ , unicode \\uXXXX and octal
     * \\ddd.  An unrecognised escape \\x degrades to the char itself in a char
     * literal, or to the two characters \\ and x in a string (MyOPL's historic
     * behaviour). Returns the resolved text and leaves currentChar just past it.
     */
    private String readEscape(boolean forChar) {
        advance();                                        // consume the backslash
        if (currentChar == null) return forChar ? null : "\\";
        char e = currentChar;
        switch (e) {
            case 'n':  advance(); return "\n";
            case 't':  advance(); return "\t";
            case 'r':  advance(); return "\r";
            case 'b':  advance(); return "\b";
            case 'f':  advance(); return "\f";
            case 's':  advance(); return " ";             // \s — space (Java 15+)
            case '"':  advance(); return "\"";
            case '\'': advance(); return "'";
            case '\\': advance(); return "\\";
            case 'u':  return readUnicodeEscape();
            default:
                if (e >= '0' && e <= '7') return readOctalEscape();
                advance();                                // unknown escape
                return forChar ? String.valueOf(e) : ("\\" + e);
        }
    }

    /** \\uXXXX — exactly four hex digits (a run of extra 'u's is allowed, as in Java). */
    private String readUnicodeEscape() {
        advance();                                        // consume 'u'
        while (currentChar != null && currentChar == 'u') advance();
        StringBuilder hex = new StringBuilder();
        while (hex.length() < 4 && currentChar != null && isHex(currentChar)) { hex.append(currentChar); advance(); }
        if (hex.length() == 0) return "u";                // malformed — degrade gracefully
        return String.valueOf((char) Integer.parseInt(hex.toString(), 16));
    }

    /** \\ddd — one to three octal digits (\0 .. \377). */
    private String readOctalEscape() {
        int maxDigits = (currentChar <= '3') ? 3 : 2;     // values above \377 are not valid
        StringBuilder oct = new StringBuilder();
        while (oct.length() < maxDigits && currentChar != null && currentChar >= '0' && currentChar <= '7') {
            oct.append(currentChar); advance();
        }
        return String.valueOf((char) Integer.parseInt(oct.toString(), 8));
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
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
