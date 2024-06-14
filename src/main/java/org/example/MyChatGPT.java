package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import jadx.api.*;
import org.example.core.JadxMainEngine;
import org.example.data.*;
import org.example.util.MainUtils;
import org.example.util.jadx.CodeNode;
import org.example.util.jadx.CodeSearchProvider;
import org.example.util.jadx.SearchSettings;

import java.io.File;
import java.util.*;

import static org.example.CallChatGPTMain.*;
import static org.example.core.JadxMainEngine.*;
import static org.example.core.JavaParser.getRefName;
import static org.example.data.MyChatMessage.transform;
import static org.example.data.Prompts.*;
import static org.example.util.MainUtils.*;

public class MyChatGPT {
    public static String packageName;
    public static String outputPath;
    public static final int defaultMaxCostTime = 600000;
    public static final int defaultMaxQueryNum = 12;
    public static final int defaultMaxRetrofitQueryNum = 10;
    public static final int defaultMaxGeneralQueryNum = 10;
    public static final int defaultRetrofitMaxDepth = 3;
    public static final int defaultRetrofitMaxUsageNum = 1;
    public static final int defaultMaxDepth = 4;
    public static final int defaultMaxUsageNum = 2;
    public static int currentMaxUsageNum = defaultMaxUsageNum;
    public static int currentMaxDepth = defaultMaxDepth;
    public static int totalTokenConsumedNum = 0;
    public static long totalTimeConsumed = 0;
    public static long systemStartTime = 0;
    public static String currentTargetMth = "";
    public static boolean isTargetRetrofit = false;
    public static int defaultMinMthNameLen = 3;
    public static int currentCodeNotAvailableTime = 0;
    public static final int maxCodeNotAvailableTime = 1;
    public static final int defaultShortestVarOrMthNameLen = 2;
    public static Map<String, List<SearchResult>> networkingAPIsUsage;
    public static void main(String[] args) {

        String apkPath = "xxxx.apk"; // replace with your apk path

        outputPath = "output/" + apkPath.split("/")[apkPath.split("/").length - 1] + "/";
        JadxArgs jadxArgs = new JadxArgs();
        jadxArgs.setInputFile(new File(apkPath));
        jadxArgs.setOutDir(new File(outputPath));
        jadxArgs.setUseImports(false);

        systemStartTime = System.currentTimeMillis();
        try (JadxDecompiler jadx = new JadxDecompiler(jadxArgs)) {
            jadx.load();
            packageName = apkPath.split("/")[apkPath.split("/").length - 1];
            packageName = packageName.substring(0, packageName.lastIndexOf("."));
            if (!getAndroidManifest(jadx).contains("android.permission.INTERNET")) {
                saveToFile("output/noInternetApp.txt", packageName + ".apk");
                saveToFile("output/completelyProcessApp.txt", packageName + ".apk");
                return;
            }

            List<Query> queries = getUsageOfRetrofit(jadx);
            if (queries.size() > 0) {
                saveToFile("output/hasRetrofitApp.txt", packageName + ".apk");
            }

            List<Query> queries2 = getUsageOfNetworkingAPI(jadx);
            discardRandomElements(queries, defaultMaxRetrofitQueryNum);
            discardRandomElements(queries2, defaultMaxGeneralQueryNum);
            queries.addAll(queries2);
            systemStartTime = System.currentTimeMillis();
            if (queries.size() > defaultMaxQueryNum)
                queries = queries.subList(0, defaultMaxQueryNum);

            List<List<ChatMessage>> obtainInfoChatMessagesList = new ArrayList<>();
            List<List<MyChatMessage>> obtainInfoMyChatMessagesList = new ArrayList<>();
            List<List<ChatMessage>> analyseInfoChatMessagesList = new ArrayList<>();
            List<List<MyChatMessage>> analyseInfoMyChatMessagesList = new ArrayList<>();
            for (Query query : queries) {
                try {
                    long currentCostTime = System.currentTimeMillis() - systemStartTime;
                    if (currentCostTime > defaultMaxCostTime) {
                        saveToFile("output/timeoutApp.txt", packageName + ".apk");
                        break;
                    }

                    currentTargetMth = query.getRefName();
                    isTargetRetrofit = !currentTargetMth.startsWith("<");
                    if (isTargetRetrofit) {
                        currentMaxUsageNum = defaultRetrofitMaxUsageNum;
                        currentMaxDepth = defaultRetrofitMaxDepth;
                    } else {
                        currentMaxUsageNum = defaultMaxUsageNum;
                        currentMaxDepth = defaultMaxDepth;
                    }
                    List<ChatMessage> chatMessages = callChatGPTForObtainInfoSetUp(query);
                    Set<List<ChatMessage>> chatMessagesSet = continueObtainInfoFromGPT(jadx, chatMessages, query.getSearchResult(), 1);
                    for (List<ChatMessage> chatMessages2 : chatMessagesSet) {
                        obtainInfoChatMessagesList.add(new ArrayList<>(chatMessages2));
                        obtainInfoMyChatMessagesList.add(transform(new ArrayList<>(chatMessages2)));
                        boolean isError = false;
                        for (ChatMessage chatMessage2 : chatMessages2) {
                            if (chatMessage2.getRole().equals("ERROR"))
                                isError = true;
                        }
                        if (isError)
                            continue;

                        List<ChatMessage> infoTypeChatMessages = callGPTForAnalyseInfo(chatMessages2);
                        if (infoTypeChatMessages.size() == 0)
                            continue;
                        chatMessages2.addAll(infoTypeChatMessages);
                        analyseInfoChatMessagesList.add(new ArrayList<>(chatMessages2));
                        analyseInfoMyChatMessagesList.add(transform(new ArrayList<>(chatMessages2)));
                    }
                } catch (Exception e) {
                    mainLog("ERROR", "MainQuery", e);
                }

            }
            long endTime = System.currentTimeMillis();
            totalTimeConsumed = endTime - systemStartTime;

            Map<String, PrivacyLabel> privacyLabelMap = getLabelsFromChatMessage(analyseInfoChatMessagesList);

            logResult(obtainInfoMyChatMessagesList, analyseInfoMyChatMessagesList, privacyLabelMap);

        } catch (Exception e) {
            mainLog("ERROR", "Main", e);
            saveToFile("output/crashedApp.txt", packageName + ".apk");
        }
    }

    public static List<ChatMessage> callGPTForAnalyseInfo(List<ChatMessage> chatMessages) {
        List<ChatMessage> newChatMessages = new ArrayList<>();
        try {
            String reply = matchJSONString(chatMessages.get(chatMessages.size() - 1).getContent());
            JsonObject replyJSON = JsonParser.parseString(reply).getAsJsonObject();
            boolean isFound = replyJSON.get("Question_1").getAsJsonObject().get("answer").getAsString().equals("YES");
            if (!isFound)
                return newChatMessages;
            JsonArray infoArray = replyJSON.get("Question_2").getAsJsonArray();
            if (infoArray.toString().equals("[]"))
                return newChatMessages;
            String typeReply = callChatGPTForAnalyseInfo(infoArray.toString());
            newChatMessages.add(new ChatMessage("infoArray", infoArray.toString()));
            newChatMessages.add(new ChatMessage("typeJSON", typeReply));

        } catch (Exception ignored) {}

        return newChatMessages;
    }

    public static void logResult(List<List<MyChatMessage>> obtainInfoMyChatMessagesList, List<List<MyChatMessage>> analyseInfoMyChatMessagesList, Map<String, PrivacyLabel> privacyLabelMap) {
        saveObj(privacyLabelMap, outputPath + "/result/privacyLabelMap.obj");
        saveObj(obtainInfoMyChatMessagesList, outputPath + "/result/obtainInfoMyChatMessagesList.obj");
        saveObj(analyseInfoMyChatMessagesList, outputPath + "/result/analyseInfoMyChatMessagesList.obj");
        saveObj(totalTimeConsumed, outputPath + "/result/totalTimeConsumed.obj");
        saveObj(totalTokenConsumedNum, outputPath + "/result/totalTokenConsumedNum.obj");
        StringBuilder sb = new StringBuilder("********************************\n");
        for (String dataType : privacyLabelMap.keySet()) {
            sb.append(dataType).append("\n");
        }
        sb.append("********************************\n");
        sb.append("Time: ").append(totalTimeConsumed).append("\n").append("Token: ").append(totalTokenConsumedNum).append("\n********************************\n\n\n");
        saveToFile(outputPath + "/result/result.txt", sb.toString());
        saveToFile("output/completelyProcessApp.txt", packageName + ".apk");
        if (analyseInfoMyChatMessagesList.size() > 0)
            saveToFile("output/successfullyGetLabelApp.txt", packageName + ".apk");
        if (obtainInfoMyChatMessagesList.size() > 0)
            saveToFile("output/successfullyGetAnalysedApp.txt", packageName + ".apk");
    }

    public static Map<String, PrivacyLabel> getLabelsFromChatMessage(List<List<ChatMessage>> chatMessagesSetAll) {
        Map<String, PrivacyLabel> privacyLabelMap = new HashMap<>();
        for (List<ChatMessage> chatMessages : chatMessagesSetAll) {
            for (ChatMessage chatMessage : chatMessages) {
                if (!chatMessage.getRole().equals("typeJSON"))
                    continue;
                try {
                    String reply = matchJSONString(chatMessage.getContent());
                    JsonArray typeArray = JsonParser.parseString(reply).getAsJsonArray();
                    for (JsonElement typeEle : typeArray) {
                        JsonObject typeJSON = typeEle.getAsJsonObject();
                        String info = typeJSON.get("info").getAsString();
                        String dataType = typeJSON.get("data_type").getAsString();
                        String dataTypeJust = typeJSON.get("justification").getAsString();
                        PrivacyLabel privacyLabel = privacyLabelMap.get(dataType);
                        if (privacyLabel == null)
                            privacyLabel = new PrivacyLabel(dataType);
                        privacyLabel.addPrivacyLabel(info, dataTypeJust, transform(chatMessages));
                        privacyLabelMap.put(dataType, privacyLabel);
                    }
                } catch (Exception ignored) {}
            }
        }
        return privacyLabelMap;
    }


    public static Set<List<ChatMessage>> continueObtainInfoFromGPT(JadxDecompiler jadx, List<ChatMessage> chatMessages, SearchResult searchResult, int depth) {
        String rawReply = chatMessages.get(chatMessages.size() - 1).getContent();
        if (rawReply.equals("MaxTokenNumReach")) {
            chatMessages.subList(0, chatMessages.size() - 2);
            depth = currentMaxDepth;
        }
        String reply = matchJSONString(chatMessages.get(chatMessages.size() - 1).getContent());
        JsonObject replyJSON;
        try {
            replyJSON = JsonParser.parseString(reply).getAsJsonObject();
        } catch (Exception e) {
            try {
                replyJSON = JsonParser.parseString(reply + "}").getAsJsonObject();
            } catch (Exception e2) {
                mainLog("ERROR", "JsonParser", reply.replace('\n', ' '));
                Set<List<ChatMessage>> chatMessagesSet = new HashSet<>();
                chatMessages.add(new ChatMessage("ERROR", "ERROR"));
                chatMessagesSet.add(chatMessages);
                return chatMessagesSet;
            }
        }
        boolean moreCodeNeeded = replyJSON.get("Question_1").getAsJsonObject().get("answer").getAsString().equals("NO");
        if (moreCodeNeeded) {
            if (depth == currentMaxDepth) {
                callChatGPTForObtainInfoContinue(chatMessages, maxDepthReachNote);
                return continueObtainInfoFromGPT(jadx, chatMessages, searchResult, depth + 1);
            } else if (depth > currentMaxDepth) {
                Set<List<ChatMessage>> chatMessagesSet = new HashSet<>();
                chatMessages.add(new ChatMessage("ERROR", "ERROR"));
                chatMessagesSet.add(chatMessages);
                return chatMessagesSet;
            }
            JsonObject checkCodeJSON = replyJSON.get("Question_3").getAsJsonObject();
            try {
                checkCodeJSON.get("name").getAsString();
            } catch (Exception e) {
                Set<List<ChatMessage>> chatMessagesSet = new HashSet<>();
                chatMessages.add(new ChatMessage("ERROR", "ERROR"));
                chatMessagesSet.add(chatMessages);
                return chatMessagesSet;
            }
            String codeName = checkCodeJSON.get("name").getAsString();
            String codeType = checkCodeJSON.get("type").getAsString();
            String operation = checkCodeJSON.get("operation").getAsString();

            if (codeType.equals("") || codeType.equals("N/A") || codeName.equals("")) {
                mainLog("ERROR", "codeType/codeName", "codeName = " + codeName + ", codeType = " + codeType);
                Set<List<ChatMessage>> chatMessagesSet = new HashSet<>();
                chatMessages.add(new ChatMessage("ERROR", "ERROR"));
                chatMessagesSet.add(chatMessages);
                return chatMessagesSet;
            }
            JavaNode codeNode = codeType.equals("METHOD") ? searchJavaNode(jadx, searchResult, codeName + "(") : searchJavaNode(jadx, searchResult, codeName);
            if (codeNode == null && codeName.endsWith(")")) {
                codeNode = searchJavaNode(jadx, searchResult, codeName);
            }
            if (codeNode == null) {
                try{
                    String splitCodeName = codeName.split("\\.")[codeName.split("\\.").length - 1];
                    if (splitCodeName.length() >= defaultMinMthNameLen) {
                        codeName = splitCodeName;
                        codeNode = codeType.equals("METHOD") ? searchJavaNode(jadx, searchResult, codeName + "(") : searchJavaNode(jadx, searchResult, codeName);
                    }
                } catch (Exception ignored) {}
            }

            if (operation.equals("DEFINITION")) {
                SearchResult newSearchResult = goToDeclaration(codeNode);
                if (newSearchResult != null && !newSearchResult.getSrcCode().equals("") && !newSearchResult.getMthSignature().equals(searchResult.getMthSignature())) {
                    StringBuilder srcCodeWithAnno = new StringBuilder();
                    try {
                        String clsName = parseMethodSignature(newSearchResult.getMthSignature()).getClassName();
                        srcCodeWithAnno.append(String.format("// The '%s' is defined in '%s'.\n", codeName, clsName));
                    } catch (Exception ignored) {}
                    srcCodeWithAnno.append(newSearchResult.getSrcCode());
                    newSearchResult.setSrcCode(srcCodeWithAnno.toString());
                    currentCodeNotAvailableTime = 0;
                    callChatGPTForObtainInfoContinue(chatMessages, newSearchResult.getSrcCode());
                    return continueObtainInfoFromGPT(jadx, chatMessages, newSearchResult, depth + 1);
                } else { //codeNotAvailable
                    if (currentCodeNotAvailableTime < maxCodeNotAvailableTime) {
                        currentCodeNotAvailableTime++;
                        callChatGPTForObtainInfoContinue(chatMessages, codeNotAvailableNote);
                    } else {
                        callChatGPTForObtainInfoContinue(chatMessages, maxDepthReachNote);
                    }
                    return continueObtainInfoFromGPT(jadx, chatMessages, searchResult, depth + 1);
                }
            } else if (operation.equals("USAGES")) {
                Set<List<ChatMessage>> chatMessagesSetAll = new HashSet<>();
                List<SearchResult> newSearchResults = getUsageOfJavaNode(jadx, codeNode);
                Set<String> alreadyHasMth = new HashSet<>();
                newSearchResults.removeIf(newSearchResult ->
                        newSearchResult == null ||
                                alreadyHasMth.contains(newSearchResult.getMthSignature()) ||
                                !alreadyHasMth.add(newSearchResult.getMthSignature()) ||  // add returns false if the element was already in the set
                                newSearchResult.getMthSignature().equals(searchResult.getMthSignature())
                );
                discardRandomElements(newSearchResults, currentMaxUsageNum);
                if (newSearchResults.size() == 0) { //codeNotAvailable
                    if (currentCodeNotAvailableTime < maxCodeNotAvailableTime) {
                        currentCodeNotAvailableTime++;
                        callChatGPTForObtainInfoContinue(chatMessages, codeNotAvailableNote);
                    } else {
                        callChatGPTForObtainInfoContinue(chatMessages, maxDepthReachNote);
                    }
                    return continueObtainInfoFromGPT(jadx, chatMessages, searchResult, depth + 1);
                }
                for (SearchResult newSearchResult : newSearchResults) {
                    if (isTargetRetrofit) {
                        String srcCodeWithClsInfo = insertClsMemberInfo(codeName, newSearchResult);
                        newSearchResult.setSrcCode(srcCodeWithClsInfo);
                    } else {
                        StringBuilder srcCodeWithAnno = new StringBuilder();
                        String mthName = parseMethodSignature(newSearchResult.getMthSignature()).getMethodName();
                        String clsName = parseMethodSignature(newSearchResult.getMthSignature()).getClassName();
                        srcCodeWithAnno.append(String.format("// The '%s' is used in '%s' in '%s'.\n", codeName, mthName, clsName));
                        srcCodeWithAnno.append(newSearchResult.getSrcCode());
                        newSearchResult.setSrcCode(srcCodeWithAnno.toString());
                    }
                    List<ChatMessage> chatMessagesCopy = new ArrayList<>(chatMessages);
                    currentCodeNotAvailableTime = 0;
                    callChatGPTForObtainInfoContinue(chatMessagesCopy, newSearchResult.getSrcCode());
                    Set<List<ChatMessage>> chatMessagesSet = continueObtainInfoFromGPT(jadx, chatMessagesCopy, newSearchResult, depth + 1);
                    chatMessagesSetAll.addAll(chatMessagesSet);
                }
                return chatMessagesSetAll;
            }
        } else {
            Set<List<ChatMessage>> chatMessagesSet = new HashSet<>();
            chatMessagesSet.add(chatMessages);
            return chatMessagesSet;
        }
        Set<List<ChatMessage>> chatMessagesSet = new HashSet<>();
        chatMessages.add(new ChatMessage("ERROR", "ERROR"));
        chatMessagesSet.add(chatMessages);
        return chatMessagesSet;
    }

    public static <T> void discardRandomElements(List<T> list, int n) {
        Collections.shuffle(list);
        int removeCount = list.size() > n ? list.size() - n : 0;
        for (int i = 0; i < removeCount; i++) {
            list.remove(list.size() - 1);
        }
    }

    private static String insertClsMemberInfo(String codeName, SearchResult newSearchResult) {
        StringBuilder srcCode = new StringBuilder();
        String mthName = parseMethodSignature(newSearchResult.getMthSignature()).getMethodName();
        String clsName = parseMethodSignature(newSearchResult.getMthSignature()).getClassName();
        srcCode.append(String.format("/* The '%s' is used in '%s' in '%s'\n", codeName, mthName, clsName));
        srcCode.append("   The class has the following members:\n");
        for (JavaField javaField : newSearchResult.getRootCls().getFields()) {
            srcCode.append(String.format("       * %s\n", getFieldDescription(javaField)));
        }
        for (JavaMethod javaMethod : newSearchResult.getRootCls().getMethods()) {
            srcCode.append(String.format("       * %s\n", getMethodDescription(javaMethod)));
        }
        srcCode.append("*/\n");
        srcCode.append(newSearchResult.getSrcCode());
        return srcCode.toString();
    }

    public static void callChatGPTForObtainInfoContinue(List<ChatMessage> chatMessages, String code) {
        String modelName = isTargetRetrofit ? GPT_35_FT_AnalyseInfo90_10 : GPT_35_FT_AddObtainInfo20;
        String srcCode = removeMetaDataInClsCode(code);
        if (srcCode.equals(""))
            srcCode = maxDepthReachNote;
        chatMessages.add(new ChatMessage(ChatMessageRole.USER.value(), String.format("```java\n%s\n```", srcCode)));
        String reply = chatCompletion(chatMessages, defaultMaxTokenNum, modelName);
        chatMessages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), reply));
    }

    public static List<ChatMessage> callChatGPTForObtainInfoSetUp(Query query) {
        String targetMth = query.getRefName();
        String srcCode = isTargetRetrofit ? removeMetaDataInClsCode(query.getSearchResult().getSrcCode()) : query.getSearchResult().getSrcCode();
        String modelName = isTargetRetrofit ? GPT_35_FT_AnalyseInfo90_10 : GPT_35_FT_AddObtainInfo20;
        if (srcCode.equals(""))
            srcCode = maxDepthReachNote;

        System.out.println("================================================================================================================================================================");
        System.out.printf("Target: %s%n", targetMth);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemStringForCallChatGPTSetUp));
        String prompt = isTargetRetrofit ? userStringForCallChatGPTSetUp_Retrofit : userStringForCallChatGPTSetUp;
        prompt = prompt.replace("<METHOD_NAME_TO_BE_REPLACE>", targetMth);
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt));
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), String.format("```java\n%s\n```", srcCode)));
        String reply = chatCompletion(messages, defaultMaxTokenNum, modelName);
        messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), reply));

        return messages;
    }


    public static String callChatGPTForAnalyseInfo(String infoJSON) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemStringForAnalyseInfo));
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userStringForAnalyseInfo));
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), String.format("```json\n%s\n```", infoJSON)));
        String reply = chatCompletion(messages, defaultMaxTokenNum, GPT_35_FT_AnalyseInfo90_10);
        messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), reply));

        return reply;
    }

    public static List<Query> getUsageOfRetrofit(JadxDecompiler jadx) {
        SearchSettings searchSettings = new SearchSettings("@retrofit2.http.", false, false); // 查找retrofit API
        CodeSearchProvider codeSearchProvider = new CodeSearchProvider(jadx, searchSettings);
        List<CodeNode> codeNodes = codeSearchProvider.searchAll();
        List<Query> queries = new ArrayList<>();
        Set<String> checkedCls = new HashSet<>();
        for (CodeNode codeNode : codeNodes) {
            if (codeNode.getJavaNode() instanceof JavaClass javaClass) {
                if (checkedCls.contains(javaClass.getFullName()))
                    continue;
                checkedCls.add(javaClass.getFullName());
                for (JavaMethod javaMethod : javaClass.getMethods()) {
                    if (javaMethod.getMethodNode().getAttributesString().contains("Lretrofit2/http/") && javaMethod.getArguments().size() > 0) { // 筛选传参retrofit API
                        String callerSignature = MainUtils.getMethodSignature(javaMethod);
                        String clsSourceCode = codeNode.getRootCls().getCode();
                        int defPos = javaMethod.getDefPos();
                        MethodSource methodSource = getAbstractMethodSourceAtPosInClsCode(clsSourceCode, defPos);
                        if (methodSource == null)
                            continue;

                        SearchResult searchResult = new SearchResult(methodSource.getStartOffset(), methodSource.getEndOffset(), methodSource.getFirstLine(), methodSource.getMthSrc(), clsSourceCode, codeNode.getRootCls(), callerSignature);
                        String vaName = javaMethod.getName() + "()";
                        String refName = getRefName(methodSource.getFirstLine(), vaName);

                        StringBuilder srcCode = new StringBuilder();
                        String mthName = javaMethod.getName();
                        String clsName = javaClass.getFullName();
                        srcCode.append(String.format("// The '%s' is defined in '%s'.\n", mthName, clsName));
                        srcCode.append(searchResult.getSrcCode());
                        searchResult.setSrcCode(srcCode.toString());

                        Query query = new Query(searchResult, refName, null);
                        queries.add(query);
                    }
                }
            }
        }
        return queries;
    }

    public static List<Query> getUsageOfNetworkingAPI(JadxDecompiler jadx) {
        List<Query> queries = new ArrayList<>();

        Map<String, String[]> networkingAPIs = new HashMap<>();

        networkingAPIs.put("1", new String[]{"", "<java.net.URLConnection: java.io.OutputStream getOutputStream()>"});
        networkingAPIs.put("2", new String[]{"", "<java.net.HttpURLConnection: java.io.OutputStream getOutputStream()>"});
        networkingAPIs.put("3", new String[]{"", "<java.net.HttpsURLConnection: java.io.OutputStream getOutputStream()>"});
        //HttpClient
        networkingAPIs.put("4", new String[]{"", "<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest)>"});
        networkingAPIs.put("5", new String[]{"", "<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.protocol.HttpContext)>"});
        networkingAPIs.put("6", new String[]{"", "<org.apache.http.client.HttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>)>"});
        networkingAPIs.put("7", new String[]{"", "<org.apache.http.client.HttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>"});
        networkingAPIs.put("8", new String[]{"", "<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest)>"});
        networkingAPIs.put("9", new String[]{"", "<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.protocol.HttpContext)>"});
        networkingAPIs.put("10", new String[]{"", "<org.apache.http.client.HttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>)>"});
        networkingAPIs.put("11", new String[]{"", "<org.apache.http.client.HttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>"});
        //AndroidHttpClient
        networkingAPIs.put("12", new String[]{"", "<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest)>"});
        networkingAPIs.put("13", new String[]{"", "<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.protocol.HttpContext)>"});
        networkingAPIs.put("14", new String[]{"", "<android.net.http.AndroidHttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>)>"});
        networkingAPIs.put("15", new String[]{"", "<android.net.http.AndroidHttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>"});
        networkingAPIs.put("16", new String[]{"", "<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest)>"});
        networkingAPIs.put("17", new String[]{"", "<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.protocol.HttpContext)>"});
        networkingAPIs.put("18", new String[]{"", "<android.net.http.AndroidHttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>)>"});
        networkingAPIs.put("19", new String[]{"", "<android.net.http.AndroidHttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>"});
        //OkHttp
        networkingAPIs.put("20", new String[]{"", "<okhttp3.OkHttpClient: okhttp3.Call newCall(okhttp3.Request)>"});
        //volley
        networkingAPIs.put("21", new String[]{"", "<com.android.volley.toolbox.JsonObjectRequest: void JsonObjectRequest(int, java.lang.String, com.android.volley.Response.Listener<java.lang.String>, com.android.volley.Response.ErrorListener)>"});
        networkingAPIs.put("22", new String[]{"", "<com.android.volley.toolbox.JsonObjectRequest: void JsonObjectRequest(int, java.lang.String, org.json.JSONObject, com.android.volley.Response.Listener<org.json.JSONObject>, com.android.volley.Response.ErrorListener)>"});
        networkingAPIs.put("23", new String[]{"", "<com.android.volley.toolbox.JsonObjectRequest: void JsonArrayRequest(int, java.lang.String, org.json.JSONArray, com.android.volley.Response.Listener<org.json.JSONArray> listener, com.android.volley.Response.ErrorListener errorListener)>"});
        networkingAPIs.put("24", new String[]{"", "<com.android.volley.toolbox.JsonObjectRequest: void ImageRequest(java.lang.String, com.android.volley.Response.Listener<android.graphics.Bitmap>, int, int, android.widget.ImageView.ScaleType, android.graphics.Bitmap.Config, com.android.volley.Response.ErrorListener)>"});

        networkingAPIsUsage = JadxMainEngine.getUsageByMthSign(jadx, networkingAPIs);

        Map<String, String> apiLibMap = new HashMap<>();
        apiLibMap.put("<java.net.URLConnection: java.io.OutputStream getOutputStream()>", "com.android.volley");
        apiLibMap.put("<java.net.HttpURLConnection: java.io.OutputStream getOutputStream()>", "com.android.volley");
        apiLibMap.put("<java.net.HttpsURLConnection: java.io.OutputStream getOutputStream()>", "com.android.volley");

        apiLibMap.put("<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest)>", "com.android.volley");
        apiLibMap.put("<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.protocol.HttpContext)>", "com.android.volley");
        apiLibMap.put("<org.apache.http.client.HttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>)>", "com.android.volley");
        apiLibMap.put("<org.apache.http.client.HttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>", "com.android.volley");
        apiLibMap.put("<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest)>", "com.android.volley");
        apiLibMap.put("<org.apache.http.client.HttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.protocol.HttpContext)>", "com.android.volley");
        apiLibMap.put("<org.apache.http.client.HttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>)>", "com.android.volley");
        apiLibMap.put("<org.apache.http.client.HttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>", "com.android.volley");

        apiLibMap.put("<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest)>", "com.android.volley");
        apiLibMap.put("<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.protocol.HttpContext)>", "com.android.volley");
        apiLibMap.put("<android.net.http.AndroidHttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>)>", "com.android.volley");
        apiLibMap.put("<android.net.http.AndroidHttpClient: <?> execute(org.apache.http.HttpHost, org.apache.http.HttpRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>", "com.android.volley");
        apiLibMap.put("<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest)>", "com.android.volley");
        apiLibMap.put("<android.net.http.AndroidHttpClient: org.apache.http.HttpResponse execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.protocol.HttpContext)>", "com.android.volley");
        apiLibMap.put("android.net.http.AndroidHttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>)>", "com.android.volley");
        apiLibMap.put("android.net.http.AndroidHttpClient: <?> execute(org.apache.http.client.methods.HttpUriRequest, org.apache.http.client.ResponseHandler<?>, org.apache.http.protocol.HttpContext)>", "com.android.volley");

        apiLibMap.put("<okhttp3.OkHttpClient: okhttp3.Call newCall(okhttp3.Request)>", "retrofit2");

        for (String apiSign : networkingAPIsUsage.keySet()) {
            List<SearchResult> usages = networkingAPIsUsage.get(apiSign);
            List<String> callerSignatureResultSet = new ArrayList<>();
            for (SearchResult usage : usages) {
                if (callerSignatureResultSet.contains(usage.getMthSignature()))
                    continue;
                callerSignatureResultSet.add(usage.getMthSignature());
                StringBuilder srcCode = new StringBuilder();
                String mthName = parseMethodSignature(usage.getMthSignature()).getMethodName();
                String clsName = parseMethodSignature(usage.getMthSignature()).getClassName();
                String uponLibName = apiLibMap.get(apiSign);
                if (uponLibName != null && clsName.contains(uponLibName))
                    continue;
                srcCode.append(String.format("/* The '%s' is used in '%s' in '%s'*/\n", apiSign, mthName, clsName));
                srcCode.append(usage.getSrcCode());
                usage.setSrcCode(srcCode.toString());
                if (usage.getSrcCode().contains("(\"Method not decompiled: "))
                    continue;
                queries.add(new Query(usage, apiSign, null));
            }
        }
        return queries;
    }
}
