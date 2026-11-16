package org.example.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 用户自带的模型 API 配置；只在请求生命周期内使用，不落库、不写日志。
 *
 * @author dabuw
 * @date 16/11/26
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserApiConfigVO {

    /** 例如 https://api.deepseek.com */
    private String baseUrl;

    /** 用户自己的 API Key */
    private String apiKey;

    /** 模型名称，例如 deepseek-chat */
    private String model;

    /** 是否可用：baseUrl 与 apiKey 都必须填写 */
    public boolean usable() {
        return StringUtils.isNotBlank(baseUrl) && StringUtils.isNotBlank(apiKey);
    }

    /** 缓存键：用哈希避免把明文 key 作为 map 的 key 长期驻留 */
    public String fingerprint(String agentId) {
        String raw = StringUtils.defaultString(baseUrl) + "|" + StringUtils.defaultString(model) + "|" + StringUtils.defaultString(apiKey);
        return agentId + "@" + Integer.toHexString(raw.hashCode());
    }
}
