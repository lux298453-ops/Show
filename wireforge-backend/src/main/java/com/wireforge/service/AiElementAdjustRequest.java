package com.wireforge.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiElementAdjustRequest {
    /** 选中的元素唯一标识，如 data-wf-element-id、data-wf-uid 或 class */
    private String elementId;
    /** 元素标签类型，如 button, div, span, svg */
    private String elementTag;
    /** 元素展示文案或标题标签 */
    private String elementLabel;
    /** 单个元素完整的 outerHTML 代码片段 */
    private String elementHtml;
    /** 用户的微调指令（如：换成橙色渐变、加粗、删除等） */
    private String instruction;
}
