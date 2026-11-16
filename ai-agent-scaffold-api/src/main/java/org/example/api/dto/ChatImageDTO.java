package org.example.api.dto;

import lombok.Data;

/**
 * 对话中的图片：前端压缩后转 base64 内联传输，后端不落盘。
 *
 * @author dabuw
 * @date 16/11/26
 */
@Data
public class ChatImageDTO {

    /** 图片 MIME 类型，例如 image/png */
    private String mimeType;

    /** base64 编码的图片内容（不含 data: 前缀） */
    private String data;

}
