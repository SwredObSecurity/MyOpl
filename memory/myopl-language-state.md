---
name: myopl-language-state
description: Current state of the MyOpl language work — features added, SCL library, and non-obvious constraints
metadata:
  type: project
---

MyOpl is a Java tree-walking interpreter (Lexer→Parser→Interpreter) the user is actively extending. Repo: https://github.com/SwredObSecurity/MyOpl (branch main, direct-to-main commits, solo dev). Build/run: see [[build-run-myopl]].

**Features added in recent sessions (all on main, tested):**
- `CONST` constants; unary minus; `'x'` char literals; `==`/`!=` work on text not just numbers.
- `BEGIN ... END` required for multi-line function bodies; `else if` works via recursive parsing.
- Objects: `INIT(args)` constructor (stored as `__init__`) + `NEW ClassName(args)` instantiation. Each instance is its own Map scope.
- **Instance/class method field writes now PERSIST across calls** (mutable object state) — enables stateful objects like the PRNG seed.
- Fixed an operator-precedence bug: additive (`+`/`-`) now binds tighter than comparison (`i + 1 <= x` → `(i+1) <= x`).

**SCL standard library (src/main/java/SCL/):**
- `Math.myopl` (AUTO-LOADED): pi/e/tau/etc, abs, sign, min, max, clamp, negate, half, square, cube, average, isPos/isNeg/isZero, lerp, inverseLerp, remap.
- `Extras/` (NOT auto-loaded — need explicit `IMPORT "SCL/Extras/X.myopl"`): `Random.myopl` (seedable LCG PRNG), `List.myopl` (ArrayList-like linked list), `File.myopl` (path wrapper over READ/WRITE/APPEND_FILE built-ins), `Console.myopl` (Scanner+System.out wrapper).

**Non-obvious constraints (matter for any new SCL class):**
- `Files.list` in Shell auto-load is NON-recursive → only top-level `SCL/*.myopl` auto-loads; `SCL/Extras/*` must be IMPORTed.
- The language has NO list indexing and NO list mutation (only `[literal]` and `LEN`) — that's why List.myopl is a linked list of `ListNode` objects.
- There is NO `obj.field = value` syntax. A method mutates only its OWN object's fields via `VAR field = ...`; to change another object you call a setter method on it (e.g. `node.setNext(x)`).
- No `%` modulo, no `floor`, no system clock. Random.myopl works around this (modulo by subtraction, floor by counting loop, deterministic per-seed).

Docs live in `src/main/java/Grammar.txt`; the runnable feature tour is `src/main/java/test.myopl`. Note: user sometimes sets files read-only via IntelliJ (clear with `(Get-Item path).IsReadOnly=$false`).

**No open/pending task** as of last session — user closed the window after all work was committed and pushed (last commit d8d4779).
