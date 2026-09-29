package com.wireforge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectAnalysisStatus {
    private Long projectId;
    /** 是否正在分析生成中 */
    private boolean analyzing;
    /** 当前正在分析第几页 (1-based) */
    private int current;
    /** 总待分析页面数 */
    private int total;
    /** 当前正在分析的页面名称 */
    private String currentPageName;
    /** 当前阶段描述 (例如: "正在分析「扭蛋抽奖页」(1/39)", "正在执行 AI 拓扑智能连线...") */
    private String step;
    /** 已成功生成页面数 */
    private int okCount;
    /** 失败页面数 */
    private int failCount;
    /** 意外错误描述（若中断或异常则记录） */
    private String lastError;
    /** 本轮分析是否已完成/结束 */
    private boolean finished;
    /** 任务开始时间戳 */
    private Long startTime;
    /** 最近状态更新时间戳 */
    private Long updateTime;
}
