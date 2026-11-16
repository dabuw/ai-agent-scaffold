package org.example.domain.business.service;

import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import org.example.domain.agent.model.entity.ChatCommandEntity;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;

import java.util.List;

/**
 * @author dabuw
 * @description
 * @date 11/11/26
 */
public interface IChatService {

    List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList();

    String createSession(String agentId, String userId);

    List<String> handleMessage(String agentId, String userId, String message);

    List<String> handleMessage(String agentId, String userId, String sessionId, String message);

    Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message);

    List<String> handleMessage(ChatCommandEntity chatCommandEntity);

}