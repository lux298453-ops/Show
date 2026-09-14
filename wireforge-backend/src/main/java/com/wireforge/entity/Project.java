package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String coverImage;
    /** App Map：全局共享组件定义（底部 Tab 栏等）JSON，由 AppMapService 确定性构建 */
    private String appMap;
    private LocalDateTime createdAt;
}
