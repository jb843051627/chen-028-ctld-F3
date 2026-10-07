package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 土壤-农产品协同检测流水拨头对象 t_ctld_flow_batch（一拨一行，批次码唯一）
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_flow_batch")
@ApiModel(value = "TCtldFlowBatch", description = "土壤-农产品协同检测流水拨头")
public class TCtldFlowBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 协同检测流水批次码（同一批次码走两回只认头一拨） */
    @TableField("batch_no")
    @ApiModelProperty(value = "协同检测流水批次码（同一批次码走两回只认头一拨）")
    private String batchNo;

    /** 拨头抬头那一格写定的上限（原样留存，空/负/离谱都照实留） */
    @TableField("limit_declared")
    @ApiModelProperty(value = "拨头抬头那一格写定的上限（原样留存）")
    private Integer limitDeclared;

    /** 认定上限（抬头空着、负数、大得离谱一律按 300 认） */
    @TableField("limit_resolved")
    @ApiModelProperty(value = "认定上限（抬头空着、负数、大得离谱一律按 300 认）")
    private Integer limitResolved;

    /** 本拨认的切法 DELIMITED按分隔符/FIXED_WIDTH按定宽，一拨之内认一种 */
    @TableField("parse_mode")
    @ApiModelProperty(value = "本拨认的切法 DELIMITED按分隔符/FIXED_WIDTH按定宽")
    private String parseMode;

    /** 来路（县农技中心直报／镇级补报两条来路） */
    @TableField("put_kind")
    @ApiModelProperty(value = "来路（县农技中心直报／镇级补报两条来路）")
    private String putKind;

    /** 几行进门（=收下+退回，与逐行去向同一次点算出来） */
    @TableField("in_count")
    @ApiModelProperty(value = "几行进门（=收下+退回）")
    private Integer inCount;

    /** 几行收下 */
    @TableField("accept_count")
    @ApiModelProperty(value = "几行收下")
    private Integer acceptCount;

    /** 几行退回 */
    @TableField("reject_count")
    @ApiModelProperty(value = "几行退回")
    private Integer rejectCount;

    /** 几行待核（未勾，单列一份待核清单，不进三栏） */
    @TableField("pending_count")
    @ApiModelProperty(value = "几行待核（未勾，单列一份待核清单）")
    private Integer pendingCount;

    /** 由哪一拨到顶溢出另起（头一拨为 NULL） */
    @TableField("overflow_from")
    @ApiModelProperty(value = "由哪一拨到顶溢出另起（头一拨为 NULL）")
    private String overflowFrom;

    /** 回执那句原话（空拨只落「空拨」两个字，不收讫那句不出） */
    @TableField("receipt_msg")
    @ApiModelProperty(value = "回执那句原话（空拨只落「空拨」两个字）")
    private String receiptMsg;

    /** 拨的情形 0已收讫 1空拨 */
    @TableField("status")
    @ApiModelProperty(value = "拨的情形 0已收讫 1空拨")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getLimitDeclared() {
        return limitDeclared;
    }

    public void setLimitDeclared(Integer limitDeclared) {
        this.limitDeclared = limitDeclared;
    }

    public Integer getLimitResolved() {
        return limitResolved;
    }

    public void setLimitResolved(Integer limitResolved) {
        this.limitResolved = limitResolved;
    }

    public String getParseMode() {
        return parseMode;
    }

    public void setParseMode(String parseMode) {
        this.parseMode = parseMode;
    }

    public String getPutKind() {
        return putKind;
    }

    public void setPutKind(String putKind) {
        this.putKind = putKind;
    }

    public Integer getInCount() {
        return inCount;
    }

    public void setInCount(Integer inCount) {
        this.inCount = inCount;
    }

    public Integer getAcceptCount() {
        return acceptCount;
    }

    public void setAcceptCount(Integer acceptCount) {
        this.acceptCount = acceptCount;
    }

    public Integer getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(Integer rejectCount) {
        this.rejectCount = rejectCount;
    }

    public Integer getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(Integer pendingCount) {
        this.pendingCount = pendingCount;
    }

    public String getOverflowFrom() {
        return overflowFrom;
    }

    public void setOverflowFrom(String overflowFrom) {
        this.overflowFrom = overflowFrom;
    }

    public String getReceiptMsg() {
        return receiptMsg;
    }

    public void setReceiptMsg(String receiptMsg) {
        this.receiptMsg = receiptMsg;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
