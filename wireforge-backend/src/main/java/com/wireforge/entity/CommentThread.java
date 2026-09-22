package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("comment_thread")
public class CommentThread {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long pageId;
    private Double x;
    private Double y;
    private String author;
    private Boolean resolved;
    private LocalDateTime createdAt;

    /** 关联回复列表（非数据库字段） */
    @TableField(exist = false)
    private List<CommentReply> replies;

    /** 所在画板/页面名称（非数据库字段，方便列表展示） */
    @TableField(exist = false)
    private String pageName;
}
