package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("interaction_exclusion")
public class InteractionExclusion {
    @TableId(type = IdType.AUTO) private Long id;
    private Long projectId;
    private Long pageId;
    private Long elementId;
    private String elementKey;
    private String elementLabel;
    private String scope;
    private String relationKey;
    private String reason;
    private String origin;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
