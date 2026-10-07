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
 * 核收流水到门账对象 t_ctld_flow_arrive（同一批次码每到一回落一笔，只追加）
 *
 * @author fuce
 * @date 2026-10-07
 */
@TableName("t_ctld_flow_arrive")
@ApiModel(value = "TCtldFlowArrive", description = "核收流水到门账")
public class TCtldFlowArrive implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 到门的批次码 */
    @TableField("batch_no")
    @ApiModelProperty(value = "到门的批次码")
    private String batchNo;

    /** 第几回到门（本批次码内时序） */
    @TableField("arrive_seq")
    @ApiModelProperty(value = "第几回到门（本批次码内时序）")
    private Integer arriveSeq;

    /** 这一回的下场 LANDED头一回落地/REPLAYED照面重递 */
    @TableField("result_kind")
    @ApiModelProperty(value = "这一回的下场 LANDED头一回落地/REPLAYED照面重递")
    private String resultKind;

    /** 这一回的记要 */
    @TableField("note")
    @ApiModelProperty(value = "这一回的记要")
    private String note;

    /** 删除标记 0正常 1删除（到门流水只追加，不置1） */
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

    public Integer getArriveSeq() {
        return arriveSeq;
    }

    public void setArriveSeq(Integer arriveSeq) {
        this.arriveSeq = arriveSeq;
    }

    public String getResultKind() {
        return resultKind;
    }

    public void setResultKind(String resultKind) {
        this.resultKind = resultKind;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
}
