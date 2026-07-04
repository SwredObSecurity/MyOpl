package lang;
import java.util.*;
import java.util.stream.Stream;
import java.nio.file.*;
import javax.swing.*;
import java.awt.FlowLayout;

public class Interpreter {
    private Map<String, Object> symbols = new HashMap<>();
    private Set<String> constants = new HashSet<>();
    /** Declared / inferred type name for each bound variable, parallel to `symbols`.
     *  Powers typeOf() and the typed-declaration checks. Saved/restored alongside
     *  `symbols` whenever a new scope is entered (classes, calls, NEW). */
    private Map<String, String> varTypes = new HashMap<>();
    /** Type aliases: an alias name -> the type it stands for (ALIAS Money = Dec). */
    private final Map<String, String> typeAliases = new HashMap<>();
    /** For each class/interface, the set of all its ancestors (superclasses and
     *  implemented interfaces, transitively) — used for subtype matching. */
    private final Map<String, Set<String>> ancestors = new HashMap<>();
    private final Scanner stdin = new Scanner(System.in);
    private String baseDir = ".";

    /** Root of the Standard Class Library, searched as a fallback for bare
     *  imports like  IMPORT "Random.myopl"  so they resolve from any script.
     *  Shared across the main interpreter and every sub-interpreter. */
    private static String sclRoot = null;
    public static void setSclRoot(String dir) { sclRoot = dir; }

    private record BoundMethod(FunDefNode fun, Map<String, Object> classScope) {}

    /** Unwinds the stack from a RETURN statement up to the enclosing function call. */
    private static final class ReturnSignal extends RuntimeException {
        final Object value;
        ReturnSignal(Object value) { super(null, null, false, false); this.value = value; }
    }

    private static final Set<String> BUILTINS = Set.of(
        "PRINT", "LEN", "INPUT", "INPUT_NUM", "READ_FILE", "WRITE_FILE", "APPEND_FILE", "typeOf",
        "INPUT_STR", "INPUT_INT", "INPUT_DEC", "INPUT_BOOL", "INPUT_CHR",
        // --- Swing GUI bridge (used by the Frame and OptionPane companion classes) ---
        "MSG_BOX", "INPUT_BOX", "CONFIRM_BOX",
        "FRAME_NEW", "FRAME_SET_TITLE", "FRAME_GET_TITLE", "FRAME_SET_SIZE",
        "FRAME_SET_VISIBLE", "FRAME_IS_VISIBLE", "FRAME_SET_CLOSE", "FRAME_ADD_LABEL",
        "FRAME_ADD_BUTTON", "FRAME_CLEAR", "FRAME_CENTER", "FRAME_PACK", "FRAME_DISPOSE"
    );

    /** Live Swing windows created by the Frame companion class, keyed by the handle
     *  each Frame instance stores. Keeping the real JFrame in Java lets MyOPL Frame
     *  methods mutate the same window across calls. */
    private final Map<Double, JFrame> frames = new HashMap<>();
    private double nextFrameId = 1.0;

    public Interpreter() { registerBuiltins(); }
    public Interpreter(String baseDir) { this.baseDir = baseDir; registerBuiltins(); }

    private void registerBuiltins() {
        BUILTINS.forEach(n -> symbols.put(n, new BuiltInFunctionNode(n)));
    }

    public void setBaseDir(String dir) { this.baseDir = dir; }

    /** Shared console access so the host (Shell) and the interpreter read from a
     *  single Scanner — two Scanners on System.in would steal each other's input. */
    public boolean hasConsoleLine() { return stdin.hasNextLine(); }
    public String  readConsoleLine() { return stdin.nextLine(); }

    /**
     * Resolve an IMPORT target. First look next to the current file
     * (baseDir/pathStr). If that does not exist, fall back to searching the
     * Standard Class Library tree by file name, so a bare
     * {@code IMPORT "Random.myopl"} finds SCL/Extras/Random.myopl from any
     * script. If nothing matches, return the local path so the subsequent
     * read throws a clear "no such file" error for the path the user wrote.
     */
    private Path resolveImport(String pathStr) {
        Path local = Path.of(baseDir).resolve(pathStr);
        if (Files.exists(local) || sclRoot == null) return local;
        String wanted = Path.of(pathStr).getFileName().toString();
        try (Stream<Path> tree = Files.walk(Path.of(sclRoot))) {
            Optional<Path> hit = tree
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(wanted))
                    .findFirst();
            if (hit.isPresent()) return hit.get();
        } catch (Exception ignored) {}
        return local;
    }

    public Map<String, Object> getPublicSymbols() {
        Map<String, Object> pub = new HashMap<>(symbols);
        BUILTINS.forEach(pub::remove);
        return pub;
    }

    /** Type metadata exported to an importing interpreter (aliases + ancestor sets). */
    public Map<String, String> getPublicAliases()      { return typeAliases; }
    public Map<String, Set<String>> getPublicAncestors() { return ancestors; }

    public Object visit(Node node) {
        return switch (node) {
            case NumberNode n -> n.token().value();
            case StringNode s -> s.token().value();
            case CharNode c   -> c.token().value();
            case ListNode l -> {
                List<Object> res = new ArrayList<>();
                for (Node n : l.elementNodes()) res.add(visit(n));
                yield res;
            }
            case VarAssignNode v -> {
                String name = (String) v.varNameToken().value();
                if (constants.contains(name))
                    throw new RuntimeException("Cannot reassign constant '" + name + "'");
                Object val = visit(v.valueNode());
                String type;
                if (v.typeToken() != null) {
                    type = (String) v.typeToken().value();   // explicit:  Int x = ...
                    checkType(type, name, val);
                } else {
                    type = inferType(v.valueNode(), val);     // VAR / CONST infer the type
                }
                symbols.put(name, val);
                varTypes.put(name, type);
                if (v.isConst()) constants.add(name);
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
            case ReturnNode r -> { throw new ReturnSignal(r.nodeToReturn() != null ? visit(r.nodeToReturn()) : null); }
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
                String className = (String) cd.nameToken().value();

                // Collect the direct parents (one superclass, any interfaces).
                List<String> parents = new ArrayList<>();
                if (cd.superToken() != null) parents.add((String) cd.superToken().value());
                for (Token it : cd.interfaceTokens()) parents.add((String) it.value());

                Map<String, Object> saved = symbols;
                Set<String> savedConsts = constants;
                Map<String, String> savedTypes = varTypes;
                symbols = new HashMap<>();
                constants = new HashSet<>();
                varTypes = new HashMap<>();
                // Inherit members from each parent (defined earlier in the outer scope);
                // the subclass body below overrides any it redefines.
                for (String pname : parents) {
                    if (saved.get(pname) instanceof Map<?, ?> pm) {
                        for (Map.Entry<?, ?> e : pm.entrySet())
                            if (!"__class__".equals(e.getKey())) symbols.put((String) e.getKey(), e.getValue());
                    }
                }
                visit(cd.body());
                Map<String, Object> classScope = new HashMap<>(symbols);
                // Tag the class scope with its name so instances copied from it report
                // their class via typeOf() and satisfy custom-type declarations.
                classScope.put("__class__", className);
                symbols = saved;
                constants = savedConsts;
                varTypes = savedTypes;
                symbols.put(className, classScope);
                varTypes.put(className, "Class");

                // Record this type's full ancestor set for subtype matching.
                Set<String> anc = new HashSet<>();
                for (String pname : parents) {
                    anc.add(pname);
                    Set<String> pa = ancestors.get(pname);
                    if (pa != null) anc.addAll(pa);
                }
                ancestors.put(className, anc);
                yield null;
            }

            case EnumDefNode ed -> {
                String enumName = (String) ed.nameToken().value();
                Map<String, Object> enumScope = new HashMap<>();
                enumScope.put("__class__", enumName);
                List<Token> members = ed.memberTokens();
                for (int i = 0; i < members.size(); i++) {
                    String m = (String) members.get(i).value();
                    enumScope.put(m, new EnumValue(enumName, m, i));
                }
                symbols.put(enumName, enumScope);
                varTypes.put(enumName, "Enum");
                yield null;
            }

            case AliasNode al -> {
                typeAliases.put((String) al.nameToken().value(), (String) al.targetToken().value());
                yield null;
            }

            case ImportNode imp -> {
                String pathStr = (String) imp.pathToken().value();
                Path filePath  = resolveImport(pathStr);
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
                    typeAliases.putAll(sub.getPublicAliases());     // carry over type metadata
                    ancestors.putAll(sub.getPublicAncestors());
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

            case NewNode nw -> {
                String className = (String) nw.classNameToken().value();
                Object classObj = symbols.get(className);
                if (!(classObj instanceof Map<?,?> raw))
                    throw new RuntimeException("Cannot instantiate '" + className + "': not a class");
                @SuppressWarnings("unchecked")
                Map<String, Object> classScope = (Map<String, Object>) raw;

                // Each instance gets its own copy of the class's members/fields.
                Map<String, Object> instance = new HashMap<>(classScope);

                Object ctorObj = classScope.get("__init__");
                if (ctorObj instanceof FunDefNode ctor) {
                    // Evaluate constructor args in the CALLER's scope first.
                    List<Object> argVals = new ArrayList<>();
                    for (Node a : nw.argNodes()) argVals.add(visit(a));

                    Map<String, Object> saved = symbols;
                    Set<String> savedConsts = constants;
                    Map<String, String> savedTypes = varTypes;
                    symbols = new HashMap<>(saved);   // keep builtins + globals visible
                    symbols.putAll(instance);          // class members visible to the ctor
                    varTypes = new HashMap<>(savedTypes);
                    Set<String> argNames = new HashSet<>();
                    for (Token t : ctor.argNameTokens()) argNames.add((String) t.value());
                    constants = new HashSet<>(savedConsts);
                    bindArgs(ctor, argVals);           // type-check + bind ctor parameters
                    callBody(ctor);

                    // Persist fields the ctor declared/changed (class members or brand-new
                    // names), but not its parameters, builtins, or pre-existing globals.
                    for (String k : symbols.keySet()) {
                        if (argNames.contains(k) || BUILTINS.contains(k)) continue;
                        if (classScope.containsKey(k) || !saved.containsKey(k))
                            instance.put(k, symbols.get(k));
                    }
                    symbols = saved;
                    constants = savedConsts;
                    varTypes = savedTypes;
                }
                yield instance;
            }
        };
    }

    private Object executeCall(CallNode call) {
        Object callable = visit(call.nodeToCall());

        if (callable instanceof BuiltInFunctionNode bi) {
            if (bi.name().equals("typeOf")) {
                if (call.argNodes().isEmpty()) return "Null";
                Node argNode = call.argNodes().get(0);
                // For a plain variable, report its declared / inferred type directly,
                // so e.g.  VAR b = TRUE  reads back as "Bool" not "Int".
                if (argNode instanceof VarAccessNode va) {
                    String nm = (String) va.varNameToken().value();
                    if (varTypes.containsKey(nm)) return varTypes.get(nm);
                }
                return inferType(argNode, visit(argNode));
            }
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
            if (bi.name().equals("INPUT_NUM") || bi.name().equals("INPUT_DEC")) {
                String line = readInputLine(call);
                try { return Double.parseDouble(line.trim()); }
                catch (NumberFormatException e) { return 0.0; }
            }
            if (bi.name().equals("INPUT_STR")) {
                return readInputLine(call);                 // a Str (any text)
            }
            if (bi.name().equals("INPUT_INT")) {
                String line = readInputLine(call);
                try { return (double) (long) Double.parseDouble(line.trim()); }  // whole number, toward zero
                catch (NumberFormatException e) { return 0.0; }
            }
            if (bi.name().equals("INPUT_BOOL")) {
                String s = readInputLine(call).trim().toLowerCase();
                return (s.equals("true") || s.equals("yes") || s.equals("y")
                        || s.equals("t") || s.equals("1")) ? 1.0 : 0.0;
            }
            if (bi.name().equals("INPUT_CHR")) {
                String s = readInputLine(call);             // a Chr: the first character typed
                return s.isEmpty() ? "" : s.substring(0, 1);
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

            // --- Swing GUI bridge -------------------------------------------------
            // JOptionPane-style dialogs. Message/option-type ints mirror Java's
            // constants (see the OptionPane companion class).
            if (bi.name().equals("MSG_BOX")) {
                List<Object> a = evalArgs(call);
                String  message = a.size() > 0 ? String.valueOf(a.get(0)) : "";
                String  title   = a.size() > 1 ? String.valueOf(a.get(1)) : "Message";
                int     type    = a.size() > 2 ? (int) toNum(a.get(2)) : JOptionPane.INFORMATION_MESSAGE;
                JOptionPane.showMessageDialog(null, message, title, type);
                return null;
            }
            if (bi.name().equals("INPUT_BOX")) {
                List<Object> a = evalArgs(call);
                String message = a.size() > 0 ? String.valueOf(a.get(0)) : "";
                String title   = a.size() > 1 ? String.valueOf(a.get(1)) : "Input";
                Object initial = a.size() > 2 ? a.get(2) : "";
                String res = (String) JOptionPane.showInputDialog(null, message, title,
                        JOptionPane.QUESTION_MESSAGE, null, null, String.valueOf(initial));
                return res == null ? "" : res;      // Cancel/close -> "" (no null value in MyOPL)
            }
            if (bi.name().equals("CONFIRM_BOX")) {
                List<Object> a = evalArgs(call);
                String message = a.size() > 0 ? String.valueOf(a.get(0)) : "";
                String title   = a.size() > 1 ? String.valueOf(a.get(1)) : "Confirm";
                int    option  = a.size() > 2 ? (int) toNum(a.get(2)) : JOptionPane.YES_NO_CANCEL_OPTION;
                return (double) JOptionPane.showConfirmDialog(null, message, title, option);
            }

            // JFrame-style windows. Every op takes the frame handle as its first arg.
            if (bi.name().equals("FRAME_NEW")) {
                List<Object> a = evalArgs(call);
                String title = (!a.isEmpty() && a.get(0) instanceof String s) ? s : "";
                JFrame f = new JFrame(title);
                f.setLayout(new FlowLayout());
                f.setSize(400, 300);
                f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                double id = nextFrameId++;
                frames.put(id, f);
                return id;
            }
            if (bi.name().startsWith("FRAME_")) {
                List<Object> a = evalArgs(call);
                JFrame f = a.isEmpty() ? null : frames.get(toNum(a.get(0)));
                if (f == null) throw new RuntimeException(bi.name() + ": no such window (was it disposed?)");
                switch (bi.name()) {
                    case "FRAME_SET_TITLE"   -> f.setTitle(a.get(1) instanceof String s ? s : "");
                    case "FRAME_GET_TITLE"   -> { return f.getTitle(); }
                    case "FRAME_SET_SIZE"    -> f.setSize((int) toNum(a.get(1)), (int) toNum(a.get(2)));
                    case "FRAME_SET_VISIBLE" -> f.setVisible(toNum(a.get(1)) != 0);
                    case "FRAME_IS_VISIBLE"  -> { return f.isVisible() ? 1.0 : 0.0; }
                    case "FRAME_SET_CLOSE"   -> f.setDefaultCloseOperation((int) toNum(a.get(1)));
                    case "FRAME_ADD_LABEL"   -> { f.add(new JLabel(String.valueOf(a.get(1)))); f.revalidate(); f.repaint(); }
                    case "FRAME_ADD_BUTTON"  -> { f.add(new JButton(String.valueOf(a.get(1)))); f.revalidate(); f.repaint(); }
                    case "FRAME_CLEAR"       -> { f.getContentPane().removeAll(); f.revalidate(); f.repaint(); }
                    case "FRAME_CENTER"      -> f.setLocationRelativeTo(null);
                    case "FRAME_PACK"        -> f.pack();
                    case "FRAME_DISPOSE"     -> { f.dispose(); frames.remove(toNum(a.get(0))); }
                    default -> throw new RuntimeException("Unknown frame op: " + bi.name());
                }
                return null;
            }
        }

        if (callable instanceof BoundMethod bm) {
            FunDefNode def = bm.fun();
            // Evaluate arguments in the CALLER's scope FIRST, before switching scopes,
            // so nested calls like f(a, g(b)) and caller-shadowed names resolve correctly.
            List<Object> argVals = new ArrayList<>();
            for (Node n : call.argNodes()) argVals.add(visit(n));
            Map<String, Object> snap = new HashMap<>(symbols);
            Set<String> constSnap = new HashSet<>(constants);
            Map<String, String> typeSnap = varTypes;
            varTypes = new HashMap<>(typeSnap);
            symbols.putAll(bm.classScope());
            bindArgs(def, argVals);
            Object res = callBody(def);
            // Persist writes to the object's OWN fields back into it, so methods
            // can mutate instance/class state across calls (e.g. a Random's seed).
            for (String k : bm.classScope().keySet())
                if (symbols.containsKey(k)) bm.classScope().put(k, symbols.get(k));
            symbols = snap;
            constants = constSnap;
            varTypes = typeSnap;
            return res;
        }

        FunDefNode def = (FunDefNode) callable;
        List<Object> argVals = new ArrayList<>();
        for (Node n : call.argNodes()) argVals.add(visit(n));
        Map<String, Object> snap = new HashMap<>(symbols);
        Set<String> constSnap = new HashSet<>(constants);
        Map<String, String> typeSnap = varTypes;
        varTypes = new HashMap<>(typeSnap);
        bindArgs(def, argVals);
        Object res = callBody(def);
        symbols = snap;
        constants = constSnap;
        varTypes = typeSnap;
        return res;
    }

    /**
     * Bind call arguments to a function's parameters in the current scope,
     * enforcing each parameter's declared type and recording it for typeOf().
     * Missing trailing arguments default to 0 (unchecked), matching the
     * language's "a missing argument defaults to 0" rule.
     */
    private void bindArgs(FunDefNode def, List<Object> argVals) {
        List<Token> names = def.argNameTokens();
        List<Token> types = def.argTypeTokens();
        String fname = def.varNameToken() != null ? (String) def.varNameToken().value() : "<function>";
        for (int i = 0; i < names.size(); i++) {
            String pname = (String) names.get(i).value();
            Object val   = i < argVals.size() ? argVals.get(i) : 0.0;
            String ptype = (types != null && i < types.size() && types.get(i) != null)
                    ? (String) types.get(i).value() : null;
            if (ptype != null && i < argVals.size() && !typeMatches(ptype, val))
                throw new RuntimeException("Type error: parameter '" + pname + "' of '" + fname
                        + "' expects " + ptype + " but got " + typeNameOfValue(val) + " value");
            symbols.put(pname, val);
            if (ptype != null) varTypes.put(pname, ptype);
        }
    }

    /** Evaluate every argument of a call, in order, in the current scope. */
    private List<Object> evalArgs(CallNode call) {
        List<Object> out = new ArrayList<>();
        for (Node n : call.argNodes()) out.add(visit(n));
        return out;
    }

    /** Best-effort numeric view of a MyOPL value (Double as-is, bool/anything else -> 0). */
    private double toNum(Object v) {
        if (v instanceof Double d) return d;
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        if (v instanceof String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; } }
        return 0.0;
    }

    /** Print the optional prompt argument (if any) and read one line from stdin. */
    private String readInputLine(CallNode call) {
        if (!call.argNodes().isEmpty()) System.out.print(visit(call.argNodes().get(0)));
        return stdin.nextLine();
    }

    /** Runs a function body, turning a RETURN anywhere inside it into the call's result. */
    private Object callBody(FunDefNode def) {
        try { return visit(def.bodyNode()); }
        catch (ReturnSignal rs) { return rs.value; }
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
        // Equality works on text too (chars and strings), not just numbers.
        if ((b.opToken().type().equals("EE") || b.opToken().type().equals("NE"))
                && (left instanceof String || right instanceof String)) {
            boolean equal = String.valueOf(left).equals(String.valueOf(right));
            boolean want  = b.opToken().type().equals("EE") ? equal : !equal;
            return want ? 1.0 : 0.0;
        }
        // Equality on enum values and other non-numeric values (e.g. objects) compares
        // by value, so Color.RED == Color.RED is true and never tries numeric coercion.
        if ((b.opToken().type().equals("EE") || b.opToken().type().equals("NE"))
                && (!(left instanceof Number) || !(right instanceof Number))) {
            boolean equal = Objects.equals(left, right);
            boolean want  = b.opToken().type().equals("EE") ? equal : !equal;
            return want ? 1.0 : 0.0;
        }
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

    // ── Type system ─────────────────────────────────────────────────────────

    /** Throw a clear error if `val` cannot inhabit the declared type `declared`. */
    private void checkType(String declared, String name, Object val) {
        if (!typeMatches(declared, val))
            throw new RuntimeException("Type error: cannot assign " + typeNameOfValue(val)
                    + " value to '" + name + "' (declared as " + declared + ")");
        // For a generic list type like List<Int>, also check each element.
        if (resolveAlias(stripGenerics(declared)).equals("List") && val instanceof List<?> list) {
            String arg = firstTypeArg(declared);
            if (arg != null)
                for (Object el : list)
                    if (!typeMatches(arg, el))
                        throw new RuntimeException("Type error: list element " + typeNameOfValue(el)
                                + " does not match element type " + arg + " of '" + name + "'");
        }
    }

    /**
     * Does a runtime value satisfy a declared type name? Aliases are resolved and
     * generic parameters erased first (List<Int> matches like List). Because MyOPL
     * stores booleans as numbers and chars as one-character strings, the built-in
     * checks are by broad category (number / text / list / object). `Any` accepts
     * anything; any other name is a class/enum/interface type, matched against the
     * value's own class tag and its ancestors (so a subclass satisfies a supertype).
     */
    private boolean typeMatches(String type, Object v) {
        if (type == null) return true;
        String base = resolveAlias(stripGenerics(type));
        return switch (base) {
            case "Any", "Obj", "Object"            -> true;
            case "Int", "Dec", "Num", "Number", "Bool" -> v instanceof Double || v instanceof Boolean;
            case "Str", "String"                   -> v instanceof String;
            case "Chr", "Char"                     -> v instanceof String s && s.length() == 1;
            case "List"                            -> v instanceof List;
            case "Fun"                             -> v instanceof FunDefNode || v instanceof BoundMethod || v instanceof BuiltInFunctionNode;
            default -> {
                if (v instanceof EnumValue ev) yield ev.type().equals(base) || isAncestor(base, ev.type());
                if (v instanceof Map<?, ?> m) {
                    Object tag = m.get("__class__");
                    if (tag == null) yield true;            // untagged object — accept
                    yield tag.equals(base) || isAncestor(base, tag.toString());
                }
                yield false;
            }
        };
    }

    /** True if `concrete` has `wanted` among its (transitive) ancestors. */
    private boolean isAncestor(String wanted, String concrete) {
        Set<String> anc = ancestors.get(concrete);
        return anc != null && anc.contains(wanted);
    }

    /** Follow a chain of type aliases to the underlying type (generics erased). */
    private String resolveAlias(String type) {
        String t = type; int guard = 0;
        while (typeAliases.containsKey(t) && guard++ < 64) t = stripGenerics(typeAliases.get(t));
        return t;
    }

    /** "List<Int>" -> "List"; a plain type is returned unchanged. */
    private static String stripGenerics(String type) {
        int lt = type.indexOf('<');
        return lt < 0 ? type : type.substring(0, lt);
    }

    /** The first type argument of a generic type, or null if there is none. */
    private static String firstTypeArg(String type) {
        int lt = type.indexOf('<'), gt = type.lastIndexOf('>');
        if (lt < 0 || gt <= lt) return null;
        String inner = type.substring(lt + 1, gt).trim();
        int comma = inner.indexOf(',');
        return (comma < 0 ? inner : inner.substring(0, comma)).trim();
    }

    /**
     * Infer a type name for an inferred (VAR/CONST) binding. The static shape of
     * the value expression is preferred (it distinguishes Chr/Str and Bool, which
     * look identical at runtime); otherwise we fall back to the runtime value.
     */
    private String inferType(Node valueNode, Object val) {
        if (valueNode instanceof CharNode)   return "Chr";
        if (valueNode instanceof StringNode) return "Str";
        if (valueNode instanceof NumberNode nn) {
            String tt = nn.token().type();
            if (tt.equals("BOOL"))  return "Bool";
            if (tt.equals("FLOAT")) return "Dec";
            return "Int";
        }
        if (valueNode instanceof BinOpNode b) {
            String op = b.opToken().type();
            if (op.equals("EE") || op.equals("NE") || op.equals("LT")
                    || op.equals("GT") || op.equals("LTE") || op.equals("GTE"))
                return "Bool";
        }
        if (valueNode instanceof NewNode nw) return (String) nw.classNameToken().value();
        if (valueNode instanceof ListNode)   return "List";
        return typeNameOfValue(val);
    }

    /** Best-effort type name from a runtime value alone. */
    private String typeNameOfValue(Object v) {
        if (v == null) return "Null";
        if (v instanceof EnumValue ev) return ev.type();
        if (v instanceof Double d)
            return (d == Math.floor(d) && !d.isInfinite() && !d.isNaN()) ? "Int" : "Dec";
        if (v instanceof Boolean) return "Bool";
        if (v instanceof String s) return s.length() == 1 ? "Chr" : "Str";
        if (v instanceof List) return "List";
        if (v instanceof Map<?, ?> m) { Object t = m.get("__class__"); return t != null ? t.toString() : "Object"; }
        if (v instanceof FunDefNode || v instanceof BoundMethod || v instanceof BuiltInFunctionNode) return "Fun";
        return "Unknown";
    }
}
