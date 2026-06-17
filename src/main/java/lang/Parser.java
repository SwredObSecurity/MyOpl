package lang;
import java.util.*;

public class Parser {
    private List<Token> tokens;
    private int idx = -1;
    private Token current;

    public Parser(List<Token> tokens) { this.tokens = tokens; advance(); }
    private void advance() { idx++; current = (idx < tokens.size()) ? tokens.get(idx) : null; }

    /** Look ahead k tokens from the current one without consuming (peek(0) == current). */
    private Token peek(int k) { int j = idx + k; return (j >= 0 && j < tokens.size()) ? tokens.get(j) : null; }

    public Node parse() {
        List<Node> statements = new ArrayList<>();
        Position start = current != null ? current.posStart() : null;
        while (current != null) {
            Node stmt = expr();
            if (stmt != null) statements.add(stmt);
        }
        return new BlockNode(statements, start,
                (statements.isEmpty() ? start : statements.get(statements.size() - 1).posEnd()));
    }

    // ── Expressions ───────────────────────────────────────────────────────

    private Node expr() {
        if (current != null && current.type().equals("KEYWORD")) {
            String val = (String) current.value();
            if (val.equals("VAR"))    return varDecl(false);
            if (val.equals("CONST"))  return varDecl(true);
            if (val.equals("IF"))     return ifExpr();
            if (val.equals("FOR"))    return forExpr();
            if (val.equals("WHILE"))  return whileExpr();
            if (val.equals("RETURN")) { Position s = current.posStart(); advance(); Node v = expr(); return new ReturnNode(v, s, v != null ? v.posEnd() : s); }
            if (val.equals("CLASS"))  return classDefExpr();
            if (val.equals("IMPORT")) return importExpr();
        }
        // Typed declaration:  Type name = expr   (two identifiers followed by '=').
        // Two identifiers in a row only ever mean a typed declaration, and the
        // trailing '=' separates it cleanly from a bare identifier expression.
        if (current != null && current.type().equals("IDENTIFIER")
                && peek(1) != null && peek(1).type().equals("IDENTIFIER")
                && peek(2) != null && peek(2).type().equals("EQ")) {
            Token typeTok = current; advance();   // consume Type
            Token nameTok = current; advance();   // consume name
            advance();                            // consume '='
            return new VarAssignNode(nameTok, expr(), false, typeTok);
        }
        // Comparisons are the lowest precedence (outermost), so additive operators
        // bind tighter:  i + 1 <= x  parses as  (i + 1) <= x.
        return binOp(this::arith, List.of("EE", "NE", "LT", "GT", "LTE", "GTE"));
    }

    /**
     * VAR / CONST declaration. Both infer the type from the value (like Java's
     * `var`); CONST additionally makes the binding immutable. An optional explicit
     * type is tolerated too, e.g.  CONST Int X = 5.
     *   VAR name = expr            (inferred, mutable)
     *   CONST name = expr          (inferred, immutable)
     *   VAR/CONST Type name = expr (explicit type)
     */
    private Node varDecl(boolean isConst) {
        advance();                               // consume VAR / CONST
        Token typeTok = null;
        if (current != null && current.type().equals("IDENTIFIER")
                && peek(1) != null && peek(1).type().equals("IDENTIFIER")) {
            typeTok = current; advance();         // consume explicit type name
        }
        Token name = current; advance();          // consume variable name
        if (current != null && current.type().equals("EQ")) advance();   // consume '='
        return new VarAssignNode(name, expr(), isConst, typeTok);
    }

    /**
     * Parse a typed parameter list (the tokens between the parentheses).
     * Every parameter must be written as 'Type name'; names and types are
     * collected into the two parallel lists.
     */
    private void parseParams(List<Token> names, List<Token> types) {
        if (current != null && current.type().equals("IDENTIFIER")) {
            parseOneParam(names, types);
            while (current != null && current.type().equals("COMMA")) { advance(); parseOneParam(names, types); }
        }
    }

    private void parseOneParam(List<Token> names, List<Token> types) {
        Token typeTok = current;
        if (typeTok == null || !typeTok.type().equals("IDENTIFIER"))
            throw new RuntimeException("Expected a parameter type name");
        advance();
        if (current == null || !current.type().equals("IDENTIFIER"))
            throw new RuntimeException("Parameter '" + typeTok.value()
                + "' must be written as 'Type name' (a type then a name), e.g. FUN f(Int x)");
        types.add(typeTok);
        names.add(current);
        advance();
    }

    // ── Control-flow helpers ──────────────────────────────────────────────

    private Node ifExpr() {
        advance(); Node cond = expr();
        if (current != null && current.value().equals("THEN")) advance();
        Node thenCase = expr(); Node elseCase = null;
        if (current != null && current.value().equals("ELSE")) { advance(); elseCase = expr(); }
        return new IfNode(cond, thenCase, elseCase);
    }

    private Node forExpr() {
        advance(); Token var = current; advance(); advance(); Node start = expr();
        advance(); Node end = expr();
        Node step = new NumberNode(new Token("INT", 1.0, current.posStart(), current.posEnd()));
        if (current != null && current.value().equals("STEP")) { advance(); step = expr(); }
        if (current != null && current.value().equals("THEN")) advance();
        return new ForNode(var, start, end, step, expr());
    }

    private Node whileExpr() {
        advance(); Node cond = expr();
        if (current != null && current.value().equals("THEN")) advance();
        return new WhileNode(cond, expr());
    }

    // ── Class / Import ────────────────────────────────────────────────────

    /**
     * CLASS ClassName BEGIN
     *     FUN method(args) -> body
     *     VAR field = value
     * END
     */
    private Node classDefExpr() {
        advance();                           // consume CLASS
        Token name = current; advance();     // consume ClassName
        Position start = name.posStart();
        if (current != null && current.value().equals("BEGIN")) advance(); // consume BEGIN
        List<Node> stmts = new ArrayList<>();
        while (current != null && !current.value().equals("END")) {
            Node s = expr();
            if (s != null) stmts.add(s);
        }
        Position end = current != null ? current.posEnd() : name.posEnd();
        if (current != null) advance();      // consume END
        return new ClassDefNode(name, new BlockNode(stmts, start, end), start, end);
    }

    /**
     * IMPORT "path/to/file.myopl"
     */
    private Node importExpr() {
        advance();                            // consume IMPORT
        Token path = current; advance();      // consume STRING path
        return new ImportNode(path);
    }

    // ── Operators ─────────────────────────────────────────────────────────

    private Node arith()    { return binOp(this::term, List.of("PLUS", "MINUS")); }
    private Node term()     { return binOp(this::unary, List.of("MUL", "DIV")); }

    /** Unary minus / plus:  -5,  -n,  -Math.abs(x),  +3  (binds tighter than * and /). */
    private Node unary() {
        if (current != null && (current.type().equals("MINUS") || current.type().equals("PLUS"))) {
            Token op = current; advance();
            return new UnaryOpNode(op, unary());
        }
        return factor();
    }

    /**
     * Handles function calls  foo(args)
     * and member access        Obj.member  /  Obj.method(args)
     */
    private Node factor() {
        Node res = atom();
        while (current != null) {
            if (current.type().equals("LPAREN")) {
                advance();
                List<Node> args = new ArrayList<>();
                if (current != null && !current.type().equals("RPAREN")) {
                    args.add(expr());
                    while (current != null && current.type().equals("COMMA")) { advance(); args.add(expr()); }
                }
                Position end = current != null ? current.posEnd() : res.posEnd();
                if (current != null) advance(); // consume RPAREN
                res = new CallNode(res, args, res.posStart(), end);
            } else if (current.type().equals("DOT")) {
                advance();                          // consume '.'
                Token member = current; advance();  // consume member name
                res = new MemberAccessNode(res, member);
            } else {
                break;
            }
        }
        return res;
    }

    private Node atom() {
        if (current == null) return null;
        if (current.type().equals("KEYWORD")) {
            if (current.value().equals("FUN")) return funDef();
            if (current.value().equals("INIT")) return constructorDef();
            if (current.value().equals("NEW")) return newExpr();
            // Boolean literals are just 1 / 0 under the hood, matching how
            // comparisons and IF/WHILE already represent truth in this language.
            if (current.value().equals("TRUE") || current.value().equals("FALSE")) {
                Token t = current; advance();
                double v = t.value().equals("TRUE") ? 1.0 : 0.0;
                // Tagged "BOOL" (still a 1.0 / 0.0 Double at runtime) so the type
                // layer can infer Bool for  VAR b = TRUE.
                return new NumberNode(new Token("BOOL", v, t.posStart(), t.posEnd()));
            }
            if (current.value().equals("BEGIN")) {
                Position s = current.posStart(); advance();
                List<Node> stmts = new ArrayList<>();
                while (current != null && !current.value().equals("END")) { stmts.add(expr()); }
                Position e = current != null ? current.posEnd() : s;
                if (current != null) advance();
                return new BlockNode(stmts, s, e);
            }
        }
        if (current.type().equals("STRING"))     { Token t = current; advance(); return new StringNode(t); }
        if (current.type().equals("CHAR"))       { Token t = current; advance(); return new CharNode(t); }
        if (current.type().equals("INT") || current.type().equals("FLOAT")) { Token t = current; advance(); return new NumberNode(t); }
        if (current.type().equals("IDENTIFIER")) { Token t = current; advance(); return new VarAccessNode(t); }
        if (current.type().equals("LPAREN"))     { advance(); Node n = expr(); if (current != null && current.type().equals("RPAREN")) advance(); return n; }
        if (current.type().equals("LSQUARE"))    return listExpr();
        return null;
    }

    private Node listExpr() {
        Position s = current.posStart(); advance();
        List<Node> elements = new ArrayList<>();
        if (current != null && !current.type().equals("RSQUARE")) {
            elements.add(expr());
            while (current != null && current.type().equals("COMMA")) { advance(); elements.add(expr()); }
        }
        Position e = current != null ? current.posEnd() : s;
        if (current != null) advance();
        return new ListNode(elements, s, e);
    }

    /**
     * Single-line body:  FUN f(args) -> expr                  (body is the returned expression)
     * Multi-line  body:  FUN f(args) BEGIN stmt stmt END      (body is a block; use RETURN to yield)
     */
    private Node funDef() {
        advance();
        Token name = null;
        if (current != null && current.type().equals("IDENTIFIER")) { name = current; advance(); }
        if (current != null && current.type().equals("LPAREN")) advance();
        List<Token> args = new ArrayList<>();
        List<Token> argTypes = new ArrayList<>();
        parseParams(args, argTypes);
        if (current != null && current.type().equals("RPAREN")) advance();

        // Single-line body:  FUN f(args) -> expr
        if (current != null && current.type().equals("ARROW")) { advance(); return new FunDefNode(name, args, argTypes, expr()); }

        // Multi-line body:  FUN f(args) BEGIN stmt stmt ... END   (atom() parses the BEGIN ... END block)
        if (current == null || !current.type().equals("KEYWORD") || !"BEGIN".equals(current.value()))
            throw new RuntimeException("Expected '->' for a single-line body, or 'BEGIN ... END' for a multi-line function body");
        return new FunDefNode(name, args, argTypes, expr());
    }

    /**
     * Constructor:  INIT(args) BEGIN stmt stmt ... END   (or  INIT(args) -> expr)
     * Parsed like a function but stored under the reserved member name "__init__",
     * so NEW ClassName(args) can find and run it.
     */
    private Node constructorDef() {
        Token kw = current; advance();                    // consume INIT
        Token name = new Token("IDENTIFIER", "__init__", kw.posStart(), kw.posEnd());
        if (current != null && current.type().equals("LPAREN")) advance();
        List<Token> args = new ArrayList<>();
        List<Token> argTypes = new ArrayList<>();
        parseParams(args, argTypes);
        if (current != null && current.type().equals("RPAREN")) advance();

        if (current != null && current.type().equals("ARROW")) { advance(); return new FunDefNode(name, args, argTypes, expr()); }
        if (current == null || !current.type().equals("KEYWORD") || !"BEGIN".equals(current.value()))
            throw new RuntimeException("Expected '->' or 'BEGIN ... END' for an INIT constructor body");
        return new FunDefNode(name, args, argTypes, expr());
    }

    /**
     * NEW ClassName(args)  — instantiate a class into a fresh object.
     */
    private Node newExpr() {
        Position start = current.posStart(); advance();   // consume NEW
        Token className = current; advance();              // consume ClassName
        List<Node> args = new ArrayList<>();
        if (current != null && current.type().equals("LPAREN")) {
            advance();
            if (current != null && !current.type().equals("RPAREN")) {
                args.add(expr());
                while (current != null && current.type().equals("COMMA")) { advance(); args.add(expr()); }
            }
            if (current != null && current.type().equals("RPAREN")) advance();
        }
        Position end = className.posEnd();
        return new NewNode(className, args, start, end);
    }

    private Node binOp(java.util.function.Supplier<Node> func, List<String> ops) {
        Node left = func.get();
        while (current != null && ops.contains(current.type())) {
            Token op = current; advance();
            left = new BinOpNode(left, op, func.get());
        }
        return left;
    }
}
