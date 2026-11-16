package org.example.test;

import com.alibaba.fastjson.JSON;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import org.example.domain.business.service.armory.matter.springai.MediaAwareSpringAI;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.example.test.support.LlmConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.io.InputStream;

/**
 * 多模态（图片输入）验证。
 * <p>
 * 需要模型支持图片输入；DeepSeek 目前没有视觉模型，跑之前请在 .env 里配置
 * VISION_BASE_URL / VISION_API_KEY / VISION_MODEL。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

    @Test
    public void test() {
        log.info("测试完成");
    }

    public static void main(String[] args) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream resourceAsStream = classLoader.getResourceAsStream("dog.png");
        Resource resource = new ClassPathResource("dog.png", classLoader);
        assert resourceAsStream != null;

        ChatModel chatModel = LlmConfig.visionChatModel();

        // 1）直接用 Spring AI 调多模态模型
        ChatResponse response = chatModel.call(new Prompt(
                UserMessage.builder()
                        .text("请描述这张图片的主要内容，并说明图中物品的可能用途。")
                        .media(Media.builder()
                                .mimeType(MimeType.valueOf(MimeTypeUtils.IMAGE_PNG_VALUE))
                                .data(resource)
                                .build())
                        .build(),
                OpenAiChatOptions.builder()
                        .model(LlmConfig.visionModel())
                        .build()));

        System.out.println("模型测试结果: " + JSON.toJSONString(response.getResult().getOutput().getText()));

        // 2）走一遍 ADK，验证 Agent 侧的多模态链路
        LlmAgent agent = LlmAgent.builder()
                .name("test")
                .description("图片描述助手")
                .model(new MediaAwareSpringAI(chatModel))
                .instruction("""
                        You are a helpful assistant who describes images in detail.
                        """)
                .build();

        InMemoryRunner runner = new InMemoryRunner(agent);

        Session session = runner
                .sessionService()
                .createSession("test", "dabuw")
                .blockingGet();

        Flowable<Event> events = runner.runAsync("dabuw", session.id(),
                Content.fromParts(Part.fromText("这是什么图片"),
                        Part.fromBytes(resource.getContentAsByteArray(), MimeTypeUtils.IMAGE_PNG_VALUE)));

        System.out.print("\nAgent > ");
        events.blockingForEach(event -> System.out.println(event.stringifyContent()));
    }

}
