package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 认识的人：登录过的人，或从飞书通讯录查到的人。用来按飞书编号显示名字。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("app_user")
public class AppUser {
    @TableId(type = IdType.INPUT)
    private String userId;
    private String userName;
    private String userAvatar;
    private String unionId;
    /** 从飞书通讯录查到但还没登录过时为空 */
    private LocalDateTime lastLoginAt;
    private LocalDateTime updatedAt;
}
