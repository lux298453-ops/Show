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

/** 登录账号自己的组件。换项目、换电脑，只要是同一个账号就能看到。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_component")
public class UserComponent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String name;
    private String html;
    private Integer width;
    private Integer height;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
