package org.example.api;

import org.example.api.dto.*;
import org.example.api.response.Response;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * @author dabuw
 * @description 智能体服务接口
 * @date 15/11/26
 */
public interface IAgentService {

    Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList();

    Response<CreateSessionResponseDTO> createSession(CreateSessionRequestDTO requestDTO);

    Response<ChatResponseDTO> chat(ChatRequestDTO requestDTO);

    ResponseBodyEmitter chatStream(ChatRequestDTO requestDTO);
}