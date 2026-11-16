package org.example.api.dto;

import lombok.Data;

/**
 * @author dabuw
 * @description
 * @date 15/11/26
 */
@Data
public class CreateSessionRequestDTO {
    private String agentId;
    private String userId;
}
