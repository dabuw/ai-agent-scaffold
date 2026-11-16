package org.example.domain.business.service;

import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import org.example.domain.agent.model.entity.ChatCommandEntity;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.agent.model.valobj.UserApiConfigVO;

import java.util.List;

/**
 * @author dabuw
 * @description
 * @date 11/11/26
 */
public interface IChatService {

    List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList();

    String createSession(String agentId, String userId);

    /** 使用用户自带 API 创建会话 */
    String createSession(String agentId, String userId, UserApiConfigVO apiConfig);

    List<String> handleMessage(String agentId, String userId, String message);

    List<String> handleMessage(String agentId, String userId, String sessionId, String message);

    /** 使用用户自带 API 进行对话 */
    List<String> handleMessage(String agentId, String userId, String sessionId, String message, UserApiConfigVO apiConfig);

    Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message);

    /** 使用用户自带 API 的流式对话 */
    Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message, UserApiConfigVO apiConfig);

    List<String> handleMessage(ChatCommandEntity chatCommandEntity);

    /** 使用用户自带 API 做一次连通性测试，返回模型回复摘要 */
    String testApi(UserApiConfigVO apiConfig);

}