package org.example.data;

import com.github.javaparser.ast.body.MethodDeclaration;

public class MethodSource {
    MethodDeclaration mth;

    private String mthSrc;

    public MethodSource(MethodDeclaration mth, String mthSrc, int startOffset, int endOffset, String firstLine) {
        this.mth = mth;
        this.mthSrc = mthSrc;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.firstLine = firstLine;
    }

    public MethodDeclaration getMth() {
        return mth;
    }

    public void setMth(MethodDeclaration mth) {
        this.mth = mth;
    }

    public String getMthSrc() {
        return mthSrc;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    private int startOffset;
    private int endOffset;

    public String getFirstLine() {
        return firstLine;
    }

    private String firstLine;
}
