package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 登录凭证。存库是为了后端重启后已登录的人不掉线。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_session")
public class AuthSession {
    @TableId(type = IdType.INPUT)
    private String token;
    private String userId;
    private String userName;
    private String userAvatar;
    private String openId;
    private String unionId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
