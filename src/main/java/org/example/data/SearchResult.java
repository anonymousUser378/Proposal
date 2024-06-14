package org.example.data;

import jadx.api.JavaClass;

import java.io.Serializable;
import java.util.Objects;

public class SearchResult implements Serializable {


    public SearchResult(int startOffset, int endOffset, String line, String srcCode, String rootClsSrc, JavaClass rootCls, String mthSignature) {
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.line = line;
        this.srcCode = srcCode;
        this.rootClsSrc = rootClsSrc;
        this.rootCls = rootCls;
        this.mthSignature = mthSignature;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    private final int startOffset;
    private final int endOffset;

    private String line;

    public String getLine() {
        return line;
    }

    public void setLine(String line) {
        this.line = line;
    }

    public String getSrcCode() {
        return srcCode;
    }

    public void setSrcCode(String srcCode) {
        this.srcCode = srcCode;
    }

    private String srcCode;

    public String getRootClsSrc() {
        return rootClsSrc;
    }

    public void setRootClsSrc(String rootClsSrc) {
        this.rootClsSrc = rootClsSrc;
    }

    private String rootClsSrc;

    public JavaClass getRootCls() {
        return rootCls;
    }


    public void setRootCls(JavaClass rootCls) {
        this.rootCls = rootCls;
    }

    private transient JavaClass rootCls;

    public String getMthSignature() {
        return mthSignature;
    }

    private final String mthSignature;

    public SearchResult copy(){
        return new SearchResult(startOffset, endOffset, line, srcCode, rootClsSrc, rootCls, mthSignature);
    }
}
