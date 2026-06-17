# MyOpl

A custom programming language built from scratch in Java. MyOpl ships with a hand-written lexer, recursive-descent parser, and tree-walking interpreter, plus a REPL and a script runner.

## Features

- **A type for every variable** — five built-in types `Int`, `Dec`, `Str`, `Chr`, `Bool`, plus `List`, any class name as a type, and `Any`:
  - **Typed declarations**: `Int x = 5`, `Str s = "hi"`, `Bool ok = TRUE`
  - **Inferred types**: `VAR` works like Java's `var` (infers the type); `CONST` is the same but immutable
  - **Typed function parameters**: `FUN add(Int a, Int b) -> a + b`
  - **`typeOf(value)`** returns a value's type name; mismatched assignments/arguments raise a `Type error`
  - Each built-in type has an auto-loaded companion class (`Int`, `Dec`, `Str`, `Chr`, `Bool`) of useful helpers
- **Functions** with `FUN name(Type arg) -> body` or a `BEGIN ... END` block with `RETURN`
- **Classes** with `CLASS Name BEGIN ... END`, `INIT` constructors, and `NEW` instances
- **Custom types**:
  - **Type aliases**: `ALIAS Money = Dec`
  - **Enums**: `ENUM Color BEGIN RED, GREEN, BLUE END` (or `{ RED, GREEN }`)
  - **Inheritance**: `CLASS Dog EXTENDS Animal BEGIN … END` — subclasses override methods and satisfy a supertype parameter
  - **Interfaces**: `INTERFACE Named BEGIN … END` + `CLASS Robot IMPLEMENTS Named`
  - **Generics**: `List<Int> xs = [1, 2, 3]` — element types are checked; use `Any` for mixed contents
- **Modules**: `IMPORT "path/to/file.myopl"` merges another file's symbols into the current scope
- **Control flow**: `IF / THEN / ELSE`, `FOR x = a TO b STEP s THEN`, `WHILE cond THEN`
- **Booleans**: `TRUE` / `FALSE` keywords (typed `Bool`)
- **Strings & chars**: the full set of Java escape sequences — `\t \b \n \r \f \s \' \" \\`, unicode `\uXXXX` and octal `\ddd`
- **Blocks**: `BEGIN ... END` for multi-statement bodies
- **Comments**: single-line `# ...` and multi-line `#* ... *#`
- **Built-in I/O**: `PRINT`, `LEN`, `READ_FILE`, `WRITE_FILE`, `APPEND_FILE`,
  and console input `INPUT`, `INPUT_NUM`, plus the typed readers `INPUT_STR`, `INPUT_INT`, `INPUT_DEC`, `INPUT_BOOL`, `INPUT_CHR`

See [`src/main/java/Grammar.txt`](src/main/java/Grammar.txt) for the full language specification.

## Quick example

```
# Typed variables (VAR / CONST infer the type, like Java's var)
Int x = 10
Dec y = (x * 5) / 2
VAR label = "y ="          # inferred as Str
PRINT(label, y, typeOf(y)) # y = 25.0 Dec

# Functions with typed parameters
FUN square(Int n) -> n * n
PRINT(square(7))

# Classes
CLASS MathUtils BEGIN
    FUN double(Int n) -> n * 2
    VAR PI = 3.14159
END
PRINT(MathUtils.double(7))
PRINT(MathUtils.PI)

# Loops
FOR i = 1 TO 3 THEN PRINT("Count:", i)

# Typed input
# Int age = Int.input("Age? ")
# PRINT("Next year you will be", age + 1)

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
