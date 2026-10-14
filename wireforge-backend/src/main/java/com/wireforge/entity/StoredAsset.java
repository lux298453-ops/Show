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

/**
 * 用户上传的素材资产实体类（持久化存储至数据库 MEDIUMBLOB）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_asset")
public class StoredAsset {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 素材唯一标识，形如 u-1728900000000-icon-name */
    private String assetId;

    /** 素材分类：avatars / products / icons / backgrounds / effects / uploads */
    private String category;

    /** 素材显示名称 */
    private String name;

    /** 原始文件名 */
    private String fileName;

    /** MIME 类型，如 image/png, image/svg+xml */
    private String contentType;

    /** 字节大小 */
    private Long fileSize;

    /** 二进制数据 */
    private byte[] data;

    /** 上传人用户ID */
    private String uploaderId;

    /** 上传人姓名 */
    private String uploaderName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
