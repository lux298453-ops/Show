package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("annotation")
public class Annotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pageId;
    private Long elementId;
    /** 说明自己的标题，不修改关联元素名称。 */
    private String title;
    /** user 表示人工添加或编辑，重新分析时保留。 */
    private String source;
    private String text;
    private Double positionX;
    private Double positionY;
    /** 标注框位置（画布 px，null=自动布局） */
    private Double boxX;
    private Double boxY;
    /** 引线元素端锚点（线框逻辑坐标，null=自动跟随元素） */
    private Double anchorX;
    private Double anchorY;
    /** 引线竖折线 X（画布 px，null=自动） */
    private Double elbowX;
    /** 排序权重（越小越靠前） */
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
