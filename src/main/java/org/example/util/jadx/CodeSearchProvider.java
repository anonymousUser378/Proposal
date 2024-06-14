package org.example.util.jadx;

import jadx.api.*;
import jadx.api.metadata.ICodeMetadata;
import jadx.api.metadata.ICodeNodeRef;

import java.util.ArrayList;
import java.util.List;

import static jadx.core.utils.Utils.getOrElse;

public class CodeSearchProvider {
    private final ICodeCache codeCache;
    private String code;
    private int clsNum = 0;
    private final ISearchMethod searchMth;
    private final String searchStr;
    private final List<JavaClass> classes;
    private int pos = 0;
    private final JadxDecompiler jadx;

    public CodeSearchProvider(JadxDecompiler jadx, SearchSettings searchSettings) {
        this.jadx = jadx;
        this.codeCache = jadx.getRoot().getCodeCache();
        searchSettings.prepare();
        this.searchMth = searchSettings.getSearchMethod();
        this.searchStr = searchSettings.getSearchString();
        this.classes = jadx.getClassesWithInners();
    }

    public List<CodeNode> searchAll() {
        List<CodeNode> codeNodeList = new ArrayList<>();
        while (true) {
            CodeNode newResult = next();
            if (newResult == null) {
                return codeNodeList;
            }
            codeNodeList.add(newResult);
        }
    }

    public CodeNode next() {
        while (true) {
            if (clsNum >= classes.size()) {
                return null;
            }
            JavaClass cls = classes.get(clsNum);
            if (!cls.getClassNode().isInner()) {
                if (code == null) {
                    code = getClassCode(cls, codeCache);
                }
                CodeNode newResult = searchNext(cls, code);
                if (newResult != null) {
                    return newResult;
                }
            }
            clsNum++;
            pos = 0;
            code = null;
        }
    }

    private CodeNode searchNext(JavaClass javaClass, String clsCode) {
        int newPos = searchMth.find(clsCode, searchStr, pos);
        if (newPos == -1) {
            return null;
        }
        int lineStart = 1 + clsCode.lastIndexOf(ICodeWriter.NL, newPos);
        int lineEnd = clsCode.indexOf(ICodeWriter.NL, newPos);
        int end = lineEnd == -1 ? clsCode.length() : lineEnd;
        String line = clsCode.substring(lineStart, end);
        this.pos = end;
        JavaNode enclosingNode = getOrElse(getEnclosingNode(javaClass, end), javaClass);
        return new CodeNode(javaClass, enclosingNode, line.trim(), newPos);
    }

    private JavaNode getEnclosingNode(JavaClass javaCls, int pos) {
        try {
            ICodeMetadata metadata = javaCls.getCodeInfo().getCodeMetadata();
            ICodeNodeRef nodeRef = metadata.getNodeAt(pos);
            JavaNode encNode = jadx.getJavaNodeByRef(nodeRef);
            if (encNode != null) {
                return encNode;
            }
        } catch (Exception e) {
            System.out.println("Failed to resolve enclosing node" + e);
        }
        return null;
    }

    private String getClassCode(JavaClass javaClass, ICodeCache codeCache) {
        try {
            String code = codeCache.getCode(javaClass.getRawName());
            if (code != null) {
                return code;
            }
            return javaClass.getCode();
        } catch (Exception e) {
            System.out.println("Failed to get class code: " + javaClass + e);
            return "";
        }
    }

}
