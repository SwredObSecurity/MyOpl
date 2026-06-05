---
name: build-run-myopl
description: How to compile and run the MyOpl interpreter for testing (no Maven on PATH)
metadata:
  type: project
---

Maven is NOT on PATH on this machine (`mvn` fails); `javac`/`java` (JDK 25) are available. A `pom.xml` exists but can't be used directly.

**Compile + run the way the project actually runs (RUN.bat):** classes live alongside sources in `src/main/java`, and the working dir + classpath must be `src/main/java` so the SCL auto-loader finds the `SCL/` folder.

PowerShell compile (in-place):
```
$j = "D:\Java Coding Nafi\Coding Language - Source Java\src\main\java"
$src = Get-ChildItem -Path $j -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -d $j $src
```

Run a script (Shell reads the file path from stdin):
```
printf '%s\n' 'mytest.myopl' | java -cp "D:\Java Coding Nafi\Coding Language - Source Java\src\main\java" Shell
```

**SCL gotcha:** `Shell.loadStandardClassLibrary` locates `SCL/` by walking up from the class location and falling back to `cwd/SCL`. If you run from `target/classes`, it won't find `src/main/java/SCL`, so `Math` etc. won't load (you'll get "Not a class: cannot access '.abs'"). Always run from `src/main/java`.

Note `SCL/*.myopl` files are loaded at runtime but were historically untracked in git; `SCL/Math.myopl` is now committed.
