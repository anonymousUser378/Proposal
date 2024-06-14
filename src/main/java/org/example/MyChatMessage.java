package org.example.data;

import com.theokanning.openai.completion.chat.ChatMessage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MyChatMessage implements Serializable {
    private final String role;
    private final String content;

    public MyChatMessage(ChatMessage chatMessage) {
        this.role = chatMessage.getRole();
        this.content = chatMessage.getContent();
    }

    public static MyChatMessage transform(ChatMessage chatMessage) {
        return new MyChatMessage(chatMessage);
    }

    public static List<MyChatMessage> transform(List<ChatMessage> chatMessages) {
        List<MyChatMessage> myChatMessages = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessages) {
            myChatMessages.add(new MyChatMessage(chatMessage));
        }
        return myChatMessages;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        return "(R=" + this.role + ", C=" + this.content + ")";
    }
}
