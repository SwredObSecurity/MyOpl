package lang;
import java.util.List;

public sealed interface Node permits
        NumberNode, StringNode, BinOpNode, UnaryOpNode, VarAccessNode,
        VarAssignNode, FunDefNode, CallNode, ReturnNode, IfNode, ForNode,
        WhileNode, BlockNode, ListNode, BuiltInFunctionNode,
        ClassDefNode, ImportNode, MemberAccessNode {
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

record VarAssignNode(Token varNameToken, Node valueNode, boolean isConst) implements Node {
    public Position posStart() { return varNameToken.posStart(); }
    public Position posEnd()   { return valueNode.posEnd();      }
}

record FunDefNode(Token varNameToken, List<Token> argNameTokens, Node bodyNode) implements Node {
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
 */
record ClassDefNode(Token nameToken, Node body, Position posStart, Position posEnd) implements Node {}

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
