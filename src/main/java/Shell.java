import lang.*;
import java.util.*;
import java.nio.file.*;

public class Shell {
    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("File path (blank for REPL): ");
        String path = sc.hasNextLine() ? sc.nextLine() : "";
        path = path.trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        if (!path.isBlank()) {
            try {
                Path filePath = Path.of(path).toAbsolutePath();
                String code   = Files.readString(filePath);
                String dir    = filePath.getParent() != null
                        ? filePath.getParent().toString() : ".";
                interpreter.setBaseDir(dir);
                execute(code, false);
            } catch (Exception e) { System.out.println("File Error: " + e.getMessage()); }
        } else {
            interpreter.setBaseDir(".");
            while (true) {
                System.out.print("myopl > ");
                if (!sc.hasNextLine()) break;
                String in = sc.nextLine();
                if (in.equalsIgnoreCase("exit")) break;
                if (!in.isBlank()) execute(in, true);
            }
        }
    }

    private static void execute(String code, boolean printResult) {
        try {
            List<Token> tokens = new Lexer("<src>", code).makeTokens();
            Node ast = new Parser(tokens).parse();
            if (ast != null) {
                Object result = interpreter.visit(ast);
                if (printResult && result != null) System.out.println(result);
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
    }
}
