package org.example.trigger.http;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.example.api.IAgentService;
import org.example.api.dto.*;
import org.example.api.response.Response;
import org.example.domain.agent.model.entity.ChatCommandEntity;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.agent.model.valobj.UserApiConfigVO;
import org.example.domain.business.service.IChatService;
import org.example.types.enums.ResponseCode;
import org.example.types.exception.AppException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author dabuw
 * @description
 * @date 15/11/26
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/")
@CrossOrigin(origins = "*")
public class AgentServiceController implements IAgentService {

    /** 单张图片最大 2MB、单次最多 3 张 */
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_COUNT = 3;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    @Resource
    private IChatService chatService;

    @RequestMapping(value = "query_ai_agent_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<AiAgentConfigResponseDTO>> queryAiAgentConfigList() {
        try {
            log.info("查询智能体配置列表");

            List<AiAgentConfigTableVO.Agent> agentConfigs = chatService.queryAiAgentConfigList();

            List<AiAgentConfigResponseDTO> responseDTOS = agentConfigs.stream().map(agentConfig -> {
                AiAgentConfigResponseDTO responseDTO = new AiAgentConfigResponseDTO();
                responseDTO.setAgentId(agentConfig.getAgentId());
                responseDTO.setAgentName(agentConfig.getAgentName());
                responseDTO.setAgentDesc(agentConfig.getAgentDesc());
                return responseDTO;
            }).collect(Collectors.toList());

            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOS)
                    .build();

        } catch (AppException e) {
            log.error("查询智能体配置列表异常", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(e.getCode())
                    .info(info(e, ResponseCode.UN_ERROR))
                    .build();
        } catch (Exception e) {
            log.error("查询智能体配置列表失败", e);
            return Response.<List<AiAgentConfigResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "create_session", method = RequestMethod.POST)
    @Override
    public Response<CreateSessionResponseDTO> createSession(@RequestBody CreateSessionRequestDTO requestDTO) {
        try {
            log.info("创建会话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());
            String sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId(),
                    toApiConfig(requestDTO.getApiBaseUrl(), requestDTO.getApiKey(), requestDTO.getModel()));

            CreateSessionResponseDTO responseDTO = new CreateSessionResponseDTO();
            responseDTO.setSessionId(sessionId);

            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.warn("创建会话失败: {}", e.getInfo());
            return Response.<CreateSessionResponseDTO>builder()
                    .code(e.getCode())
                    .info(info(e, ResponseCode.UN_ERROR))
                    .build();
        } catch (Exception e) {
            log.error("创建会话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<CreateSessionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 兼容 GET 方式的创建会话，方便浏览器/前端直接跳转调用
     */
    @RequestMapping(value = "create_session", method = RequestMethod.GET)
    public Response<CreateSessionResponseDTO> createSession(@RequestParam("agentId") String agentId,
                                                            @RequestParam("userId") String userId) {
        CreateSessionRequestDTO requestDTO = new CreateSessionRequestDTO();
        requestDTO.setAgentId(agentId);
        requestDTO.setUserId(userId);
        return createSession(requestDTO);
    }

    @RequestMapping(value = "chat", method = RequestMethod.POST)
    @Override
    public Response<ChatResponseDTO> chat(@RequestBody ChatRequestDTO requestDTO) {
        try {
            log.info("智能体对话 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId());

            UserApiConfigVO apiConfig = toApiConfig(requestDTO.getApiBaseUrl(), requestDTO.getApiKey(), requestDTO.getModel());

            String sessionId = requestDTO.getSessionId();
            if (StringUtils.isBlank(sessionId)) {
                sessionId = chatService.createSession(requestDTO.getAgentId(), requestDTO.getUserId(), apiConfig);
            }

            List<String> messages;
            if (null != requestDTO.getImages() && !requestDTO.getImages().isEmpty()) {
                // 带图片：走多模态内容
                ChatCommandEntity commandEntity = buildImageCommand(requestDTO, sessionId);
                messages = chatService.handleMessage(commandEntity, apiConfig);
            } else {
                messages = chatService.handleMessage(requestDTO.getAgentId(), requestDTO.getUserId(),
                        sessionId, requestDTO.getMessage(), apiConfig);
            }

            ChatResponseDTO responseDTO = new ChatResponseDTO();
            responseDTO.setContent(String.join("\n", messages));
            responseDTO.setSessionId(sessionId);

            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (AppException e) {
            log.warn("智能体对话异常: {}", e.getInfo());
            return Response.<ChatResponseDTO>builder()
                    .code(e.getCode())
                    .info(info(e, ResponseCode.UN_ERROR))
                    .build();
        } catch (Exception e) {
            log.error("智能体对话失败 agentId:{} userId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), e);
            return Response.<ChatResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "chat_stream", method = RequestMethod.POST)
    @Override
    public ResponseBodyEmitter chatStream(@RequestBody ChatRequestDTO requestDTO) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(3 * 60 * 1000L);
        try {
            log.info("流式对话 agentId:{} userId:{} sessionId:{}", requestDTO.getAgentId(), requestDTO.getUserId(), requestDTO.getSessionId());
            chatService.handleMessageStream(requestDTO.getAgentId(), requestDTO.getUserId(),
                            requestDTO.getSessionId(), requestDTO.getMessage(),
                            toApiConfig(requestDTO.getApiBaseUrl(), requestDTO.getApiKey(), requestDTO.getModel()))
                    .subscribe(
                            event -> {
                                try {
                                    String chunk = event.stringifyContent();
                                    if (StringUtils.isNotEmpty(chunk)) {
                                        emitter.send(chunk);
                                    }
                                } catch (Exception e) {
                                    log.error("流式对话发送失败", e);
                                    emitter.completeWithError(e);
                                }
                            },
                            error -> {
                                log.warn("流式对话异常: {}", error.getMessage());
                                emitter.completeWithError(error);
                            },
                            emitter::complete
                    );
        } catch (AppException e) {
            log.warn("流式对话异常: {}", e.getInfo());
            emitter.completeWithError(e);
        } catch (Exception e) {
            log.error("流式对话失败", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @RequestMapping(value = "test_api", method = RequestMethod.POST)
    @Override
    public Response<String> testApi(@RequestBody TestApiRequestDTO requestDTO) {
        try {
            String reply = chatService.testApi(
                    toApiConfig(requestDTO.getApiBaseUrl(), requestDTO.getApiKey(), requestDTO.getModel()));
            return Response.<String>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info("连接成功")
                    .data(reply)
                    .build();
        } catch (AppException e) {
            return Response.<String>builder()
                    .code(e.getCode())
                    .info(info(e, ResponseCode.UN_ERROR))
                    .build();
        } catch (Exception e) {
            log.warn("连通性测试失败: {}", e.getMessage());
            return Response.<String>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("连接失败，请检查地址、Key 与模型名称")
                    .build();
        }
    }

    /**
     * 把请求里的 base64 图片组装成多模态消息，并做数量/体积/格式校验。
     */
    private ChatCommandEntity buildImageCommand(ChatRequestDTO requestDTO, String sessionId) {
        List<ChatImageDTO> images = requestDTO.getImages();
        if (images.size() > MAX_IMAGE_COUNT) {
            throw new AppException(ResponseCode.E0004.getCode(), "一次最多上传 " + MAX_IMAGE_COUNT + " 张图片");
        }

        List<ChatCommandEntity.Content.InlineData> inlineDatas = new ArrayList<>();
        for (ChatImageDTO image : images) {
            if (null == image || StringUtils.isBlank(image.getData())) {
                continue;
            }
            String mimeType = StringUtils.defaultIfBlank(image.getMimeType(), "image/png").toLowerCase();
            if (!ALLOWED_IMAGE_TYPES.contains(mimeType)) {
                throw new AppException(ResponseCode.E0004.getCode(), "不支持的图片格式：" + mimeType);
            }

            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(image.getData());
            } catch (IllegalArgumentException e) {
                throw new AppException(ResponseCode.E0004.getCode(), "图片数据解析失败，请重新上传");
            }
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new AppException(ResponseCode.E0004.getCode(), "单张图片不能超过 2MB，请压缩后重试");
            }

            inlineDatas.add(new ChatCommandEntity.Content.InlineData(bytes, mimeType));
        }

        if (inlineDatas.isEmpty()) {
            throw new AppException(ResponseCode.E0004.getCode(), "图片内容为空，请重新上传");
        }

        List<ChatCommandEntity.Content.Text> texts = new ArrayList<>();
        texts.add(new ChatCommandEntity.Content.Text(StringUtils.defaultIfBlank(requestDTO.getMessage(), "请描述这张图片")));

        return ChatCommandEntity.builder()
                .agentId(requestDTO.getAgentId())
                .userId(requestDTO.getUserId())
                .sessionId(sessionId)
                .texts(texts)
                .inlineDatas(inlineDatas)
                .build();
    }

    /**
     * 前端传空字符串时视为「未配置」，回落到服务端 yml 里的默认模型。
     */
    private UserApiConfigVO toApiConfig(String baseUrl, String apiKey, String model) {
        if (StringUtils.isBlank(apiKey) || StringUtils.isBlank(baseUrl)) {
            return null;
        }
        return UserApiConfigVO.builder()
                .baseUrl(baseUrl.trim())
                .apiKey(apiKey.trim())
                .model(StringUtils.trimToNull(model))
                .build();
    }

    private String info(AppException e, ResponseCode fallback) {
        return StringUtils.defaultIfBlank(e.getInfo(), fallback.getInfo());
    }

}
