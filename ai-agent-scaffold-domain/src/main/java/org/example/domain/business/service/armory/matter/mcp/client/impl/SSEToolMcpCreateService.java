package org.example.domain.business.service.armory.matter.mcp.client.impl;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.business.service.armory.matter.mcp.client.TooMcpCreateService;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;

/**
 * @author dabuw
 * @description
 * @date 14/11/26
 */
@Slf4j
@Service
public class SSEToolMcpCreateService implements TooMcpCreateService {

    @Override
    public ToolCallback[] buildToolCallback(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.SSEServerParameters sseConfig = toolMcp.getSse();

        // baseUri / sseEndpoint 均来自配置（agent/*.yml），鉴权参数由 ${BAIDU_MCP_API_KEY} 这类占位符注入，代码里不保留任何 key

        String originalBaseUri = sseConfig.getBaseUri();
        String baseUri = originalBaseUri;
        String sseEndpoint = sseConfig.getSseEndpoint();

        if (StringUtils.isBlank(sseEndpoint)) {
            URL url = new URL(originalBaseUri);

            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();

            String baseUrl = port == -1 ? protocol + "://" + host : protocol + "://" + host + ":" + port;

            int index = originalBaseUri.indexOf(baseUrl);
            if (index != -1) {
                sseEndpoint = originalBaseUri.substring(index + baseUrl.length());
            }

            baseUri = baseUrl;
        }

        sseEndpoint = StringUtils.isBlank(sseEndpoint) ? "/sse" : sseEndpoint;

        // MCP 客户端内部用 URI.resolve 拼接地址，要求 baseUri 以 "/" 结尾、sseEndpoint 不以 "/" 开头，
        // 否则路径会被截断（例：http://a.com/v2/mcp + sse?ak=x 会变成 http://a.com/v2/sse?ak=x）
        if (!sseEndpoint.startsWith("/") && !baseUri.endsWith("/")) {
            baseUri = baseUri + "/";
        }

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                .builder(baseUri)
                .sseEndpoint(sseEndpoint)
                .build();

        McpSyncClient mcpSyncClient = McpClient
                .sync(sseClientTransport)
                .requestTimeout(Duration.ofMillis(sseConfig.getRequestTimeout())).build();
        McpSchema.InitializeResult initialize = mcpSyncClient.initialize();

        log.info("tool sse mcp initialize {}", initialize);

        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClient).build()
                .getToolCallbacks();
    }

}
