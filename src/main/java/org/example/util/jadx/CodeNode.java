package org.example.util.jadx;

import jadx.api.JavaClass;
import jadx.api.JavaNode;

public class CodeNode {
    private final transient JavaClass rootCls;
    private final transient JavaNode javaNode;
    private final transient String line;
    private final transient int pos;

    public CodeNode(JavaClass rootCls, JavaNode javaNode, String lineStr, int pos) {
        this.rootCls = rootCls;
        this.javaNode = javaNode;
        this.line = lineStr;
        this.pos = pos;
    }

    public JavaClass getRootCls() {
        return rootCls;
    }

    public JavaNode getJavaNode() {
        return javaNode;
    }

    public String getLine() {
        return line;
    }

    public int getPos() {
        return pos;
    }
}
