package org.example.data;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TreeNode implements Serializable {
    public String getData() {
        return data;
    }

    public Query getQuery() {
        return query;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public TreeNode getParent() {
        return parent;
    }

    String data;
    Query query;
    List<TreeNode> children;

    TreeNode parent;

    public List<String> getParentDataFlow() {
        return parentDataFlow;
    }

    public void setParentDataFlow(List<String> parentDataFlow) {
        this.parentDataFlow = parentDataFlow;
    }

    List<String> parentDataFlow;

    public TreeNode(Query query) {
        this.query = query;
        if (query != null)
            this.data = query.getSearchResult().getMthSignature() + ": " + query.getSearchResult().getLine();
        this.children = new ArrayList<>();
        this.parent = null;
    }

    public void addChild(TreeNode child) {
        this.children.add(child);
    }

    public void setChildren(List<TreeNode> children) {
        this.children = children;
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void print(String prefix, boolean isTail) {
        System.out.println(prefix + (isTail ? "└── " : "├── ") + data);
        for (int i = 0; i < children.size() - 1; i++) {
            children.get(i).print(prefix + (isTail ? "    " : "│   "), false);
        }
        if (children.size() > 0) {
            children.get(children.size() - 1).print(prefix + (isTail ?"    " : "│   "), true);
        }
    }
}
