package org.example.core;

import jadx.api.*;
import jadx.api.utils.CodeUtils;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.visitors.AbstractVisitor;
import jadx.core.dex.visitors.IDexTreeVisitor;
import jadx.core.dex.visitors.typeinference.TypeInferenceVisitor;
import org.example.data.MethodSource;
import org.example.data.SearchResult;
import org.example.util.MainUtils;
import org.example.util.jadx.CodeNode;
import org.example.util.jadx.CodeSearchProvider;
import org.example.util.jadx.SearchSettings;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.core.JavaParser.getLineAtOffset;
import static org.example.util.MainUtils.*;

public class JadxMainEngine {
    private final static int maxUsagePositionNum = 30;
    static List<BlockNode> basicBlocks;
    static String mthNodeFullId;

    public static JavaNode searchJavaNode(JadxDecompiler jadx, SearchResult searchResult, String keyword) {
        return searchJavaNode(jadx, searchResult, keyword, searchResult.getLine());
    }

    public static JavaNode searchJavaNode(JadxDecompiler jadx, SearchResult searchResult, String keyword, String loc) {
        int additionalIndex = 0;
        loc = loc.replaceFirst("^(android|java)(\\.[a-z][a-zA-Z]*)+\\.[A-Z][a-zA-Z]* ", "");
        int index = getCodeOffset(searchResult, keyword, additionalIndex, loc);
        JavaNode javaNode = getJavaNodeAtOffset(jadx, searchResult.getRootCls(), index, keyword);
        return javaNode;
    }

    public static SearchResult goToDeclaration(JavaNode javaNode) {
        if (javaNode instanceof JavaMethod javaMethod) {
            String methodSignature = getMethodSignature(javaMethod);
            JavaClass rootCLS = javaMethod.getTopParentClass();
            String clsSourceCode = rootCLS.getCode();
            int defPos = javaMethod.getDefPos();
            MethodSource mthSrc = getMethodSourceAtPosInClsCode(clsSourceCode, defPos);
            if (mthSrc == null)
                return null;
            return new SearchResult(mthSrc.getStartOffset(), mthSrc.getEndOffset(), mthSrc.getFirstLine(), mthSrc.getMthSrc(), clsSourceCode, rootCLS, methodSignature);
        } else if (javaNode instanceof JavaField javaField) {
            for (JavaField javaField1 : javaField.getTopParentClass().getFields()) {
                if (javaField1.getFullName().equals(javaField.getFullName())) {
                    String defLine = getLineAtOffset(javaField1.getTopParentClass().getCode(), javaField1.getDefPos());
                    return new SearchResult(javaField1.getDefPos(), -1, defLine, defLine, javaField1.getTopParentClass().getCode(), javaField1.getTopParentClass(), javaField1.getDeclaringClass().getFullName());
                }
            }
            String defLine = getLineAtOffset(javaField.getTopParentClass().getCode(), javaField.getDefPos());
            return new SearchResult(javaField.getDefPos(), -1, defLine, defLine, javaField.getTopParentClass().getCode(), javaField.getTopParentClass(), javaField.getDeclaringClass().getFullName());
        } else if (javaNode instanceof JavaClass javaClass) {
            String clsCode = javaClass.getCode();
            String defLine = getLineAtOffset(javaClass.getTopParentClass().getCode(), javaClass.getDefPos());
            return new SearchResult(-1, -1, defLine, clsCode, javaClass.getCode(), javaClass, javaClass.getFullName());
        }
        return null;
    }

    public static String removeMetaDataInClsCode(String clsCode) {
        String redundantLine = "@kotlin.Metadata(";
        clsCode = Arrays.stream(clsCode.split("\n"))
                .filter(line -> !line.contains(redundantLine))
                .collect(Collectors.joining("\n")); // remove redundant meta data
        return clsCode;
    }

    public static JavaNode getJavaNodeAtOffset(JadxDecompiler jadx, JavaClass cls, int offset, String keyword) {
        if (offset == -1) {
            return null;
        }
        try {
            JavaNode javaNode = jadx.getJavaNodeAtPosition(cls.getCodeInfo(), offset);
            if (javaNode == null) {
                javaNode = jadx.getClosestJavaNode(cls.getCodeInfo(), offset);
                if (javaNode != null && !javaNode.getFullName().contains(keyword))
                    javaNode = null;
            }
            return javaNode;
        } catch (Exception e) {
            mainLog("ERROR", "GetJavaNode", "Can't get java node by offset: {}" + offset + e);
        }
        return null;
    }

    public static List<SearchResult> getUsageOfJavaNode(JadxDecompiler jadx, JavaNode javaNode) {
        Map<JavaNode, List<? extends JavaNode>> map = new HashMap<>();
        if (javaNode instanceof JavaField javaField) {
            map.put(javaField, javaField.getUseIn());
        } else if (javaNode instanceof JavaVariable javaVariable) {
            map.put(javaVariable, javaVariable.getUseIn());
        } else if (javaNode instanceof JavaMethod javaMethod) {
            for (JavaMethod mth : getMethodWithOverrides(javaMethod)) {
                map.put(mth, mth.getUseIn());
            }
        }
        List<SearchResult> searchResults = new ArrayList<>();
        map.forEach(
                (searchNode, useNodes) -> useNodes.stream()
                        .map(JavaNode::getTopParentClass)
                        .distinct()
                        .forEach(u -> searchResults.addAll(processUsage(jadx, searchNode, u))));
        return searchResults;
    }

    private static List<SearchResult> processUsage(JadxDecompiler jadx, JavaNode searchNode, JavaClass topUseClass) {
        ICodeInfo codeInfo = topUseClass.getCodeInfo();
        List<Integer> usePositions = topUseClass.getUsePlacesFor(codeInfo, searchNode);
        usePositions = usePositions.subList(0, Math.min(usePositions.size(), maxUsagePositionNum));

        if (usePositions.isEmpty()) {
            return new ArrayList<>();
        }
        String code = codeInfo.getCodeStr();

        List<SearchResult> lines = new ArrayList<>();
        for (int pos : usePositions) {
            String line = CodeUtils.getLineForPos(code, pos);
            if (line.startsWith("import ")) {
                continue;
            }
            JavaNode useNode = jadx.getEnclosingNode(codeInfo, pos);
            assert useNode != null;
            String mthSign;
            String clsSrc = topUseClass.getCode();
            if (useNode instanceof JavaMethod javaMethod) {
                mthSign = MainUtils.getMethodSignature(javaMethod);
                int defPos = javaMethod.getDefPos();
                MethodSource methodSource = getMethodSourceAtPosInClsCode(clsSrc, defPos);
                if (methodSource != null)
                    lines.add(new SearchResult(methodSource.getStartOffset(), methodSource.getEndOffset(), line.trim(), methodSource.getMthSrc(), clsSrc, topUseClass, mthSign));
            } else if (useNode instanceof JavaField javaField) {
                lines.add(new SearchResult(javaField.getDefPos(), -1, line.trim(), line.trim(), clsSrc, topUseClass, "<" + javaField.getDeclaringClass().getFullName() + "：" + javaField.getDefPos() + ">"));
            } else if (useNode instanceof JavaVariable javaVariable) {
                mthSign = MainUtils.getMethodSignature(javaVariable.getMth());
                int defPos = javaVariable.getMth().getDefPos();
                MethodSource methodSource = getMethodSourceAtPosInClsCode(clsSrc, defPos);
                if (methodSource != null)
                    lines.add(new SearchResult(methodSource.getStartOffset(), methodSource.getEndOffset(), line.trim(), methodSource.getMthSrc(), clsSrc, topUseClass, mthSign));
            }
        }
        return lines;
    }

    private static List<JavaMethod> getMethodWithOverrides(JavaMethod javaMethod) {
        List<JavaMethod> relatedMethods = javaMethod.getOverrideRelatedMethods();
        if (!relatedMethods.isEmpty()) {
            return relatedMethods;
        }
        return Collections.singletonList(javaMethod);
    }

    public static void addCustomPassAfter(List<IDexTreeVisitor> passes, Class<?> passCls, IDexTreeVisitor customPass) {
        for (int i = 0; i < passes.size(); i++) {
            IDexTreeVisitor pass = passes.get(i);
            if (pass.getClass().equals(passCls)) {
                passes.add(i + 1, customPass);
                break;
            }
        }
    }

    public static Map<String, List<SearchResult>> getUsageByMthSign(JadxDecompiler jadx, Map<String, String[]> mthSignTypeMap) {
        List<IDexTreeVisitor> passes = jadx.getRoot().getPasses();
        IDexTreeVisitor customPass = new AbstractVisitor() {
            @Override
            public void visit(MethodNode mth) {
                if (mth.isNoCode()) {
                    return;
                }
                if (mthNodeFullId != null && mth.getMethodInfo().getFullId().equals(mthNodeFullId))
                    basicBlocks = mth.getBasicBlocks();
            }
        };
        addCustomPassAfter(passes, TypeInferenceVisitor.class, customPass);

        Map<String, Set<String>> targetMthMap = new HashMap<>();
        Map<String, List<SearchResult>> resultMap = new HashMap<>();
        for (String[] strArray : mthSignTypeMap.values()) {
            String targetMethodSign = strArray[1];
            boolean isStatic = strArray[0].equals("STATIC");
            String mthSearchName = isStatic ? parseMethodSignature(targetMethodSign).getMethodName() + "(" : "." + parseMethodSignature(targetMethodSign).getMethodName() + "(";
            Set<String> targetMethodSigns = targetMthMap.get(mthSearchName);
            if (targetMethodSigns == null)
                targetMethodSigns = new HashSet<>();
            targetMethodSigns.add(targetMethodSign);
            targetMthMap.put(mthSearchName, targetMethodSigns);
            List<SearchResult> searchResults = new ArrayList<>();
            resultMap.put(targetMethodSign, searchResults);
        }

        for (String mthSearchName : targetMthMap.keySet()) {
            Set<String> targetMethodSigns = targetMthMap.get(mthSearchName);
            SearchSettings searchSettings = new SearchSettings(mthSearchName, false, false);
            CodeSearchProvider codeSearchProvider = new CodeSearchProvider(jadx, searchSettings);
            List<CodeNode> codeNodes = codeSearchProvider.searchAll();
            for (CodeNode codeNode : codeNodes) {
                if (!(codeNode.getJavaNode() instanceof JavaMethod javaMethod))
                    continue;
                MethodNode methodNode = javaMethod.getMethodNode();
                List<BlockNode> blockNodes = methodNode.getBasicBlocks();
                if (blockNodes == null) {
                    basicBlocks = null;
                    mthNodeFullId = methodNode.getMethodInfo().getFullId();
                    codeNode.getRootCls().unload();
                    codeNode.getRootCls().decompile();
                    blockNodes = basicBlocks;
                    mthNodeFullId = null;
                }
                if (blockNodes == null)
                    continue;
                for (BlockNode blockNode : blockNodes){
                    List<InsnNode> insnNodes = blockNode.getInstructions();
                    if (insnNodes == null) {
                        continue;
                    }
                    for (InsnNode insnNode : insnNodes) {
                        String insnNodeStr = insnNode.toString();
                        if (!insnNodeStr.contains(mthSearchName))
                            continue;
                        String foundMthSign = containsWhichMth(insnNodeStr, targetMethodSigns);
                        if (foundMthSign == null)
                            continue;

                        String callerMthSign = getMethodSignature(javaMethod);
                        String clsSourceCode = javaMethod.getTopParentClass().getCode();
                        String id1 = methodNode.getMethodInfo().getFullId();
                        int defPos = -1;
                        for (JavaMethod javaMethod2 : javaMethod.getTopParentClass().getMethods()) {
                            String id2 = javaMethod2.getMethodNode().getMethodInfo().getFullId();
                            if (id2.equals(id1)) {
                                defPos = javaMethod2.getDefPos();
                                break;
                            }
                        }
                        if (defPos == -1) {
                            for (JavaMethod javaMethod2 : javaMethod.getDeclaringClass().getMethods()) {
                                String id2 = javaMethod2.getMethodNode().getMethodInfo().getFullId();
                                if (id2.equals(id1)) {
                                    defPos = javaMethod2.getDefPos();
                                    break;
                                }
                            }
                        }
                        if (defPos == -1)
                            continue;
                        MethodSource methodSource = getMethodSourceAtPosInClsCode(clsSourceCode, defPos);
                        if (methodSource != null) {
                            SearchResult searchResult = new SearchResult(methodSource.getStartOffset(), methodSource.getEndOffset(), codeNode.getLine(), methodSource.getMthSrc(), clsSourceCode, javaMethod.getTopParentClass(), callerMthSign);
                            List<SearchResult> searchResults = resultMap.get(foundMthSign);
                            searchResults.add(searchResult);
                            break;
                        }
                    }
                }
            }

        }
        return resultMap;
    }

    public static int getCodeOffset(SearchResult searchResult, String keyword, int additionalIndex, String loc) {
        String sourceCode;
        if (searchResult.getEndOffset() == -1) {
            sourceCode = searchResult.getRootClsSrc();
        } else {
            sourceCode = searchResult.getRootClsSrc().substring(searchResult.getStartOffset(), searchResult.getEndOffset());
        }
        int preIndex = sourceCode.indexOf(loc);
        int index = loc.contains(keyword) ? sourceCode.indexOf(keyword, preIndex) : sourceCode.indexOf(keyword, additionalIndex);
        boolean endsWithLeftParenthesis = keyword.endsWith("(");
        while (index != -1) {
            if (!endsWithLeftParenthesis && index + keyword.length() < sourceCode.length()) {
                char charAfterKeyword = sourceCode.charAt(index + keyword.length());
                if (Character.isLetterOrDigit(charAfterKeyword)) {
                    index = sourceCode.indexOf(keyword, index + 1);
                    continue;
                }
            }
            break;
        }
        if (index == -1) {
            return -1;
        }
        int subOffset = getSubOffset(keyword);
        if (searchResult.getEndOffset() == -1) {
            return index + subOffset;
        } else {
            return index + subOffset + searchResult.getStartOffset();
        }
    }

    public static int getSubOffset(String keyword) {
        if (keyword.endsWith("("))
            keyword += ")";
        int balance = 0;
        int methodStart = -1;

        for (int i = keyword.length() - 1; i >= 0; i--) {
            char ch = keyword.charAt(i);
            if (ch == ')') {
                balance++;
            } else if (ch == '(') {
                balance--;
            } else if ((ch == '.' || ch == ' ') && balance == 0) {
                methodStart = i + 1;
                break;
            }
        }

        if (methodStart == -1) {
            for (int i = 0; i < keyword.length(); i++) {
                char ch = keyword.charAt(i);
                if (ch == '(') {
                    balance++;
                } else if (ch == ')') {
                    balance--;
                } else if (balance == 0 && !Character.isWhitespace(ch)) {
                    methodStart = i;
                    break;
                }
            }
        }

        return methodStart;
    }

    public static MethodSource getMethodSourceAtPosInClsCode(String classCode, int defPos) {
        int start = defPos;
        while (start > 0 && classCode.charAt(start) != '\n') {
            start--;
        }
        start++;

        int end = defPos;
        while (end < classCode.length() && classCode.charAt(end) != '\n') {
            end++;
        }
        end--;

        if (classCode.charAt(end) != '{') {
            mainLog("WARN", "getMethodSourceAtPosInClsCode", "defPos is not at the position of a left brace '{'.");
            return null;
        }

        int braceCounter = 1;

        boolean inSingleLineComment = false;
        boolean inMultiLineComment = false;
        boolean inStringLiteral = false;
        boolean inCharLiteral = false;
        char prevChar = 0;

        while (braceCounter > 0 && end < classCode.length()) {
            end++;
            char c = classCode.charAt(end);

            if (c == '"' && !inSingleLineComment && !inMultiLineComment && !inCharLiteral && prevChar != '\\') {
                inStringLiteral = !inStringLiteral;
            }

            if (c == '\'' && !inSingleLineComment && !inMultiLineComment && !inStringLiteral && prevChar != '\\') {
                inCharLiteral = !inCharLiteral;
            }

            if (c == '/' && prevChar == '/' && !inStringLiteral && !inMultiLineComment && !inCharLiteral) {
                inSingleLineComment = true;
            }
            if (c == '\n' && inSingleLineComment) {
                inSingleLineComment = false;
            }

            if (c == '*' && prevChar == '/' && !inStringLiteral && !inSingleLineComment && !inCharLiteral) {
                inMultiLineComment = true;
            }
            if (c == '/' && prevChar == '*' && inMultiLineComment) {
                inMultiLineComment = false;
            }

            if (!inStringLiteral && !inSingleLineComment && !inMultiLineComment && !inCharLiteral) {
                if (c == '{') {
                    braceCounter++;
                } else if (c == '}') {
                    braceCounter--;
                }
            }

            prevChar = c;
        }

        if (braceCounter != 0) {
            mainLog("WARN", "getMethodSourceAtPosInClsCode", "No matching right brace '}' found.");
            return null;
        }

        String methodCode = classCode.substring(start, end + 1);

        int firstLineIndent = 0;
        while (start + firstLineIndent < classCode.length() && classCode.charAt(start + firstLineIndent) == ' ') {
            firstLineIndent++;
        }

        String[] lines = methodCode.split("\n");
        StringBuilder formattedMethodCode = new StringBuilder();
        String firstLine = null;
        for (String line : lines) {
            int currentIndent = 0;
            while (currentIndent < line.length() && line.charAt(currentIndent) == ' ' && currentIndent < firstLineIndent) {
                currentIndent++;
            }
            if (firstLine == null)
                firstLine = line.substring(currentIndent);
            formattedMethodCode.append(line.substring(currentIndent)).append("\n");
        }

        return new MethodSource(null, formattedMethodCode.toString(), start, end + 1,firstLine);
    }

    public static MethodSource getAbstractMethodSourceAtPosInClsCode(String classCode, int defPos) {
        try {
            int start = defPos;
            while (start > 0 && classCode.charAt(start) != '\n')
                start--;
            start++;
            while (classCode.charAt(start) == ' ')
                start++;

            int end = defPos;
            while (end < classCode.length() && classCode.charAt(end) != '\n')
                end++;
            end--;
            String abstractMthDef = classCode.substring(start, end + 1);
            return new MethodSource(null, abstractMthDef, start, end + 1, abstractMthDef);
        } catch (Exception e) {
            return null;
        }
    }

    public static String containsWhichMth(String input, Set<String> substrings) {
        for (String substring : substrings) {
            if (input.contains("call: " + transferMethodSignature(substring))) {
                return substring;
            }
        }
        return null;
    }
}
