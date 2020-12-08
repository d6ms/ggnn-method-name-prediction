package preprocessor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NodeLabelUtil {

    private static final Map<String, String> shortNames = Collections.unmodifiableMap(new HashMap<>() {
        private static final long serialVersionUID = 1L;

        {
            put("ArrayAccessExpr", "ArAc");
            put("ArrayBracketPair", "ArBr");
            put("ArrayCreationExpr", "ArCr");
            put("ArrayCreationLevel", "ArCrLvl");
            put("ArrayInitializerExpr", "ArIn");
            put("ArrayType", "ArTy");
            put("AssertStmt", "Asrt");
            put("AssignExpr:BINARY_AND", "AsAn");
            put("AssignExpr:ASSIGN", "As");
            put("AssignExpr:LEFT_SHIFT", "AsLS");
            put("AssignExpr:MINUS", "AsMi");
            put("AssignExpr:BINARY_OR", "AsOr");
            put("AssignExpr:PLUS", "AsP");
            put("AssignExpr:REMAINDER", "AsRe");
            put("AssignExpr:SIGNED_RIGHT_SHIFT", "AsRSS");
            put("AssignExpr:UNSIGNED_RIGHT_SHIFT", "AsRUS");
            put("AssignExpr:DIVIDE", "AsSl");
            put("AssignExpr:MULTIPLY", "AsSt");
            put("AssignExpr:XOR", "AsX");
            put("BinaryExpr:AND", "And");
            put("BinaryExpr:BINARY_AND", "BinAnd");
            put("BinaryExpr:BINARY_OR", "BinOr");
            put("BinaryExpr:DIVIDE", "Div");
            put("BinaryExpr:EQUALS", "Eq");
            put("BinaryExpr:GREATER", "Gt");
            put("BinaryExpr:GREATER_EQUALS", "Geq");
            put("BinaryExpr:LESS", "Ls");
            put("BinaryExpr:LESS_EQUALS", "Leq");
            put("BinaryExpr:LEFT_SHIFT", "LS");
            put("BinaryExpr:MINUS", "Minus");
            put("BinaryExpr:NOT_EQUALS", "Neq");
            put("BinaryExpr:OR", "Or");
            put("BinaryExpr:PLUS", "Plus");
            put("BinaryExpr:REMAINDER", "Mod");
            put("BinaryExpr:SIGNED_RIGHT_SHIFT", "RSS");
            put("BinaryExpr:UNSIGNED_RIGHT_SHIFT", "RUS");
            put("BinaryExpr:MULTIPLY", "Mul");
            put("BinaryExpr:XOR", "Xor");
            put("BlockStmt", "Bk");
            put("BooleanLiteralExpr", "BoolEx");
            put("CastExpr", "Cast");
            put("CatchClause", "Catch");
            put("CharLiteralExpr", "CharEx");
            put("ClassExpr", "ClsEx");
            put("ClassOrInterfaceDeclaration", "ClsD");
            put("ClassOrInterfaceType", "Cls");
            put("ConditionalExpr", "Cond");
            put("ConstructorDeclaration", "Ctor");
            put("DoStmt", "Do");
            put("DoubleLiteralExpr", "Dbl");
            put("EmptyMemberDeclaration", "Emp");
            put("EnclosedExpr", "Enc");
            put("ExplicitConstructorInvocationStmt", "ExpCtor");
            put("ExpressionStmt", "Ex");
            put("FieldAccessExpr", "Fld");
            put("FieldDeclaration", "FldDec");
            put("ForeachStmt", "Foreach");
            put("ForStmt", "For");
            put("IfStmt", "If");
            put("InitializerDeclaration", "Init");
            put("InstanceOfExpr", "InstanceOf");
            put("IntegerLiteralExpr", "IntEx");
            put("IntegerLiteralMinValueExpr", "IntMinEx");
            put("LabeledStmt", "Labeled");
            put("LambdaExpr", "Lambda");
            put("LongLiteralExpr", "LongEx");
            put("MarkerAnnotationExpr", "MarkerExpr");
            put("MemberValuePair", "Mvp");
            put("MethodCallExpr", "Cal");
            put("MethodDeclaration", "Mth");
            put("MethodReferenceExpr", "MethRef");
            put("NameExpr", "Nm");
            put("NormalAnnotationExpr", "NormEx");
            put("NullLiteralExpr", "Null");
            put("ObjectCreationExpr", "ObjEx");
            put("Parameter", "Prm");
            put("PrimitiveType", "Prim");
            put("QualifiedNameExpr", "Qua");
            put("ReturnStmt", "Ret");
            put("SimpleName", "Sn");
            put("SingleMemberAnnotationExpr", "SMEx");
            put("StringLiteralExpr", "StrEx");
            put("SuperExpr", "SupEx");
            put("SwitchEntryStmt", "SwiEnt");
            put("SwitchStmt", "Switch");
            put("SynchronizedStmt", "Sync");
            put("ThisExpr", "This");
            put("ThrowStmt", "Thro");
            put("TryStmt", "Try");
            put("TypeDeclarationStmt", "TypeDec");
            put("TypeExpr", "Type");
            put("TypeParameter", "TypePar");
            put("UnaryExpr:BITWISE_COMPLEMENT", "Inverse");
            put("UnaryExpr:MINUS", "Neg");
            put("UnaryExpr:LOGICAL_COMPLEMENT", "Not");
            put("UnaryExpr:POSTFIX_DECREMENT", "PosDec");
            put("UnaryExpr:POSTFIX_INCREMENT", "PosInc");
            put("UnaryExpr:PLUS", "Pos");
            put("UnaryExpr:PREFIX_DECREMENT", "PreDec");
            put("UnaryExpr:PREFIX_INCREMENT", "PreInc");
            put("UnionType", "Unio");
            put("VariableDeclarationExpr", "VDE");
            put("VariableDeclarator", "VD");
            put("VariableDeclaratorId", "VDID");
            put("VoidType", "Void");
            put("WhileStmt", "While");
            put("WildcardType", "Wild");
        }
    });

    public static String splitToSubtokens(String str) {
        str = str.replace("|", " ").trim();
        return Stream.of(str.split("(?<=[a-z])(?=[A-Z])|_|[0-9]|(?<=[A-Z])(?=[A-Z][a-z])|\\s+"))
                .filter(s -> s.length() > 0).map(s -> normalizeName(s, ""))
                .filter(s -> s.length() > 0).collect(Collectors.joining("|"));
    }

    public static String normalizeName(String original, String defaultString) {
        original = original.toLowerCase().replaceAll("\\\\n", "") // escaped new
                // lines
                .replaceAll("//s+", "") // whitespaces
                .replaceAll("[\"',]", "") // quotes, apostrophies, commas
                .replaceAll("\\P{Print}", ""); // unicode weird characters
        String stripped = original.replaceAll("[^A-Za-z]", "");
        if (stripped.length() == 0) {
            String carefulStripped = original.replaceAll(" ", "_");
            if (carefulStripped.length() == 0) {
                return defaultString;
            } else {
                return carefulStripped;
            }
        } else {
            return stripped;
        }
    }

    public static String shortenNodeName(String nodeName) {
        return shortNames.getOrDefault(nodeName, nodeName);
    }
}
