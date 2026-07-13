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
@TableName("order_info")
public class OrderInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String externalOrderId;
    private String externalOrderNumber;
    private String platform;
    private Long customerId;
    private String externalCustomerId;
    private String customerEmail;
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private String shippingCountry;
    private String shippingState;
    private String shippingZip;
    private String billingAddress;
    private String orderStatus;
    private String paymentStatus;
    private String fulfillmentStatus;
    private String currency;
    private BigDecimal subtotalAmount;
    private BigDecimal shippingAmount;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal refundedAmount;
    private String orderItems;
    private Integer itemCount;
    private Integer totalQuantity;
    private String trackingNumber;
    private String trackingNumbers;
    private String trackingCarrier;
    private String trackingUrl;
    private String trackingStatus;
    private String trackingHistory;
    private LocalDateTime trackingUpdatedAt;
    private LocalDateTime estimatedDeliveryAt;
    private LocalDateTime actualDeliveryAt;
    private String tags;
    private String discountCodes;
    private String note;
    private LocalDateTime placedAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime syncedAt;
    private String syncSource;
    private Integer syncVersion;
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
