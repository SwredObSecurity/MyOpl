package lang;

public record Position(int idx, int ln, int col, String fn, String ftxt) {
    public Position advance(char currentChar) {
        int nIdx = idx + 1;
        int nLn = (currentChar == '\n') ? ln + 1 : ln;
        int nCol = (currentChar == '\n') ? 0 : col + 1;
        return new Position(nIdx, nLn, nCol, fn, ftxt);
    }
    public Position copy() { return new Position(idx, ln, col, fn, ftxt); }
}
