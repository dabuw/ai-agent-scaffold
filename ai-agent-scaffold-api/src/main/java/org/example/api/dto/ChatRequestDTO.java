package org.example.api.dto;

import lombok.Data;

/**
 * @author dabuw
 * @description
 * @date 15/11/26
 */
@Data
public class ChatRequestDTO {

    private String agentId;
    private String userId;
    private String sessionId;
    private String message;


    /** 用户自带的模型地址，例如 https://api.deepseek.com */
    private String apiBaseUrl;

    /** 用户自带的 API Key */
    private String apiKey;

    /** 模型名称，例如 deepseek-chat */
    private String model;

}

