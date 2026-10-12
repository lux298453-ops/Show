package com.wireforge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录的人是谁。能做什么由 AuthService 按管理员名单和项目成员名单另算。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser {
    private String userId;
    private String userName;
    private String userAvatar;
    private String email;
    private String openId;
    private String unionId;
}
