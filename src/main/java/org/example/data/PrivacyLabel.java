package org.example.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PrivacyLabel implements Serializable {
    private String dataType;
    private List<String> infos;
    private List<String> justifications;
    private List<List<MyChatMessage>> chatMessagesList;

    public PrivacyLabel(String dataType) {
        this.dataType = dataType;
        this.infos = new ArrayList<>();
        this.justifications = new ArrayList<>();
        this.chatMessagesList = new ArrayList<>();
    }

    public void addPrivacyLabel(String info, String justification, List<MyChatMessage> chatMessages) {
        this.infos.add(info);
        this.justifications.add(justification);
        this.chatMessagesList.add(chatMessages);
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public List<String> getInfos() {
        return infos;
    }

    public void setInfos(List<String> infos) {
        this.infos = infos;
    }

    public List<String> getJustifications() {
        return justifications;
    }

    public void setJustifications(List<String> justifications) {
        this.justifications = justifications;
    }

    public List<List<MyChatMessage>> getChatMessagesList() {
        return chatMessagesList;
    }

    public void setChatMessagesList(List<List<MyChatMessage>> chatMessagesList) {
        this.chatMessagesList = chatMessagesList;
    }
}
