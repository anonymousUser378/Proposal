package org.example;

import com.google.gson.*;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;

import com.theokanning.openai.service.OpenAiService;


import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.MyChatGPT.*;

import static org.example.util.MainUtils.mainLog;
import static org.example.util.MainUtils.saveToFile;


public class CallChatGPTMain {
    static double defaultTemperatureParam = 0.0;
    static int defaultMaxTokenNum = 4096;
    static int defaultGPT35MaxTokenNum = 16384;
    static String GPT_35 = "gpt-3.5-turbo";
    static String GPT_35_FT_AnalyseInfo90_10 = "ft:gpt-3.5-turbo-0125:anonymous:analyseinfo-90-10:xxx";
    public static String GPT_35_FT_AddObtainInfo20 = "ft:gpt-3.5-turbo-0125:anonymous:addobtaininfo-20:xxx";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    static String key1 = "xxxxxxxxxxxxxxxxxx"; // replace with your OpenAI API key

    static OpenAiService service = new OpenAiService(key1, DEFAULT_TIMEOUT);

    public static String chatCompletion(List<ChatMessage> messages, int maxToken, String modelName) {
        int tokenNum = countTokens(messages);
        if (tokenNum > defaultGPT35MaxTokenNum - 300) {
            return "MaxTokenNumReach";
        }
        if (tokenNum + maxToken > defaultGPT35MaxTokenNum)
            maxToken = defaultGPT35MaxTokenNum - tokenNum - 300;
        int maxAttempt = 5;
        int attempt = 0;

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model(modelName)
                .messages(messages)
                .temperature(defaultTemperatureParam)
                .maxTokens(maxToken)
                .build();

        do {
            try {
                long startTime = System.currentTimeMillis();
                ChatMessage responseMessage = service.createChatCompletion(chatCompletionRequest).getChoices().get(0).getMessage();
                String responseRaw = responseMessage.getContent();
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                mainLog("DEBUG", "ChatCompletionTime", executionTime);
                logToFile(messages, responseRaw);
                totalTokenConsumedNum += countTokens(messages);
                return responseRaw;
            } catch (Exception e) {
                mainLog("ERROR", "ChatCompletion", e);
            }
            try {
                Thread.sleep(2000L * attempt);
                attempt += 1;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (attempt < maxAttempt);
        return "ERROR";
    }

    public static String matchJSONString(String input) {
        String output;
        String patternString = "(?<=```json\\s)[\\s\\S]*?(?=\\s```)";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            output = matcher.group();
        } else {
            output = "ERROR";
        }
        return output;
    }

    public static void logToFile(List<ChatMessage> messages, String rawResponse) {
        JsonArray jsonArray = new JsonArray();
        for (ChatMessage chatMessage : messages) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("role", chatMessage.getRole());
            jsonObject.addProperty("content", chatMessage.getContent());
            jsonArray.add(jsonObject);
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("role", "assistant");
        jsonObject.addProperty("content", rawResponse);
        jsonArray.add(jsonObject);
        JsonObject sampleJ = new JsonObject();
        sampleJ.add("messages", jsonArray);
        saveToFile(outputPath + "chatGPTRawResponse.txt", sampleJ.toString());
    }


    public static int countTokens(List<ChatMessage> messages) {
        EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
        Encoding encoding = registry.getEncodingForModel(ModelType.GPT_4);
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage message : messages) {
            prompt.append(message.getContent());
        }
        return encoding.countTokens(prompt.toString());
    }
}
