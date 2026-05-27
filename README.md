# MyOpl

A custom programming language built from scratch in Java. MyOpl ships with a hand-written lexer, recursive-descent parser, and tree-walking interpreter, plus a REPL and a script runner.

## Features

- **Variables** with `VAR name = value`
- **Functions** with `FUN name(args) -> body` (single-expression arrow form)
- **Classes** with `CLASS Name BEGIN ... END`, accessed via dot notation
- **Modules**: `IMPORT "path/to/file.myopl"` merges another file's symbols into the current scope
- **Control flow**: `IF / THEN / ELSE`, `FOR x = a TO b STEP s THEN`, `WHILE cond THEN`
- **Data types**: numbers, strings, lists, booleans (via comparison)
- **Blocks**: `BEGIN ... END` for multi-statement bodies
- **Comments**: single-line `# ...` and multi-line `#* ... *#`
- **Built-in I/O**: `PRINT`, `INPUT`, `INPUT_NUM`, `READ_FILE`, `WRITE_FILE`, `APPEND_FILE`, `LEN`

See [`src/main/java/Grammar.txt`](src/main/java/Grammar.txt) for the full language specification.

## Quick example

```
# Variables and math
VAR x = 10
VAR y = (x * 5) / 2
PRINT("y =", y)

# Functions
FUN square(n) -> n * n
PRINT(square(7))

# Classes
CLASS MathUtils BEGIN
    FUN double(n) -> n * 2
    VAR PI = 3.14159
END
PRINT(MathUtils.double(7))
PRINT(MathUtils.PI)

# Loops
FOR i = 1 TO 3 THEN PRINT("Count:", i)

# File I/O
WRITE_FILE("out.txt", "Hello from MyOPL!")
PRINT(READ_FILE("out.txt"))
```

## Running

Requires Java 25.

### Using Maven

```bash
mvn compile
cd target/classes
java Shell           # opens REPL
java Shell script.myopl   # runs a file
```

### Without Maven

```bash
cd src/main/java
javac Shell.java lang/*.java
java Shell           # opens REPL
```

On Windows, `src/main/java/RUN.bat` compiles and launches the shell in one step.

## Project layout

```
src/main/java/
  Shell.java          # REPL + file runner entry point
  Grammar.txt         # language specification
  lang/
    Lexer.java        # source -> tokens
    Parser.java       # tokens -> AST
    Interpreter.java  # AST -> values
    Node.java, Token.java, Position.java
  test.myopl          # demo script exercising the whole language
  MathHelper.myopl    # example module imported by test.myopl
src/main/resources/
  py-myopl-code-master/   # original Python reference (David Callanan, MIT)
```

## Credits

The language design follows the structure of David Callanan's "Make YOUR OWN Programming Language" tutorial series ([CodePulse](https://github.com/davidcallanan/py-myopl-code)) — the Python reference is bundled under `src/main/resources/py-myopl-code-master`. The Java implementation, class system, module system, and built-in I/O functions are original work.
