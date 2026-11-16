package org.example.test.api.tool.skills;

import lombok.extern.slf4j.Slf4j;
import org.example.test.support.LlmConfig;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

/**
 * Skills 能力验证：把 agent/skills 下的技能目录挂给模型，让模型按技能说明来回答。
 * <p>
 * 依赖 spring-ai-agent-utils 的 SkillsTool。
 */
@Slf4j
public class SkillsToolTest {

    public static void main(String[] args) {
        // resource 方式：技能放在 classpath 下（src/main/resources/agent/skills）
        ToolCallback skillsTool = SkillsTool.builder()
                .addSkillsResource(new ClassPathResource("agent/skills"))
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(LlmConfig.openAiApi())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(LlmConfig.model())
                        .toolCallbacks(List.of(skillsTool))
                        .build())
                .build();

        String call = chatModel.call("基于 skill 解答，电脑性能优化");
        log.info("测试结果:{}", call);
    }

}
