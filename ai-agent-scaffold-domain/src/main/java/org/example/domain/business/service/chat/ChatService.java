package org.example.domain.business.service.chat;

import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.domain.agent.model.entity.ArmoryCommandEntity;
import org.example.domain.agent.model.entity.ChatCommandEntity;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.agent.model.valobj.AiAgentRegisterVO;
import org.example.domain.agent.model.valobj.UserApiConfigVO;
import org.example.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import org.example.domain.business.service.IChatService;
import org.example.domain.business.service.armory.factory.DefaultArmoryFactory;
import org.example.types.enums.ResponseCode;
import org.example.types.exception.AppException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话服务。
 * <p>
 * 支持两种模型来源：
 * 1）启动时按 yml 装配好的默认智能体；
 * 2）用户在页面里填写的自带 API Key（请求级装配，用完即弃，不注册 Bean、不落库）。
 *
 * @author dabuw
 * @date 11/11/26
 */
@Slf4j
@Service
public class ChatService implements IChatService {

    /** 每分钟允许的对话次数 */
    private static final int RATE_LIMIT_PER_MINUTE = 30;
    /** 自带 API 的装配结果缓存条数 */
    private static final int USER_AGENT_CACHE_SIZE = 16;
    /** 错误信息回显长度上限 */
    private static final int ERROR_MSG_LIMIT = 160;

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    /** 用户会话缓存；同一用户重复创建会话时复用，避免每次对话都新建 Session */
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    /** 自带 API 的装配结果缓存：fingerprint -> 已装配好的智能体 */
    private final Map<String, AiAgentRegisterVO> userAgentCache = Collections.synchronizedMap(
            new LinkedHashMap<String, AiAgentRegisterVO>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, AiAgentRegisterVO> eldest) {
                    return size() > USER_AGENT_CACHE_SIZE;
                }
            });

    /** 简单的按用户限流桶 */
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();

        List<AiAgentConfigTableVO.Agent> agentList = new ArrayList<>();
        if (null != tables) {
            for (AiAgentConfigTableVO vo : tables.values()) {
                if (null != vo.getAgent()) {
                    agentList.add(vo.getAgent());
                }
            }
        }

        return agentList;
    }

    @Override
    public String createSession(String agentId, String userId) {
        return createSession(agentId, userId, null);
    }

    @Override
    public String createSession(String agentId, String userId, UserApiConfigVO apiConfig) {
        AiAgentRegisterVO registerVO = resolveAgent(agentId, apiConfig);
        InMemoryRunner runner = registerVO.getRunner();

        Session session = runner.sessionService()
                .createSession(registerVO.getAppName(), userId)
                .blockingGet();
        return session.id();
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String message) {
        return handleMessage(agentId, userId, null, message, null);
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {
        return handleMessage(agentId, userId, sessionId, message, null);
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message, UserApiConfigVO apiConfig) {
        checkRateLimit(userId);

        AiAgentRegisterVO registerVO = resolveAgent(agentId, apiConfig);
        InMemoryRunner runner = registerVO.getRunner();
        String useSessionId = StringUtils.isBlank(sessionId)
                ? createSession(agentId, userId, apiConfig)
                : sessionId;

        Content userMsg = Content.fromParts(Part.fromText(message));

        try {
            List<String> outputs = new ArrayList<>();
            runner.runAsync(userId, useSessionId, userMsg)
                    .blockingForEach(event -> outputs.add(event.stringifyContent()));
            return outputs;
        } catch (Exception e) {
            throw friendlyError(e, "对话失败");
        }
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
        return handleMessageStream(agentId, userId, sessionId, message, null);
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message, UserApiConfigVO apiConfig) {
        checkRateLimit(userId);

        AiAgentRegisterVO registerVO = resolveAgent(agentId, apiConfig);
        InMemoryRunner runner = registerVO.getRunner();
        String useSessionId = StringUtils.isBlank(sessionId)
                ? createSession(agentId, userId, apiConfig)
                : sessionId;

        Content userMsg = Content.fromParts(Part.fromText(message));
        return runner.runAsync(userId, useSessionId, userMsg);
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity) {
        return handleMessage(chatCommandEntity, null);
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity, UserApiConfigVO apiConfig) {
        checkRateLimit(chatCommandEntity.getUserId());

        AiAgentRegisterVO aiAgentRegisterVO = resolveAgent(chatCommandEntity.getAgentId(), apiConfig);

        List<Part> parts = new ArrayList<>();

        List<ChatCommandEntity.Content.Text> texts = chatCommandEntity.getTexts();
        if (null != texts && !texts.isEmpty()) {
            for (ChatCommandEntity.Content.Text text : texts) {
                parts.add(Part.fromText(text.getMessage()));
            }
        }

        List<ChatCommandEntity.Content.File> files = chatCommandEntity.getFiles();
        if (null != files && !files.isEmpty()) {
            for (ChatCommandEntity.Content.File file : files) {
                parts.add(Part.fromUri(file.getFileUri(), file.getMimeType()));
            }
        }

        List<ChatCommandEntity.Content.InlineData> inlineDatas = chatCommandEntity.getInlineDatas();
        if (null != inlineDatas && !inlineDatas.isEmpty()) {
            for (ChatCommandEntity.Content.InlineData inlineData : inlineDatas) {
                parts.add(Part.fromBytes(inlineData.getBytes(), inlineData.getMimeType()));
            }
        }

        Content content = Content.builder().role("user").parts(parts).build();

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();
        String sessionId = StringUtils.isBlank(chatCommandEntity.getSessionId())
                ? createSession(chatCommandEntity.getAgentId(), chatCommandEntity.getUserId(), apiConfig)
                : chatCommandEntity.getSessionId();

        try {
            List<String> outputs = new ArrayList<>();
            runner.runAsync(chatCommandEntity.getUserId(), sessionId, content)
                    .blockingForEach(event -> outputs.add(event.stringifyContent()));
            return outputs;
        } catch (Exception e) {
            throw friendlyError(e, "对话失败");
        }
    }

    /**
     * 连通性测试：用用户填写的配置发一次最小请求。
     */
    public String testApi(UserApiConfigVO apiConfig) {
        if (null == apiConfig || !apiConfig.usable()) {
            throw new AppException(ResponseCode.E0004.getCode(), "请先填写 Base URL 和 API Key");
        }

        try {
            ChatModel chatModel = buildChatModel(apiConfig);
            String reply = chatModel.call("ping");
            String text = StringUtils.defaultString(reply).replaceAll("\\s+", " ").trim();
            return StringUtils.abbreviate(text, 80);
        } catch (Exception e) {
            throw friendlyError(e, "连通性测试失败");
        }
    }

    private ChatModel buildChatModel(UserApiConfigVO apiConfig) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(apiConfig.getBaseUrl())
                .apiKey(apiConfig.getApiKey())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(StringUtils.defaultIfBlank(apiConfig.getModel(), "deepseek-chat"))
                        .build())
                .build();
    }

    /**
     * 解析可用的智能体：没有自带配置时用启动装配好的，有则按请求现场装配并缓存。
     */
    private AiAgentRegisterVO resolveAgent(String agentId, UserApiConfigVO apiConfig) {
        if (null == apiConfig || !apiConfig.usable()) {
            AiAgentRegisterVO registerVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);
            if (null == registerVO) {
                throw new AppException(ResponseCode.E0001.getCode());
            }
            return registerVO;
        }

        String cacheKey = apiConfig.fingerprint(agentId);
        AiAgentRegisterVO cached = userAgentCache.get(cacheKey);
        if (null != cached) {
            return cached;
        }

        AiAgentRegisterVO built = buildAgentWithUserApi(agentId, apiConfig);
        userAgentCache.put(cacheKey, built);
        return built;
    }

    private AiAgentRegisterVO buildAgentWithUserApi(String agentId, UserApiConfigVO apiConfig) {
        AiAgentConfigTableVO configTable = findConfigTable(agentId);
        if (null == configTable) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(apiConfig.getBaseUrl())
                .apiKey(apiConfig.getApiKey())
                .build();

        DefaultArmoryFactory.DynamicContext dynamicContext = new DefaultArmoryFactory.DynamicContext();
        dynamicContext.setOpenAiApi(openAiApi);
        dynamicContext.setOverrideModel(apiConfig.getModel());
        // 关键：不要注册成 Bean，否则会把默认智能体覆盖掉
        dynamicContext.setSkipRegisterBean(true);

        ArmoryCommandEntity commandEntity = ArmoryCommandEntity.builder()
                .aiAgentConfigTableVO(configTable)
                .build();

        try {
            AiAgentRegisterVO registerVO = defaultArmoryFactory.armoryStrategyHandler()
                    .apply(commandEntity, dynamicContext);
            if (null == registerVO) {
                throw new AppException(ResponseCode.E0005.getCode(), "使用自带 API 装配智能体失败");
            }
            log.info("使用用户自带 API 装配完成 agentId:{} model:{}", agentId, apiConfig.getModel());
            return registerVO;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw friendlyError(e, "使用自带 API 装配智能体失败");
        }
    }

    private AiAgentConfigTableVO findConfigTable(String agentId) {
        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();
        if (null == tables) {
            return null;
        }
        for (AiAgentConfigTableVO table : tables.values()) {
            if (null != table.getAgent() && StringUtils.equals(agentId, table.getAgent().getAgentId())) {
                return table;
            }
        }
        return null;
    }

    private void checkRateLimit(String userId) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Long> bucket = rateBuckets.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > 60_000L) {
                bucket.pollFirst();
            }
            if (bucket.size() >= RATE_LIMIT_PER_MINUTE) {
                throw new AppException(ResponseCode.E0003.getCode(),
                        "请求过于频繁，每分钟最多 " + RATE_LIMIT_PER_MINUTE + " 次，请稍后再试");
            }
            bucket.addLast(now);
        }
    }

    /**
     * 把底层模型的英文异常翻译成用户能看懂的中文提示。
     */
    private AppException friendlyError(Exception e, String fallback) {
        String message = StringUtils.defaultString(e.getMessage());
        String lower = message.toLowerCase();

        String hint;
        if (containsAny(lower, "401", "unauthorized", "invalid api key", "incorrect api key", "authentication")) {
            hint = "API Key 无效或已过期，请检查后重试";
        } else if (containsAny(lower, "403", "forbidden", "permission")) {
            hint = "该 API Key 没有访问权限，请确认账号已开通对应模型";
        } else if (containsAny(lower, "429", "rate limit", "too many requests")) {
            hint = "模型服务限流，请稍后再试";
        } else if (containsAny(lower, "insufficient", "quota", "balance", "arrears")) {
            hint = "账户额度不足，请充值或更换 API Key";
        } else if (containsAny(lower, "model") && containsAny(lower, "not found", "does not exist", "not exist")) {
            hint = "模型不存在或不可用，请更换模型名称";
        } else if (containsAny(lower, "timeout", "timed out", "read timed out")) {
            hint = "请求超时，请稍后重试";
        } else {
            hint = fallback + (StringUtils.isBlank(message) ? "" : "：" + StringUtils.abbreviate(message, ERROR_MSG_LIMIT));
        }

        log.warn("对话异常已友好化处理: {}", message);
        return new AppException(ResponseCode.E0005.getCode(), hint);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

}
