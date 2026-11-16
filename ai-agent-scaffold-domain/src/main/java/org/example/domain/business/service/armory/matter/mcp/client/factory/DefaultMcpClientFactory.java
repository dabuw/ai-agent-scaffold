package org.example.domain.business.service.armory.matter.mcp.client.factory;

import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.business.service.armory.matter.mcp.client.TooMcpCreateService;
import org.example.domain.business.service.armory.matter.mcp.client.impl.LocalToolMcpCreateService;
import org.example.domain.business.service.armory.matter.mcp.client.impl.SSEToolMcpCreateService;
import org.example.domain.business.service.armory.matter.mcp.client.impl.StdioToolMcpCreateService;
import org.example.types.enums.ResponseCode;
import org.example.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author dabuw
 * @description
 * @date 14/11/26
 */
@Slf4j
@Service
public class DefaultMcpClientFactory {

    @Resource
    private LocalToolMcpCreateService localToolMcpCreateService;

    @Resource
    private SSEToolMcpCreateService sseToolMcpCreateService;

    @Resource
    private StdioToolMcpCreateService stdioToolMcpCreateService;

    public TooMcpCreateService getTooMcpCreateService(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) {
        if (null != toolMcp.getLocal()) return localToolMcpCreateService;
        if (null != toolMcp.getSse()) return sseToolMcpCreateService;
        if (null != toolMcp.getStdio()) return stdioToolMcpCreateService;
        throw new AppException(ResponseCode.NOT_FOUND_METHOD.getCode(), ResponseCode.NOT_FOUND_METHOD.getInfo());
    }
}
