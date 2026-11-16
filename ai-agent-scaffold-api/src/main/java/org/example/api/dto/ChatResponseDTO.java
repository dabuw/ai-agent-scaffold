package org.example.api.dto;

import lombok.Data;

/**
 * @author dabuw
 * @description
 * @date 15/11/26
 */
@Data
public class ChatResponseDTO {
    private String content;

    /** 本次对话使用的会话 ID，前端下一轮带上它即可保持上下文 */
    private String sessionId;
}
