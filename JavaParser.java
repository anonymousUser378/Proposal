package org.example.core;

import static org.example.MyChatGPT.defaultShortestVarOrMthNameLen;

public class JavaParser {
    public static String getRefName(String codeLine, String keyword) {
        String mthName = keyword.substring(0, keyword.length() - 2);
        try {
            return keyword.endsWith("()") ? matchMethodCall(codeLine, mthName) : matchVariable(codeLine, keyword);
        } catch (Exception e) {
            return keyword.endsWith("()") ? mthName : keyword;
        }
    }

    public static String getNameForVarOrMthCall(String varOrMthCall, boolean getShortName) {
        int endIndex = varOrMthCall.lastIndexOf(')');
        if (endIndex != varOrMthCall.length() - 1) {
            return varOrMthCall;
        }

        int parenthesisCount = 1;
        int startIndex = endIndex;
        while (startIndex > 0 && parenthesisCount > 0) {
            startIndex--;
            char ch = varOrMthCall.charAt(startIndex);
            if (ch == ')') {
                parenthesisCount++;
            } else if (ch == '(') {
                parenthesisCount--;
            }
        }

        if (parenthesisCount != 0) {
            return varOrMthCall;
        }

        int nameStartIndex = startIndex;
        while (nameStartIndex > 0) {
            char ch = varOrMthCall.charAt(nameStartIndex - 1);
            if (Character.isWhitespace(ch) || ch == '.' || ch == '(') {
                break;
            }
            nameStartIndex--;
        }

        if (getShortName) {
            String varOrMthName = varOrMthCall.substring(nameStartIndex, startIndex);
            return varOrMthName.length() > defaultShortestVarOrMthNameLen ? varOrMthName : varOrMthCall.substring(0, startIndex);
        }
        return varOrMthCall.substring(0, startIndex);
    }

    public static String matchVariable(String line, String keyword) {
        int keywordIndex = line.indexOf(keyword);
        if (keywordIndex == -1)
            return null;
        int startIndex = keywordIndex;
        int parenthesisCount = 0;
        while (startIndex > 0) {
            if (line.charAt(startIndex) == ')') {
                parenthesisCount++;
            } else if (line.charAt(startIndex) == '(') {
                parenthesisCount--;
            }
            if (parenthesisCount < 0 || line.charAt(startIndex) == ' ') {
                startIndex++;
                break;
            }
            startIndex--;
        }
        int endIndex = keywordIndex + keyword.length();

        return line.substring(startIndex, endIndex);
    }

    public static String matchMethodCall(String line, String keyword) {
        int keywordIndex = line.indexOf(keyword + '(');
        if (keywordIndex == -1)
            return null;
        int startIndex = keywordIndex;
        int parenthesisCount = 0;
        while (startIndex > 0) {
            if (line.charAt(startIndex) == ')') {
                parenthesisCount++;
            } else if (line.charAt(startIndex) == '(') {
                parenthesisCount--;
            }
            if (parenthesisCount < 0 || (line.charAt(startIndex) == ' ' && parenthesisCount == 0)) {
                startIndex++;
                break;
            }
            startIndex--;
        }
        int endIndex = keywordIndex + keyword.length();

        if (endIndex < line.length() && line.charAt(endIndex) == '(') {
            int openCount = 1;
            while (endIndex < line.length() && openCount > 0) {
                endIndex++;
                if (line.charAt(endIndex) == '(') {
                    openCount++;
                } else if (line.charAt(endIndex) == ')') {
                    openCount--;
                }
            }
        }

        return line.substring(startIndex, endIndex + 1);
    }

    public static String getLineAtOffset(String clsSourceCode, int targetOffset) {
        String[] lines = clsSourceCode.split("\n");
        int offset = 0;
        for (String line : lines) {
            int lineLength = line.length();
            if (offset + lineLength >= targetOffset) {
                return line.trim();
            }
            offset += lineLength + 1;
        }
        return null;
    }
}
