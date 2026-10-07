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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 土壤-农产品协同检测流水核收行对象 t_ctld_flow_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_ctld_flow_row")
@ApiModel(value = "TCtldFlowRow", description = "土壤-农产品协同检测流水核收行")
public class TCtldFlowRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 协同检测流水批次码 */
    @TableField("batch_no")
    @ApiModelProperty(value = "协同检测流水批次码")
    private String batchNo;

    /** 原报行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原报行次")
    private Integer rowNo;

    /** 被对上的地块码 */
    @TableField("item_code")
    @ApiModelProperty(value = "被对上的地块码")
    private String itemCode;

    /** 本行送检样品数 */
    @TableField("qty")
    @ApiModelProperty(value = "本行送检样品数")
    private BigDecimal qty;

    /** 本行配对达标数 */
    @TableField("chk_num")
    @ApiModelProperty(value = "本行配对达标数")
    private BigDecimal chkNum;

    /** 土壤镉实测数（报文带入，与当地那条告警线比对） */
    @TableField("cd_value")
    @ApiModelProperty(value = "土壤镉实测数（报文带入，与当地那条告警线比对）")
    private BigDecimal cdValue;

    /** 地块题名（一律以底册带出的那一份作准） */
    @TableField("plot_title")
    @ApiModelProperty(value = "地块题名（一律以底册带出的那一份作准）")
    private String plotTitle;

    /** 分区代号（一律以底册带出的那一份作准） */
    @TableField("zone_no")
    @ApiModelProperty(value = "分区代号（一律以底册带出的那一份作准）")
    private String zoneNo;

    /** 待核标注 PARSE报文切不开/ENCODING编码混杂/INCOMPLETE两数未同到/NO_PLOT底册查无此码 */
    @TableField("flag_kind")
    @ApiModelProperty(value = "待核标注 PARSE报文切不开/ENCODING编码混杂/INCOMPLETE两数未同到/NO_PLOT底册查无此码")
    private String flagKind;

    /** 退回理由（为哪一条退，逐行追溯） */
    @TableField("reject_reason")
    @ApiModelProperty(value = "退回理由（为哪一条退，逐行追溯）")
    private String rejectReason;

    /** 原始报文行（回核对照用，只用来比对不作行面） */
    @TableField("msg_raw")
    @ApiModelProperty(value = "原始报文行（回核对照用，只用来比对不作行面）")
    private String msgRaw;

    /** 题名或分区与底册比对不合 0合 1不合（不合的行回执单列一份） */
    @TableField("mismatch")
    @ApiModelProperty(value = "题名或分区与底册比对不合 0合 1不合（不合的行回执单列一份）")
    private Integer mismatch;

    /** 来路（县农技中心直报／镇级补报两条来路） */
    @TableField("put_kind")
    @ApiModelProperty(value = "来路（县农技中心直报／镇级补报两条来路）")
    private String putKind;

    /** 行落地情形 0未勾 1已收下 2已退回 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0未勾 1已收下 2已退回")
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

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getChkNum() {
        return chkNum;
    }

    public void setChkNum(BigDecimal chkNum) {
        this.chkNum = chkNum;
    }

    public BigDecimal getCdValue() {
        return cdValue;
    }

    public void setCdValue(BigDecimal cdValue) {
        this.cdValue = cdValue;
    }

    public String getPlotTitle() {
        return plotTitle;
    }

    public void setPlotTitle(String plotTitle) {
        this.plotTitle = plotTitle;
    }

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public String getFlagKind() {
        return flagKind;
    }

    public void setFlagKind(String flagKind) {
        this.flagKind = flagKind;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getMsgRaw() {
        return msgRaw;
    }

    public void setMsgRaw(String msgRaw) {
        this.msgRaw = msgRaw;
    }

    public Integer getMismatch() {
        return mismatch;
    }

    public void setMismatch(Integer mismatch) {
        this.mismatch = mismatch;
    }

    public String getPutKind() {
        return putKind;
    }

    public void setPutKind(String putKind) {
        this.putKind = putKind;
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
