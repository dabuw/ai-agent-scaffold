package org.example.api.dto;

import lombok.Data;

/**
 * @author dabuw
 * @description 智能体配置响应对象
 * @date 15/11/26
 */
@Data
public class AiAgentConfigResponseDTO {

    /**
     * 智能体ID
     */
    private String agentId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 智能体描述
     */
    private String agentDesc;

}

