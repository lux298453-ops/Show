package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("interaction")
public class Interaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long elementId;
    private String triggerType;
    private String actionType;
    private Long targetPageId;
    private String params;
    /** ai=识别时写下，autowire=自动布线，user=人手动保存。空视为可被自动规则整理的旧数据。 */
    private String source;
}
