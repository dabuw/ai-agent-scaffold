package org.example.test.support;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型测试统一配置（DeepSeek，OpenAI 兼容协议）。
 * <p>
 * 取值优先级：环境变量 &gt; JVM -D 参数 &gt; .env 文件 &gt; 内置默认值。
 * .env 的查找位置：从工作目录开始向上最多 5 层，找 .env 和 docs/dev-ops/api/.env。
 *
 * @author org.example
 */
public final class LlmConfig {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private LlmConfig() {
    }

    public static String baseUrl() {
        return get("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL);
    }

    /** LangChain4j 的 baseUrl 需要带 /v1 */
    public static String baseUrlV1() {
        return baseUrl().replaceAll("/+$", "") + "/v1";
    }

    public static String model() {
        return get("DEEPSEEK_MODEL", DEFAULT_MODEL);
    }

    /**
     * 多模态用例使用的模型。DeepSeek 目前没有视觉模型，需要跑图片用例时在 .env 里配置
     * VISION_BASE_URL / VISION_API_KEY / VISION_MODEL 指向支持图片输入的服务。
     */
    public static String visionModel() {
        return get("VISION_MODEL", model());
    }

    /** 多模态用例使用的 baseUrl（默认复用 DEEPSEEK_BASE_URL） */
    public static String visionBaseUrl() {
        return get("VISION_BASE_URL", baseUrl());
    }

    /** 多模态用例使用的 apiKey（默认复用 DEEPSEEK_API_KEY） */
    public static String visionApiKey() {
        String value = get("VISION_API_KEY", null);
        return (value == null || value.isBlank()) ? apiKey() : value;
    }

    public static String apiKey() {
        return required("DEEPSEEK_API_KEY",
                "没有找到可用的 DEEPSEEK_API_KEY，请填到 .env（项目根目录或 docs/dev-ops/api/.env），"
                        + "或设置同名环境变量后再运行。");
    }

    /** 百度 AI 搜索 MCP 的基地址 */
    public static String mcpBaseUrl() {
        return get("BAIDU_MCP_BASE_URL", "http://appbuilder.baidu.com/v2/ai_search/mcp/");
    }

    /** 百度 AI 搜索 MCP 的 API Key（bce-v3/ALTAK-xxx/yyy） */
    public static String mcpApiKey() {
        return required("BAIDU_MCP_API_KEY",
                "没有找到 BAIDU_MCP_API_KEY，请填到 .env（docs/dev-ops/api/.env），"
                        + "申请地址：https://console.bce.baidu.com/iam/#/iam/apikey/list");
    }

    /** 完整 SSE 端点，可直接传给 sseEndpoint(...) */
    public static String mcpSseEndpoint() {
        return "sse?api_key=" + mcpApiKey();
    }

    public static OpenAiApi openAiApi() {
        return OpenAiApi.builder()
                .baseUrl(baseUrl())
                .apiKey(apiKey())
                .completionsPath("v1/chat/completions")
                .build();
    }

    public static ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi())
                .defaultOptions(OpenAiChatOptions.builder().model(model()).build())
                .build();
    }

    /** 多模态用例使用的 ChatModel */
    public static ChatModel visionChatModel() {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(visionBaseUrl())
                        .apiKey(visionApiKey())
                        .completionsPath("v1/chat/completions")
                        .build())
                .defaultOptions(OpenAiChatOptions.builder().model(visionModel()).build())
                .build();
    }

    private static String required(String name, String hint) {
        String value = get(name, null);
        if (value == null || value.isBlank() || value.contains("在这里粘贴")) {
            throw new IllegalStateException(hint);
        }
        return value;
    }

    private static String get(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        if (value == null || value.isBlank()) {
            value = DOT_ENV.get(name);
        }
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> map = new HashMap<>();
        for (Path path : candidates()) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String text = line.trim();
                    if (text.isEmpty() || text.startsWith("#")) {
                        continue;
                    }
                    int index = text.indexOf('=');
                    if (index <= 0) {
                        continue;
                    }
                    String key = text.substring(0, index).trim();
                    String value = text.substring(index + 1).trim();
                    if (value.length() > 1 && (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (!key.isEmpty()) {
                        map.putIfAbsent(key, value);
                    }
                }
            } catch (IOException ignored) {
                // 读不了就跳过，继续找下一个候选文件
            }
        }
        return map;
    }

    private static List<Path> candidates() {
        List<Path> list = new ArrayList<>();
        Path dir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++, dir = dir.getParent()) {
            list.add(dir.resolve(".env"));
            list.add(dir.resolve("docs/dev-ops/api/.env"));
        }
        return list;
    }

}
