import lang.*;
import java.util.*;
import java.nio.file.*;
import java.util.stream.Stream;

public class Shell {
    private static final Interpreter interpreter = new Interpreter();
    private static final String SCL_DIR_NAME = "SCL";
    private static final String STD_DIR_NAME = "STD";

    public static void main(String[] args) {
        loadStandardClassLibrary();
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

    private static void loadStandardClassLibrary() {
        Path sclDir = locateSclDir();
        if (sclDir == null || !Files.isDirectory(sclDir)) return;
        // Keep the whole SCL tree as the import-search root so explicit
        // IMPORTs (List, Random, File, ...) still resolve from any script.
        Interpreter.setSclRoot(sclDir.toString());
        // Auto-load only the STD subdirectory; everything else needs IMPORT.
        Path stdDir = sclDir.resolve(STD_DIR_NAME);
        if (!Files.isDirectory(stdDir)) return;
        interpreter.setBaseDir(stdDir.toString());
        try (Stream<Path> stream = Files.list(stdDir)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".myopl"))
                  .sorted()
                  .forEach(Shell::loadSclFile);
        } catch (Exception e) {
            System.out.println("SCL scan error: " + e.getMessage());
        }
    }

    private static void loadSclFile(Path file) {
        try {
            String code = Files.readString(file);
            execute(code, false);
        } catch (Exception e) {
            System.out.println("SCL load error (" + file.getFileName() + "): " + e.getMessage());
        }
    }

    private static Path locateSclDir() {
        try {
            Path origin = Path.of(Shell.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(origin)) origin = origin.getParent();
            for (Path p = origin; p != null; p = p.getParent()) {
                Path candidate = p.resolve(SCL_DIR_NAME);
                if (Files.isDirectory(candidate)) return candidate;
            }
        } catch (Exception ignored) {}
        Path cwd = Path.of("").toAbsolutePath().resolve(SCL_DIR_NAME);
        return Files.isDirectory(cwd) ? cwd : null;
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
