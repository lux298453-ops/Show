package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String userId;
    private String userName;
    private String userAvatar;
    /** PAGE_CREATE/PAGE_EDIT/PAGE_DELETE/ELEMENT_ADD/INTERACTION_ADD/COMMENT_ADD等 */
    private String actionType;
    /** 具体中文变更说明 */
    private String actionDesc;
    /** 涉及的画板或对象名称 */
    private String targetName;
    private String ipAddress;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
