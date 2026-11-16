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

}

