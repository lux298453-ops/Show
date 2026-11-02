package com.wireforge.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAdjustResponse {
    private Long pageId;
    private String pageName;
    private String summary;
    private List<String> diffList;
    private String modifiedHtml;
}
