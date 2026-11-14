package org.example.domain.business.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.domain.agent.model.entity.ArmoryCommandEntity;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.agent.model.valobj.AiAgentRegisterVO;
import org.example.domain.business.service.armory.AbstractArmorySupport;
import org.example.domain.business.service.armory.factory.DefaultArmoryFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author dabuw
 * @description
 * @date 11/11/26
 */
@Slf4j
@Service
public class ChatModelNode extends AbstractArmorySupport {

    @Resource
    private AgentNode agentNode;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - ChatModelNode");

        OpenAiApi openAiApi = dynamicContext.getOpenAiApi();

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.ChatModel chatModelConfig = aiAgentConfigTableVO.getModule().getChatModel();

        List<McpSyncClient> mcpSyncClients = new ArrayList<>();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolMcp> toolMcpList = chatModelConfig.getToolMcpList();
        for (AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp : toolMcpList) {
            mcpSyncClients.add(createMcpSyncClient(toolMcp));
        }

        // [DeepSeek 修改] 对 MCP 工具回调做参数过滤，避免模型自己填上百度不认的可选参数
        // 详见文件末尾 SafeToolCallback 的说明
        ToolCallback[] toolCallbacks = SafeToolCallback.wrap(SyncMcpToolCallbackProvider.builder()
                .mcpClients(mcpSyncClients).build()
                .getToolCallbacks());

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelConfig.getModel())
                        .toolCallbacks(toolCallbacks)
                        .build())
                .build();

        dynamicContext.setChatModel(chatModel);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return agentNode;
    }

    private McpSyncClient createMcpSyncClient(AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp) throws Exception {

        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.SSEServerParameters sseConfig = toolMcp.getSse();
        AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters stdioConfig = toolMcp.getStdio();

        if (null != sseConfig) {
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

            // MCP 客户端内部用 URI.resolve 拼接地址，要求：
            // baseUri 以 "/" 结尾、sseEndpoint 不以 "/" 开头，否则路径会被截断
            // 例：https://mcp.map.baidu.com + sse?ak=x 会被解析成 https://mcp.map.baidu.com/sse?ak=x
            //     这是对的；但 http://a.com/v2/mcp + sse?ak=x 会变成 http://a.com/v2/sse?ak=x，少了 mcp
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

            return mcpSyncClient;
        }

        if (null != stdioConfig) {
            AiAgentConfigTableVO.Module.ChatModel.ToolMcp.StdioServerParameters.ServerParameters serverParameters = stdioConfig.getServerParameters();

            ServerParameters stdioParams = ServerParameters.builder(serverParameters.getCommand())
                    .args(serverParameters.getArgs())
                    .env(serverParameters.getEnv())
                    .build();

            McpSyncClient mcpSyncClient = McpClient.sync(new StdioClientTransport(stdioParams, new JacksonMcpJsonMapper(new ObjectMapper())))
                    .requestTimeout(Duration.ofSeconds(stdioConfig.getRequestTimeout())).build();

            McpSchema.InitializeResult initialize = mcpSyncClient.initialize();

            log.info("tool stdio mcp initialize {}", initialize);

        }

        throw new RuntimeException("tool mcp sse and stdio is null!");
    }

    /**
     * [DeepSeek 修改] MCP 工具回调包装器：对模型传入的工具参数做黑名单过滤。
     * <p>
     * 背景：百度 AI 搜索工具（AIsearch）的入参 schema 里有若干可选参数，其中 model 会被 DeepSeek
     * 偶尔自动填上（例如 "deepseek-chat"）。百度侧会拿这个参数去校验 AppBuilder 应用的模型权限，
     * 返回 {"code":"invalid_appId","message":"No permission to use the appId"}；而 MCP 又把这段
     * 错误当成正常文本返回，模型只能读到一段错误 JSON，对外就表现成"搜索服务不可用 / 权限问题"。
     * <p>
     * 这些参数对工具本身可有可无，直接丢弃即可；query、resource_type_filter 等有效参数照常透传。
     * 包装是通用的，对地图等其它 MCP 工具没有影响（它们本来也不用这几个参数）。
     */
    private static final class SafeToolCallback implements ToolCallback {

        /** 百度 AI 搜索不认、会触发 invalid_appId 的可选参数 */
        private static final Set<String> BLOCKED_KEYS = Set.of("model", "instruction", "temperature", "top_p");

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final ToolCallback delegate;

        private SafeToolCallback(ToolCallback delegate) {
            this.delegate = delegate;
        }

        private static ToolCallback[] wrap(ToolCallback[] callbacks) {
            ToolCallback[] wrapped = new ToolCallback[callbacks.length];
            for (int i = 0; i < callbacks.length; i++) {
                wrapped[i] = new SafeToolCallback(callbacks[i]);
            }
            return wrapped;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String toolInput) {
            return delegate.call(filter(toolInput));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return delegate.call(filter(toolInput), toolContext);
        }

        private String filter(String toolInput) {
            if (StringUtils.isBlank(toolInput)) {
                return toolInput;
            }
            try {
                log.debug("[DeepSeek 修改] 工具原始参数: {}", toolInput);
                Map<String, Object> args = MAPPER.readValue(toolInput, new TypeReference<Map<String, Object>>() {
                });
                if (args == null) {
                    return toolInput;
                }
                boolean removed = args.keySet().removeAll(BLOCKED_KEYS);
                if (removed) {
                    log.info("[DeepSeek 修改] 已过滤工具参数，剩余参数: {}", args.keySet());
                }
                return MAPPER.writeValueAsString(args);
            } catch (Exception e) {
                log.warn("[DeepSeek 修改] 工具参数过滤失败，按原参数透传: {}", e.getMessage());
                return toolInput;
            }
        }
    }

}
