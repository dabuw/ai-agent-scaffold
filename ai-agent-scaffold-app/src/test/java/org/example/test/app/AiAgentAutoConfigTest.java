package org.example.test.app;

import com.alibaba.fastjson.JSON;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.agent.model.valobj.AiAgentRegisterVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MimeTypeUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * @author dabuw
 * @description
 * @date 12/11/26
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AiAgentAutoConfigTest {

    @Resource
    private ApplicationContext applicationContext;

    @Test
    public void test_agent_01() throws InterruptedException {
        AiAgentRegisterVO aiAgentRegisterVO = applicationContext.getBean("100001", AiAgentRegisterVO.class);

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService()
                .createSession(appName, "dabuw")
                .blockingGet();

        Content userMsg = Content.fromParts(Part.fromText("编写冒泡排序"));
        Flowable<Event> events = runner.runAsync("dabuw", session.id(), userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        log.info("测试结果:{}", JSON.toJSONString(outputs));

        new CountDownLatch(1).await();
    }

    @Test
    public void test_handlerMessage_02(){
        AiAgentRegisterVO aiAgentRegisterVO = applicationContext.getBean("100002", AiAgentRegisterVO.class);

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService()
                .createSession(appName, "dabuw02")
                .blockingGet();

        Content userMsg = Content.fromParts(Part.fromText("把dabuw转换为大写"));
        Flowable<Event> events = runner.runAsync("dabuw02", session.id(), userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        log.info("测试结果:{}", JSON.toJSONString(outputs));
    }

    /**
     * 多模态用例：把 dog.png 作为图片输入交给智能体。
     * 需要模型支持图片输入，DeepSeek 暂无视觉模型，先配好 VISION_MODEL / VISION_BASE_URL / VISION_API_KEY 再跑。
     */
    @Test
    public void test_handlerMessage_02_image() throws IOException {
        AiAgentRegisterVO aiAgentRegisterVO = applicationContext.getBean("100002", AiAgentRegisterVO.class);

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService()
                .createSession(appName, "dabuw")
                .blockingGet();

        org.springframework.core.io.Resource resource = new ClassPathResource("file/dog.png");
        Content userMsg = Content.fromParts(
                Part.fromText("请描述这张图片的主要内容，并说明图中物品的可能用途。"),
                Part.fromBytes(resource.getContentAsByteArray(), MimeTypeUtils.IMAGE_PNG_VALUE));

        Flowable<Event> events = runner.runAsync("dabuw", session.id(), userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        log.info("测试结果:{}", JSON.toJSONString(outputs));
    }

    @Test
    public void test_handlerMessage_03(){
        AiAgentRegisterVO aiAgentRegisterVO = applicationContext.getBean("100003", AiAgentRegisterVO.class);

        String appName = aiAgentRegisterVO.getAppName();
        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Session session = runner.sessionService()
                .createSession(appName, "dabuw03")
                .blockingGet();

        Content userMsg = Content.fromParts(Part.fromText("你具备哪些能力"));
        Flowable<Event> events = runner.runAsync("dabuw03", session.id(), userMsg);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        log.info("测试结果:{}", JSON.toJSONString(outputs));
    }

}

