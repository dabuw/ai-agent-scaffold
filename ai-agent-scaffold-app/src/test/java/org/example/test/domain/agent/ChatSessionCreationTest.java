package org.example.test.domain.agent;

import com.google.adk.agents.SequentialAgent;
import com.google.adk.runner.InMemoryRunner;
import org.example.domain.agent.model.valobj.AiAgentRegisterVO;
import org.example.domain.business.service.armory.factory.DefaultArmoryFactory;
import org.example.domain.business.service.chat.ChatService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChatSessionCreationTest {

    @Test
    public void createsNewSessionsForEachRequestAndAgent() {
        DefaultArmoryFactory factory = mock(DefaultArmoryFactory.class);
        InMemoryRunner firstRunner = new InMemoryRunner(SequentialAgent.builder().name("first").build(), "first");
        InMemoryRunner secondRunner = new InMemoryRunner(SequentialAgent.builder().name("second").build(), "second");
        when(factory.getAiAgentRegisterVO("first")).thenReturn(
                AiAgentRegisterVO.builder().appName("first").runner(firstRunner).build());
        when(factory.getAiAgentRegisterVO("second")).thenReturn(
                AiAgentRegisterVO.builder().appName("second").runner(secondRunner).build());

        ChatService service = new ChatService();
        ReflectionTestUtils.setField(service, "defaultArmoryFactory", factory);

        String first = service.createSession("first", "admin");
        String next = service.createSession("first", "admin");
        String otherAgent = service.createSession("second", "admin");

        assertNotEquals(first, next);
        assertNotEquals(first, otherAgent);
        assertNotEquals(next, otherAgent);
        assertNotNull(firstRunner.sessionService().getSession("first", "admin", first, Optional.empty()).blockingGet());
        assertNotNull(secondRunner.sessionService().getSession("second", "admin", otherAgent, Optional.empty()).blockingGet());
    }
}
