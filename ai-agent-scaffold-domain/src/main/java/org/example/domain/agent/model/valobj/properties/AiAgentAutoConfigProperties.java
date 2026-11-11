package org.example.domain.agent.model.valobj.properties;

import lombok.Data;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @author dabuw
 * @description
 * @date 11/11/26
 */

@Data
@ConfigurationProperties(prefix = "ai.agent.config", ignoreInvalidFields = true)
public class AiAgentAutoConfigProperties {

    private boolean enabled = false;

    private Map<String, AiAgentConfigTableVO> tables;

}
