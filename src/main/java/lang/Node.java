package lang;
import java.util.List;

public sealed interface Node permits
        NumberNode, StringNode, CharNode, BinOpNode, UnaryOpNode, VarAccessNode,
        VarAssignNode, FunDefNode, CallNode, ReturnNode, IfNode, ForNode,
        WhileNode, BlockNode, ListNode, BuiltInFunctionNode,
        ClassDefNode, ImportNode, MemberAccessNode, NewNode,
        EnumDefNode, AliasNode {
    Position posStart();
    Position posEnd();
}

record NumberNode(Token token) implements Node {
    public Position posStart() { return token.posStart(); }
    public Position posEnd()   { return token.posEnd();   }
}

record StringNode(Token token) implements Node {
    public Position posStart() { return token.posStart(); }
    public Position posEnd()   { return token.posEnd();   }
}

/** A single-character literal written with single quotes:  'x'  '\n'. */
record CharNode(Token token) implements Node {
    public Position posStart() { return token.posStart(); }
    public Position posEnd()   { return token.posEnd();   }
}

record BinOpNode(Node leftNode, Token opToken, Node rightNode) implements Node {
    public Position posStart() { return leftNode.posStart(); }
    public Position posEnd()   { return rightNode.posEnd();  }
}

record UnaryOpNode(Token opToken, Node node) implements Node {
    public Position posStart() { return opToken.posStart(); }
    public Position posEnd()   { return node.posEnd();      }
}

record VarAccessNode(Token varNameToken) implements Node {
    public Position posStart() { return varNameToken.posStart(); }
    public Position posEnd()   { return varNameToken.posEnd();   }
}

/**
 * A variable / constant declaration.
 *   typeToken == null  -> inferred type (the VAR keyword, or an inferred CONST):
 *                         works like Java's `var`, the type is read from the value.
 *   typeToken != null  -> an explicit type annotation, e.g. `Int x = 5`. typeToken
 *                         holds the type name ("Int", "Str", a class name, "Any", ...).
 * isConst marks an immutable binding (CONST) that cannot be reassigned.
 */
record VarAssignNode(Token varNameToken, Node valueNode, boolean isConst, Token typeToken) implements Node {
    public Position posStart() { return varNameToken.posStart(); }
    public Position posEnd()   { return valueNode.posEnd();      }
}

/**
 * A function (or INIT constructor) definition.
 * argNameTokens and argTypeTokens are parallel lists: argTypeTokens.get(i) is the
 * declared type of parameter argNameTokens.get(i) (its value() is the type name).
 */
record FunDefNode(Token varNameToken, List<Token> argNameTokens, List<Token> argTypeTokens, Node bodyNode) implements Node {
    public Position posStart() { return varNameToken != null ? varNameToken.posStart() : bodyNode.posStart(); }
    public Position posEnd()   { return bodyNode.posEnd(); }
}

record CallNode(Node nodeToCall, List<Node> argNodes, Position posStart, Position posEnd) implements Node {}
record ReturnNode(Node nodeToReturn, Position posStart, Position posEnd) implements Node {}

record IfNode(Node condition, Node thenCase, Node elseCase) implements Node {
    public Position posStart() { return condition.posStart(); }
    public Position posEnd()   { return elseCase != null ? elseCase.posEnd() : thenCase.posEnd(); }
}

record ForNode(Token varName, Node start, Node end, Node step, Node body) implements Node {
    public Position posStart() { return varName.posStart(); }
    public Position posEnd()   { return body.posEnd(); }
}

record WhileNode(Node condition, Node body) implements Node {
    public Position posStart() { return condition.posStart(); }
    public Position posEnd()   { return body.posEnd(); }
}

record BlockNode(List<Node> statements, Position posStart, Position posEnd) implements Node {}
record ListNode(List<Node> elementNodes, Position posStart, Position posEnd) implements Node {}

record BuiltInFunctionNode(String name) implements Node {
    public Position posStart() { return null; }
    public Position posEnd()   { return null; }
}

// ── New nodes ──────────────────────────────────────────────────────────────

/**
 * CLASS ClassName BEGIN ... END
 * Defines a named class scope. Functions and variables declared inside
 * become members accessible via dot notation: ClassName.member
 *
 * A class may extend one superclass and implement any number of interfaces:
 *   CLASS Dog EXTENDS Animal IMPLEMENTS Named, Comparable BEGIN ... END
 * superToken is null when there is no EXTENDS clause; interfaceTokens is empty
 * when there is no IMPLEMENTS clause. isInterface marks an INTERFACE definition.
 */
record ClassDefNode(Token nameToken, Token superToken, java.util.List<Token> interfaceTokens,
                    boolean isInterface, Node body, Position posStart, Position posEnd) implements Node {}

/**
 * ENUM Name BEGIN A, B, C END   (or  ENUM Name { A, B, C })
 * Defines an enumeration type. Each member becomes a distinct EnumValue,
 * accessible as Name.MEMBER, that prints as its own name and reports its type
 * via typeOf as the enum's name.
 */
record EnumDefNode(Token nameToken, List<Token> memberTokens, Position posStart, Position posEnd) implements Node {}

/**
 * ALIAS Name = ExistingType
 * Registers Name as another name for an existing type (e.g. ALIAS Money = Dec).
 * The two names are fully interchangeable in declarations and parameters.
 */
record AliasNode(Token nameToken, Token targetToken) implements Node {
    public Position posStart() { return nameToken.posStart();   }
    public Position posEnd()   { return targetToken.posEnd();   }
}

/**
 * IMPORT "path/to/file.myopl"
 * Runs the target file in a sub-interpreter and merges every class (and
 * other top-level symbol) it defines into the current scope — equivalent
 * to pasting that file's source right below the current class declaration.
 */
record ImportNode(Token pathToken) implements Node {
    public Position posStart() { return pathToken.posStart(); }
    public Position posEnd()   { return pathToken.posEnd();   }
}

/**
 * objectNode.memberToken
 * Resolves a class member: looks up objectNode in the symbol table (must
 * be a class Map) then returns the named member from that map.
 */
record MemberAccessNode(Node objectNode, Token memberToken) implements Node {
    public Position posStart() { return objectNode.posStart();    }
    public Position posEnd()   { return memberToken.posEnd(); }
}

/**
 * NEW ClassName(args)
 * Instantiates a class: copies the class scope into a fresh per-instance map,
 * runs the class's INIT constructor (if any) with the given args to populate
 * the instance's fields, then yields the instance. Methods called on the
 * instance (instance.method()) see that instance's own fields.
 */
record NewNode(Token classNameToken, List<Node> argNodes, Position posStart, Position posEnd) implements Node {}
