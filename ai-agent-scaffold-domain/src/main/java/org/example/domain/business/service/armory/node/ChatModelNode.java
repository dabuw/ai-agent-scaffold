package org.example.domain.business.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.domain.agent.model.entity.ArmoryCommandEntity;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.agent.model.valobj.AiAgentRegisterVO;
import org.example.domain.business.service.armory.AbstractArmorySupport;
import org.example.domain.business.service.armory.factory.DefaultArmoryFactory;
import org.example.domain.business.service.armory.matter.mcp.client.TooMcpCreateService;
import org.example.domain.business.service.armory.matter.mcp.client.factory.DefaultMcpClientFactory;
import org.example.domain.business.service.armory.matter.skills.ToolSkillsCreateService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

    @Resource
    private DefaultMcpClientFactory defaultMcpClientFactory;

    @Resource
    private ToolSkillsCreateService toolSkillsCreateService;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - ChatModelNode");

        // 获取上下文对象
        OpenAiApi openAiApi = dynamicContext.getOpenAiApi();

        // 获取配置对象
        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.ChatModel chatModelConfig = aiAgentConfigTableVO.getModule().getChatModel();
        List<AiAgentConfigTableVO.Module.ChatModel.ToolMcp> toolMcpList = chatModelConfig.getToolMcpList();

        // 构建 MCP 工具（工厂按 local / sse / stdio 分发）
        List<ToolCallback> toolCallbackList = new ArrayList<>();
        if (null != toolMcpList) {
            for (AiAgentConfigTableVO.Module.ChatModel.ToolMcp toolMcp : toolMcpList) {
                TooMcpCreateService tooMcpCreateService = defaultMcpClientFactory.getTooMcpCreateService(toolMcp);
                ToolCallback[] toolCallbacks = tooMcpCreateService.buildToolCallback(toolMcp);
                // [DeepSeek 修改] 每个工具回调统一包一层参数过滤，避免模型自行填上百度不认的可选参数
                // 详见文件末尾 SafeToolCallback 的说明
                toolCallbackList.addAll(List.of(SafeToolCallback.wrap(toolCallbacks)));
            }
        }

        // 构建 skills 工具（spring-ai-agent-utils 的 SkillsTool）
        List<AiAgentConfigTableVO.Module.ChatModel.ToolSkills> toolSkillsList = chatModelConfig.getToolSkillsList();
        if (null != toolSkillsList && !toolSkillsList.isEmpty()) {
            for (AiAgentConfigTableVO.Module.ChatModel.ToolSkills toolSkills : toolSkillsList) {
                ToolCallback[] skillCallbacks = toolSkillsCreateService.buildToolCallback(toolSkills);
                toolCallbackList.addAll(List.of(skillCallbacks));
            }
        }

        // 构建对话模型
        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(chatModelConfig.getModel())
                        .toolCallbacks(toolCallbackList)
                        .build())
                .build();

        dynamicContext.setChatModel(chatModel);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return agentNode;
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
