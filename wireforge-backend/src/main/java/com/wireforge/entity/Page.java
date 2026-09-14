package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("page")
public class Page {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private String backgroundImage;
    private Integer canvasWidth;
    private Integer canvasHeight;
    /** 区块在无限画布上的位置（null=自动布局） */
    private Double canvasX;
    private Double canvasY;
    private Integer sortOrder;
    private Integer analyzed;
    /** Stitch 式整页直出：AI 生成的完整 HTML/CSS 页面（高保真视图，null=未生成） */
    private String htmlContent;
    private LocalDateTime createdAt;
}
