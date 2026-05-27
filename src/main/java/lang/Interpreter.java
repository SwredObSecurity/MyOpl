package lang;
import java.util.*;
import java.nio.file.*;

public class Interpreter {
    private Map<String, Object> symbols = new HashMap<>();
    private final Scanner stdin = new Scanner(System.in);
    private String baseDir = ".";

    private record BoundMethod(FunDefNode fun, Map<String, Object> classScope) {}

    private static final Set<String> BUILTINS = Set.of(
        "PRINT", "LEN", "INPUT", "INPUT_NUM", "READ_FILE", "WRITE_FILE", "APPEND_FILE"
    );

    public Interpreter() { registerBuiltins(); }
    public Interpreter(String baseDir) { this.baseDir = baseDir; registerBuiltins(); }

    private void registerBuiltins() {
        BUILTINS.forEach(n -> symbols.put(n, new BuiltInFunctionNode(n)));
    }

    public void setBaseDir(String dir) { this.baseDir = dir; }

    public Map<String, Object> getPublicSymbols() {
        Map<String, Object> pub = new HashMap<>(symbols);
        BUILTINS.forEach(pub::remove);
        return pub;
    }

    public Object visit(Node node) {
        return switch (node) {
            case NumberNode n -> n.token().value();
            case StringNode s -> s.token().value();
            case ListNode l -> {
                List<Object> res = new ArrayList<>();
                for (Node n : l.elementNodes()) res.add(visit(n));
                yield res;
            }
            case VarAssignNode v -> {
                Object val = visit(v.valueNode());
                symbols.put((String) v.varNameToken().value(), val);
                yield val;
            }
            case VarAccessNode v -> symbols.getOrDefault(v.varNameToken().value(), 0.0);
            case BinOpNode b   -> visitBinOp(b);
            case UnaryOpNode u -> visitUnaryOp(u);
            case FunDefNode f  -> {
                if (f.varNameToken() != null)
                    symbols.put((String) f.varNameToken().value(), f);
                yield null;
            }
            case CallNode c   -> executeCall(c);
            case ReturnNode r -> visit(r.nodeToReturn());
            case IfNode i -> {
                if (isTrue(visit(i.condition()))) yield visit(i.thenCase());
                yield i.elseCase() != null ? visit(i.elseCase()) : null;
            }
            case WhileNode w -> {
                while (isTrue(visit(w.condition()))) visit(w.body());
                yield null;
            }
            case ForNode f -> {
                double s    = ((Number) visit(f.start())).doubleValue();
                double e    = ((Number) visit(f.end())).doubleValue();
                double step = ((Number) visit(f.step())).doubleValue();
                String name = (String) f.varName().value();
                for (double i = s; i <= e; i += step) { symbols.put(name, i); visit(f.body()); }
                yield null;
            }
            case BlockNode b -> {
                Object last = null;
                for (Node n : b.statements()) last = visit(n);
                yield last;
            }
            case BuiltInFunctionNode bi -> bi;

            case ClassDefNode cd -> {
                Map<String, Object> saved = symbols;
                symbols = new HashMap<>();
                visit(cd.body());
                Map<String, Object> classScope = new HashMap<>(symbols);
                symbols = saved;
                symbols.put((String) cd.nameToken().value(), classScope);
                yield null;
            }

            case ImportNode imp -> {
                String pathStr = (String) imp.pathToken().value();
                Path filePath  = Path.of(baseDir).resolve(pathStr);
                try {
                    String code   = Files.readString(filePath);
                    String subDir = filePath.getParent() != null
                            ? filePath.getParent().toAbsolutePath().toString()
                            : baseDir;
                    Interpreter sub = new Interpreter(subDir);
                    List<Token> toks = new Lexer(pathStr, code).makeTokens();
                    Node ast = new Parser(toks).parse();
                    sub.visit(ast);
                    symbols.putAll(sub.getPublicSymbols());
                } catch (Exception e) {
                    throw new RuntimeException("IMPORT failed for '" + pathStr + "': " + e.getMessage());
                }
                yield null;
            }

            case MemberAccessNode m -> {
                Object obj = visit(m.objectNode());
                String memberName = (String) m.memberToken().value();
                if (obj instanceof Map<?,?> raw) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> classScope = (Map<String, Object>) raw;
                    Object member = classScope.get(memberName);
                    if (member instanceof FunDefNode fd)
                        yield new BoundMethod(fd, classScope);
                    yield member;
                }
                throw new RuntimeException("Not a class: cannot access '." + memberName + "'");
            }
        };
    }

    private Object executeCall(CallNode call) {
        Object callable = visit(call.nodeToCall());

        if (callable instanceof BuiltInFunctionNode bi) {
            if (bi.name().equals("PRINT")) {
                for (Node n : call.argNodes()) System.out.print(visit(n) + " ");
                System.out.println();
                return null;
            }
            if (bi.name().equals("LEN"))
                return (double) ((List<?>) visit(call.argNodes().get(0))).size();
            if (bi.name().equals("INPUT")) {
                if (!call.argNodes().isEmpty()) System.out.print(visit(call.argNodes().get(0)));
                return stdin.nextLine();
            }
            if (bi.name().equals("INPUT_NUM")) {
                if (!call.argNodes().isEmpty()) System.out.print(visit(call.argNodes().get(0)));
                try { return Double.parseDouble(stdin.nextLine().trim()); }
                catch (NumberFormatException e) { return 0.0; }
            }
            if (bi.name().equals("READ_FILE")) {
                try { return Files.readString(Path.of((String) visit(call.argNodes().get(0)))); }
                catch (Exception e) { throw new RuntimeException("READ_FILE failed: " + e.getMessage()); }
            }
            if (bi.name().equals("WRITE_FILE")) {
                try {
                    Files.writeString(Path.of((String) visit(call.argNodes().get(0))),
                            String.valueOf(visit(call.argNodes().get(1))));
                    return 1.0;
                } catch (Exception e) { throw new RuntimeException("WRITE_FILE failed: " + e.getMessage()); }
            }
            if (bi.name().equals("APPEND_FILE")) {
                try {
                    Files.writeString(Path.of((String) visit(call.argNodes().get(0))),
                            String.valueOf(visit(call.argNodes().get(1))),
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    return 1.0;
                } catch (Exception e) { throw new RuntimeException("APPEND_FILE failed: " + e.getMessage()); }
            }
        }

        if (callable instanceof BoundMethod bm) {
            FunDefNode def = bm.fun();
            Map<String, Object> snap = new HashMap<>(symbols);
            symbols.putAll(bm.classScope());
            for (int i = 0; i < def.argNameTokens().size(); i++)
                symbols.put((String) def.argNameTokens().get(i).value(), visit(call.argNodes().get(i)));
            Object res = visit(def.bodyNode());
            symbols = snap;
            return res;
        }

        FunDefNode def = (FunDefNode) callable;
        Map<String, Object> snap = new HashMap<>(symbols);
        for (int i = 0; i < def.argNameTokens().size(); i++)
            symbols.put((String) def.argNameTokens().get(i).value(), visit(call.argNodes().get(i)));
        Object res = visit(def.bodyNode());
        symbols = snap;
        return res;
    }

    private boolean isTrue(Object val) {
        if (val instanceof Double d) return d != 0.0;
        return val != null;
    }

    private Object visitBinOp(BinOpNode b) {
        Object left  = visit(b.leftNode());
        Object right = visit(b.rightNode());
        if (b.opToken().type().equals("PLUS") && (left instanceof String || right instanceof String))
            return String.valueOf(left) + String.valueOf(right);
        double l = ((Number) left).doubleValue();
        double r = ((Number) right).doubleValue();
        return switch (b.opToken().type()) {
            case "PLUS"  -> l + r;
            case "MINUS" -> l - r;
            case "MUL"   -> l * r;
            case "DIV"   -> l / r;
            case "EE"    -> l == r ? 1.0 : 0.0;
            case "NE"    -> l != r ? 1.0 : 0.0;
            case "LT"    -> l < r  ? 1.0 : 0.0;
            case "GT"    -> l > r  ? 1.0 : 0.0;
            case "LTE"   -> l <= r ? 1.0 : 0.0;
            case "GTE"   -> l >= r ? 1.0 : 0.0;
            default      -> 0.0;
        };
    }

    private Double visitUnaryOp(UnaryOpNode u) {
        double val = ((Number) visit(u.node())).doubleValue();
        return u.opToken().type().equals("MINUS") ? -val : val;
    }
}
