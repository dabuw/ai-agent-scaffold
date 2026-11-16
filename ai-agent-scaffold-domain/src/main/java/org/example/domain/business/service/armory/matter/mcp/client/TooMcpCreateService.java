package org.example.domain.business.service.armory.matter.mcp.client;

import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.ai.tool.ToolCallback;

/**工具 MCP 构建服务
 * @author dabuw
 * @description
 * @date 14/11/26
 */
public interface TooMcpCreateService {

    ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception;

}
