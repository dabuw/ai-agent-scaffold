package org.example.domain.business.service.armory.matter.skills;

import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

/**
 * 工具 skills 构建服务
 *
 */
public interface ToolSkillsCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills) throws Exception;

}
