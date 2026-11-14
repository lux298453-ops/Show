package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 生成角色与背景插画持久化实体类（存储至数据库 MEDIUMBLOB，防止 Render 容器休眠重启丢图）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("artwork_asset")
public class ArtworkAsset {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目 ID */
    private Long projectId;

    /** 图片 SHA-256 哈希标识 */
    private String digest;

    /** 角色/素材 key */
    private String artworkKey;

    /** 素材名称 */
    private String name;

    /** 生图 prompt */
    private String prompt;

    /** 素材类型：character, background, illustration, product */
    private String kind;

    /** PNG 二进制图片数据 */
    private byte[] imageData;

    /** 完整 JSON 元数据 */
    private String jsonData;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
