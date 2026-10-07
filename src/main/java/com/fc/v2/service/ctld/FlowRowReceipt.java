package com.fc.v2.service.ctld;

import java.util.ArrayList;
import java.util.List;

/**
 * 核收回执：四件事（点行数、看数据、定去处、出回执）一口气走完后，唯一作凭的那一份。
 * 末了三栏（几行进门、几行收下、几行退回）与逐行去向在同一次点算里出来，不许另起一回去算；
 * 后两栏相加须等于头一栏。页面那一份只是复述，两处不同源就谈不上对得住。
 *
 * @author fuce
 * @date 2026-10-07
 */
public class FlowRowReceipt {

    private String batchNo;
    /** 拨头抬头那一格写定的上限（原样，空/负/离谱都照实留） */
    private Integer limitDeclared;
    /** 认定上限（空着、负数、大得离谱一律按 300 认） */
    private int limitResolved;
    private String parseMode;
    private String putKind;

    /** 一行也没收着的空拨：回执只落「空拨」两个字，不收讫那句不出 */
    private boolean emptyBatch;
    /** 同一批次码第二回到门：照面，照头一拨的回执回话 */
    private boolean replayed;

    /** 几行进门（=收下+退回，待核行不进三栏） */
    private int inCount;
    /** 几行收下 */
    private int acceptCount;
    /** 几行退回 */
    private int rejectCount;
    /** 几行待核（未勾，单列一份，由回核的人逐行定去向） */
    private int pendingCount;

    /** 全量逐行去向（收下、退回、待核都在，抽任意一行回核须与三栏相互印证） */
    private List<FlowRowEngine.Row> rows = new ArrayList<>();
    /** 比对不合单列：题名或分区与底册对不上的行（行面一律以底册带出的作准） */
    private List<FlowRowEngine.Row> mismatches = new ArrayList<>();
    /** 待核清单：去处空悬在未勾那一地的行，系统不自作主张替它收 */
    private List<FlowRowEngine.Row> pendings = new ArrayList<>();
    /** 到顶装不下、另起一拨接住的行（未决，由服务层另起新拨接走） */
    private List<FlowRowEngine.Row> overflow = new ArrayList<>();
    /** 溢出另起的各拨批次码（服务层落完填回） */
    private List<String> overflowBatchNos = new ArrayList<>();

    /** 回执那句原话（空拨只落「空拨」两个字） */
    private String msg;

    /** 三栏自洽：收下+退回==进门；对不上就是两处不同源，谈不上对得住 */
    public boolean balanced() {
        return acceptCount + rejectCount == inCount;
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

    public int getLimitResolved() {
        return limitResolved;
    }

    public void setLimitResolved(int limitResolved) {
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

    public boolean isEmptyBatch() {
        return emptyBatch;
    }

    public void setEmptyBatch(boolean emptyBatch) {
        this.emptyBatch = emptyBatch;
    }

    public boolean isReplayed() {
        return replayed;
    }

    public void setReplayed(boolean replayed) {
        this.replayed = replayed;
    }

    public int getInCount() {
        return inCount;
    }

    public void setInCount(int inCount) {
        this.inCount = inCount;
    }

    public int getAcceptCount() {
        return acceptCount;
    }

    public void setAcceptCount(int acceptCount) {
        this.acceptCount = acceptCount;
    }

    public int getRejectCount() {
        return rejectCount;
    }

    public void setRejectCount(int rejectCount) {
        this.rejectCount = rejectCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public List<FlowRowEngine.Row> getRows() {
        return rows;
    }

    public void setRows(List<FlowRowEngine.Row> rows) {
        this.rows = rows;
    }

    public List<FlowRowEngine.Row> getMismatches() {
        return mismatches;
    }

    public void setMismatches(List<FlowRowEngine.Row> mismatches) {
        this.mismatches = mismatches;
    }

    public List<FlowRowEngine.Row> getPendings() {
        return pendings;
    }

    public void setPendings(List<FlowRowEngine.Row> pendings) {
        this.pendings = pendings;
    }

    public List<FlowRowEngine.Row> getOverflow() {
        return overflow;
    }

    public void setOverflow(List<FlowRowEngine.Row> overflow) {
        this.overflow = overflow;
    }

    public List<String> getOverflowBatchNos() {
        return overflowBatchNos;
    }

    public void setOverflowBatchNos(List<String> overflowBatchNos) {
        this.overflowBatchNos = overflowBatchNos;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
