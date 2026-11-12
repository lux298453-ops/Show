package com.wireforge.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiElementAdjustResponse {
    /** 操作类型: REPLACE (替换修改) 或 DELETE (彻底删除) */
    private String action;
    /** 调整的元素 ID */
    private String elementId;
    /** 简明改动摘要 */
    private String summary;
    /** 修改后的该元素外层 HTML (若 action 为 DELETE 则为空字符串) */
    private String modifiedSnippet;
    private boolean changed;
    private java.util.List<AiGeneratedImage> generatedImages;
}
