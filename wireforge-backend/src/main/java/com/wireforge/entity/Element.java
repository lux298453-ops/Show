package com.wireforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("element")
public class Element {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pageId;
    private String type;
    private String label;
    /** 素材库 ID（avatar-03 / product-01 / icon-home / bg-01 / effect-glow 等），识别到素材元素时由 AI 输出 */
    @TableField("asset_id")
    private String assetId;
    private Double positionX;
    private Double positionY;
    private Double width;
    private Double height;
    private String style;
    /** 同一页里同一次点击的共用编号。空表示不属于任何可点击组。 */
    private String groupKey;
    /** anchor=这一组的代表；member=组内其余部分；空=不属于可点击组。 */
    private String groupRole;
    private String createdBy;
    private LocalDateTime createdAt;
}
