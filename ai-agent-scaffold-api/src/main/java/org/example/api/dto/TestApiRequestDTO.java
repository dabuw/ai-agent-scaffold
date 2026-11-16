package org.example.api.dto;

import lombok.Data;

/**
 * 连通性测试请求：用户填写自己的模型配置后先探一次。
 *
 * @author dabuw
 * @date 16/11/26
 */
@Data
public class TestApiRequestDTO {

    private String apiBaseUrl;

    private String apiKey;

    private String model;

}
