package com.omnimerchant.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String externalProductId;
    private String handle;
    private String title;
    private String subtitle;
    private String description;
    private String descriptionPlain;
    private String vendor;
    private String brand;
    private String productType;
    private String categoryL1;
    private String categoryL2;
    private String categoryL3;
    private String categories;
    private String tags;
    private String defaultSku;
    private String barcode;
    private String variants;
    private Integer variantCount;
    private String optionsSchema;
    private String currency;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private BigDecimal costPerItem;
    private Integer totalStock;
    private String stockStatus;
    private Integer weightGrams;
    private String dimensions;
    private Integer requiresShipping;
    private Integer isTaxable;
    private String featuredImageUrl;
    private String images;
    private String videos;
    private String language;
    private String translations;
    private String seoTitle;
    private String seoDescription;
    private String keywords;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private String reviewSummary;
    private Integer vectorSynced;
    private Integer vectorChunkCount;
    private LocalDateTime vectorSyncedAt;
    private String contentHash;
    private Integer status;
    private LocalDateTime publishedAt;
    private LocalDateTime syncedAt;
    private String extAttr;

    @TableLogic
    private Integer isDeleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
