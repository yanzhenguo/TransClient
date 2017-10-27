package edu.shu.entity;

/**
 * 从主站返回的消息类
 */
public class ServerMessage {
    private FirstMessage[] firstMessage;
    private String dataInfo;
    private String stateInfo;

    public ServerMessage() {
    }

    public FirstMessage[] getFirstMessage() {
        return firstMessage;
    }

    public void setFirstMessage(FirstMessage[] firstMessage) {
        this.firstMessage = firstMessage;
    }

    public String getStateInfo() {
        return stateInfo;
    }

    public void setStateInfo(String stateInfo) {
        this.stateInfo = stateInfo;
    }

    public String getDataInfo() {
        return dataInfo;
    }

    public void setDataInfo(String dataInfo) {
        this.dataInfo = dataInfo;
    }

}
