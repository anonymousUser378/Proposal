package org.example.data;

import java.io.Serializable;

public class Query implements Serializable {
    public Query(SearchResult searchResult, String refName, Query lastQuery) {
        this.searchResult = searchResult;
        this.refName = refName;
        this.lastQuery = lastQuery;
        this.isInvokedByLastQuery = false;
        this.summary = null;
    }

    public Query(SearchResult searchResult, String refName, Query lastQuery, boolean isInvokedByLastQuery) {
        this.searchResult = searchResult;
        this.refName = refName;
        this.lastQuery = lastQuery;
        this.isInvokedByLastQuery = isInvokedByLastQuery;
        this.summary = null;
    }

    private SearchResult searchResult;

    private Query lastQuery;

    private boolean isInvokedByLastQuery;

    public boolean isInvokedByLastQuery() {
        return isInvokedByLastQuery;
    }

    public void setInvokedByLastQuery(boolean invokedByLastQuery) {
        isInvokedByLastQuery = invokedByLastQuery;
    }

    public Query getLastQuery() {
        return lastQuery;
    }

    public void setLastQuery(Query lastQuery) {
        this.lastQuery = lastQuery;
    }

    public SearchResult getSearchResult() {
        return searchResult;
    }

    public String getRefName() {
        return refName;
    }

    public void setRefName(String refName) {
        this.refName = refName;
    }

    public void setSearchResult(SearchResult searchResult) {
        this.searchResult = searchResult;
    }

    private String refName;

    private String summary;

    private String summaryBack;

    public void setSummaryBack(String summaryBack) {
        this.summaryBack = summaryBack;
    }

    public String getSummaryBack() {
        return summaryBack;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSummary() {
        return summary;
    }

    public Query copy() {
        Query newQuery = new Query(searchResult.copy(), refName, lastQuery, isInvokedByLastQuery);
        newQuery.setSummary(summary);
        return newQuery;
    }

}
