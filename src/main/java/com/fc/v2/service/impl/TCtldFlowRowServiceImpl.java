package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.ctld.FlowRowException;
import com.fc.v2.mapper.auto.TCtldFlowArriveMapper;
import com.fc.v2.mapper.auto.TCtldFlowBatchMapper;
import com.fc.v2.mapper.auto.TCtldFlowRowMapper;
import com.fc.v2.mapper.auto.TCtldLimitLineMapper;
import com.fc.v2.mapper.auto.TCtldPlotMapper;
import com.fc.v2.model.auto.TCtldFlowArrive;
import com.fc.v2.model.auto.TCtldFlowBatch;
import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.model.auto.TCtldLimitLine;
import com.fc.v2.model.auto.TCtldPlot;
import com.fc.v2.service.ITCtldFlowRowService;
import com.fc.v2.service.ctld.FlowRowEngine;
import com.fc.v2.service.ctld.FlowRowReceipt;
import com.fc.v2.shiro.util.ShiroUtils;

/**
 * 土壤-农产品协同检测流水核收行 Service业务层处理（batch-process 形状：整批提交）。
 * 口径全部出自 {@link FlowRowEngine}：行去处、重到批次码、整拨成不成、拨头三个数，四问只此一处；
 * 收拨与流水联动——拨头、各行、到门账在同一事务里落，要么一起成、要么一起回。
 * 本类不开单行增删改的口子：行一旦落定不改不挪，批次码一旦落定不另立一拨。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TCtldFlowRowServiceImpl implements ITCtldFlowRowService {

    /** 拨的情形 0已收讫 1空拨 */
    private static final int BATCH_RECEIVED = 0;
    private static final int BATCH_EMPTY = 1;

    /** 到门账下场：头一回落地／照面重递 */
    private static final String ARRIVE_LANDED = "LANDED";
    private static final String ARRIVE_REPLAYED = "REPLAYED";

    /** 原始报文行落库长度上限（t_ctld_flow_row.msg_raw varchar(500)） */
    private static final int MSG_RAW_MAX = 500;

    @javax.annotation.Resource
    private TCtldFlowRowMapper ctldFlowRowMapper;
    @javax.annotation.Resource
    private TCtldFlowBatchMapper flowBatchMapper;
    @javax.annotation.Resource
    private TCtldFlowArriveMapper flowArriveMapper;
    @javax.annotation.Resource
    private TCtldPlotMapper plotMapper;
    @javax.annotation.Resource
    private TCtldLimitLineMapper limitLineMapper;

    private final TransactionTemplate transactionTemplate;

    public TCtldFlowRowServiceImpl(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 门槛外部取数：底册对码、当地告警线，两处都只在此给引擎 */
    private final FlowRowEngine.GateContext gates = new FlowRowEngine.GateContext() {
        @Override
        public FlowRowEngine.PlotRef plotOf(String plotCode) {
            return plotRefOf(plotCode);
        }

        @Override
        public java.math.BigDecimal alarmLine() {
            return alarmLineNow();
        }
    };

    /** 底册对码（包私有留缝：库层取数只此一处，演练时按码顶替） */
    FlowRowEngine.PlotRef plotRefOf(String plotCode) {
        if (plotCode == null) {
            return null;
        }
        QueryWrapper<TCtldPlot> q = new QueryWrapper<>();
        q.eq("plot_no", plotCode).eq("del_flag", 0).orderByDesc("id").last("limit 1");
        TCtldPlot p = plotMapper.selectOne(q);
        return p == null ? null : new FlowRowEngine.PlotRef(p.getPlotNo(), p.getContent(), p.getZoneNo());
    }

    /** 当地那条告警线：在位、已启用、未交班，次序大的先说话（包私有留缝，理由同上） */
    java.math.BigDecimal alarmLineNow() {
        Date now = new Date();
        QueryWrapper<TCtldLimitLine> q = new QueryWrapper<>();
        q.eq("status", 0).eq("del_flag", 0)
                .and(w -> w.isNull("eff_start").or().le("eff_start", now))
                .and(w -> w.isNull("eff_end").or().gt("eff_end", now))
                .orderByDesc("priority").orderByDesc("id").last("limit 1");
        TCtldLimitLine line = limitLineMapper.selectOne(q);
        return line == null ? null : line.getTh2Max();
    }

    @Override
    public TCtldFlowRow selectTCtldFlowRowById(Long id) {
        return this.ctldFlowRowMapper.selectById(id);
    }

    @Override
    public List<TCtldFlowRow> selectTCtldFlowRowList(QueryWrapper<TCtldFlowRow> queryWrapper) {
        return this.ctldFlowRowMapper.selectList(queryWrapper);
    }

    // ============================ 整拨核收 ============================

    @Override
    public FlowRowReceipt intake(FlowRowEngine.BatchCmd cmd) {
        String batchNo = cmd == null ? null : cmd.getBatchNo();
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new FlowRowException("拨头批次码空着，拨立不起来；整拨留在门外");
        }
        // 同一批次码重到门口：第二遍到只能照面，不动行、不另立一拨
        FlowRowReceipt existed = replayIfLanded(batchNo.trim());
        if (existed != null) {
            return existed;
        }
        // 引擎一口气走完四件事，回执只此一份；落库照这份落，不另起一回去算
        FlowRowReceipt receipt = FlowRowEngine.intake(cmd, gates);
        return landWithOverflow(receipt, cmd.getDeclaredLimit(), cmd.getPutKind());
    }

    @Override
    public int submitBatch(String batchNo, List<TCtldFlowRow> rows) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            // 批次码只认拨头那一格，不许去够集合里的头一条
            throw new FlowRowException("拨头批次码空着，拨立不起来；整拨留在门外");
        }
        String no = batchNo.trim();
        FlowRowReceipt existed = replayIfLanded(no);
        if (existed != null) {
            return existed.getAcceptCount();
        }
        List<FlowRowEngine.Row> engineRows = new ArrayList<>();
        if (rows != null) {
            for (TCtldFlowRow t : rows) {
                engineRows.add(FlowRowEngine.rowOf(t.getItemCode(), t.getQty(), t.getChkNum(),
                        t.getCdValue(), t.getRowNo(), t.getPutKind()));
            }
        }
        FlowRowReceipt receipt = FlowRowEngine.intakeRows(engineRows, null, gates);
        receipt.setBatchNo(no);
        receipt.setLimitDeclared(null);
        receipt = landWithOverflow(receipt, null, null);
        return receipt.getAcceptCount();
    }

    // ============================ 回查：退回明细 / 待核清单 / 回执复述 ============================

    @Override
    public List<TCtldFlowRow> listErrors(String batchNo) {
        return this.ctldFlowRowMapper.selectList(new QueryWrapper<TCtldFlowRow>()
                .eq("batch_no", batchNo).eq("status", FlowRowEngine.STATUS_REJECTED)
                .eq("del_flag", 0).orderByAsc("row_no"));
    }

    @Override
    public List<TCtldFlowRow> listPending(String batchNo) {
        QueryWrapper<TCtldFlowRow> q = new QueryWrapper<>();
        q.eq("status", FlowRowEngine.STATUS_UNCHECKED).eq("del_flag", 0);
        if (batchNo != null && !batchNo.trim().isEmpty()) {
            q.eq("batch_no", batchNo.trim());
        }
        q.orderByAsc("batch_no", "row_no");
        return this.ctldFlowRowMapper.selectList(q);
    }

    @Override
    public FlowRowReceipt receiptOf(String batchNo) {
        TCtldFlowBatch header = findBatch(batchNo);
        if (header == null) {
            throw new FlowRowException("查无此拨「" + batchNo + "」，无回执可复述");
        }
        return receiptOf(header);
    }

    // ============================ 内部：落库、照面、复述 ============================

    /** 头一拨已落定就照面：照头一拨的回执回话，到门账添一笔重递，行与拨头一概不动 */
    private FlowRowReceipt replayIfLanded(String batchNo) {
        TCtldFlowBatch header = findBatch(batchNo);
        if (header == null) {
            return null;
        }
        appendArrive(batchNo, ARRIVE_REPLAYED, "同一批次码重到门口，照面不再落行");
        FlowRowReceipt receipt = receiptOf(header);
        receipt.setReplayed(true);
        receipt.setMsg("照面（同一批次码重到门口，只认头一拨）：" + header.getReceiptMsg());
        return receipt;
    }

    /**
     * 收拨与流水联动：拨头、各行、到门账同一事务落；到顶溢出的另起一拨接住，不许硬塞。
     * 并发同批次码撞唯一拨头：整笔回滚，照头一拨回话（幂等）。
     */
    private FlowRowReceipt landWithOverflow(FlowRowReceipt receipt, Integer declaredLimit, String putKind) {
        try {
            transactionTemplate.execute(status -> {
                persistOne(receipt, null, putKind);
                List<FlowRowEngine.Row> overflow = receipt.getOverflow();
                int n = 0;
                while (!overflow.isEmpty()) {
                    n++;
                    FlowRowReceipt sub = FlowRowEngine.intakeRows(overflow, declaredLimit, gates);
                    sub.setBatchNo(receipt.getBatchNo() + "-溢" + n);
                    sub.setLimitDeclared(declaredLimit);
                    sub.setParseMode(receipt.getParseMode());
                    sub.setPutKind(putKind);
                    persistOne(sub, receipt.getBatchNo(), putKind);
                    receipt.getOverflowBatchNos().add(sub.getBatchNo());
                    overflow = sub.getOverflow();
                }
                return null;
            });
        } catch (DuplicateKeyException dup) {
            // 同一拨并着进两回：头一拨已落定，第二回只照面
            FlowRowReceipt replayed = replayIfLanded(receipt.getBatchNo());
            if (replayed != null) {
                return replayed;
            }
            throw dup;
        }
        return receipt;
    }

    /** 落一拨：拨头一行、各行原样、到门账一笔，全在同一事务里 */
    private void persistOne(FlowRowReceipt receipt, String overflowFrom, String putKind) {
        TCtldFlowBatch header = new TCtldFlowBatch();
        header.setBatchNo(receipt.getBatchNo());
        header.setLimitDeclared(receipt.getLimitDeclared());
        header.setLimitResolved(receipt.getLimitResolved());
        header.setParseMode(receipt.getParseMode());
        header.setPutKind(receipt.getPutKind() != null ? receipt.getPutKind() : putKind);
        header.setInCount(receipt.getInCount());
        header.setAcceptCount(receipt.getAcceptCount());
        header.setRejectCount(receipt.getRejectCount());
        header.setPendingCount(receipt.getPendingCount());
        header.setOverflowFrom(overflowFrom);
        header.setReceiptMsg(receipt.getMsg());
        header.setStatus(receipt.isEmptyBatch() ? BATCH_EMPTY : BATCH_RECEIVED);
        header.setDelFlag(0);
        flowBatchMapper.insert(header);

        for (FlowRowEngine.Row r : receipt.getRows()) {
            // 行去处只有三种写法，第四种落不了库
            if (!FlowRowEngine.isValidStatus(r.getStatus())) {
                throw new FlowRowException("行去处只有未勾/收下/退回三种写法，第 " + r.getRowNo()
                        + " 行的「" + r.getStatus() + "」落不了库");
            }
            TCtldFlowRow row = new TCtldFlowRow();
            row.setBatchNo(receipt.getBatchNo());
            row.setRowNo(r.getRowNo());
            row.setItemCode(r.getPlotCode());
            row.setQty(r.getQty());
            row.setChkNum(r.getChkNum());
            row.setCdValue(r.getCdValue());
            row.setPlotTitle(r.getPlotTitle());
            row.setZoneNo(r.getZoneNo());
            row.setPutKind(r.getPutKind() != null ? r.getPutKind() : putKind);
            row.setStatus(r.getStatus());
            row.setFlagKind(r.getFlagKind());
            row.setRejectReason(r.getRejectReason());
            row.setMsgRaw(truncate(r.getMsgRaw(), MSG_RAW_MAX));
            row.setMismatch(r.isMismatched() ? 1 : 0);
            row.setRemark(remarkOf(r));
            row.setDelFlag(0);
            ctldFlowRowMapper.insert(row);
        }
        appendArrive(receipt.getBatchNo(), ARRIVE_LANDED,
                receipt.isEmptyBatch() ? "空拨到门，只落「空拨」" : "头一回落地：" + receipt.getMsg());
    }

    private String remarkOf(FlowRowEngine.Row r) {
        if (r.getFlagNote() != null) {
            return r.getFlagNote();
        }
        if (r.isMismatched()) {
            return "题名或分区与底册比对不合，行面以底册带出的作准";
        }
        return null;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 到门账只追加：同一批次码每到一回落一笔，两回各留一份流水，并起来期数才对得平 */
    private void appendArrive(String batchNo, String resultKind, String note) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                TCtldFlowArrive a = new TCtldFlowArrive();
                a.setBatchNo(batchNo);
                a.setArriveSeq(countArrive(batchNo) + 1);
                a.setResultKind(resultKind);
                a.setNote(note);
                a.setDelFlag(0);
                a.setCreateBy(safeLoginName());
                a.setCreateTime(new Date());
                flowArriveMapper.insert(a);
                return;
            } catch (DuplicateKeyException ignored) {
                // 并发同到：重数一遍再落，各留各的一份
            }
        }
    }

    /** 到门回数（包私有留缝：库层取数只此一处） */
    int countArrive(String batchNo) {
        QueryWrapper<TCtldFlowArrive> q = new QueryWrapper<>();
        q.eq("batch_no", batchNo);
        return Math.toIntExact(flowArriveMapper.selectCount(q));
    }

    /** 拨头回查（包私有留缝：库层取数只此一处） */
    TCtldFlowBatch findBatch(String batchNo) {
        QueryWrapper<TCtldFlowBatch> q = new QueryWrapper<>();
        q.eq("batch_no", batchNo).eq("del_flag", 0).last("limit 1");
        return flowBatchMapper.selectOne(q);
    }

    /** 本拨各行回查（包私有留缝，理由同上） */
    List<TCtldFlowRow> rowsOfBatch(String batchNo) {
        return ctldFlowRowMapper.selectList(new QueryWrapper<TCtldFlowRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0).orderByAsc("row_no"));
    }

    /** 本拨溢出另起的各拨回查（包私有留缝，理由同上） */
    List<TCtldFlowBatch> overflowSubs(String batchNo) {
        return flowBatchMapper.selectList(new QueryWrapper<TCtldFlowBatch>()
                .eq("overflow_from", batchNo).eq("del_flag", 0).orderByAsc("id"));
    }

    /** 回执复述：三个数从本拨流水照抄，逐行去向从库内原样带出，不另起一回去算 */
    private FlowRowReceipt receiptOf(TCtldFlowBatch header) {
        FlowRowReceipt receipt = new FlowRowReceipt();
        receipt.setBatchNo(header.getBatchNo());
        receipt.setLimitDeclared(header.getLimitDeclared());
        receipt.setLimitResolved(header.getLimitResolved() == null ? 0 : header.getLimitResolved());
        receipt.setParseMode(header.getParseMode());
        receipt.setPutKind(header.getPutKind());
        receipt.setEmptyBatch(header.getStatus() != null && header.getStatus() == BATCH_EMPTY);
        receipt.setInCount(header.getInCount() == null ? 0 : header.getInCount());
        receipt.setAcceptCount(header.getAcceptCount() == null ? 0 : header.getAcceptCount());
        receipt.setRejectCount(header.getRejectCount() == null ? 0 : header.getRejectCount());
        receipt.setPendingCount(header.getPendingCount() == null ? 0 : header.getPendingCount());
        receipt.setMsg(header.getReceiptMsg());

        List<TCtldFlowRow> rows = rowsOfBatch(header.getBatchNo());
        for (TCtldFlowRow t : rows) {
            FlowRowEngine.Row r = FlowRowEngine.rowOf(t.getItemCode(), t.getQty(), t.getChkNum(),
                    t.getCdValue(), null, t.getPutKind());
            r.setRowNo(t.getRowNo());
            r.setStatus(t.getStatus());
            r.setRejectReason(t.getRejectReason());
            r.setFlagKind(t.getFlagKind());
            r.setFlagNote(t.getRemark());
            r.setPlotTitle(t.getPlotTitle());
            r.setZoneNo(t.getZoneNo());
            r.setMsgRaw(t.getMsgRaw());
            r.setMismatched(t.getMismatch() != null && t.getMismatch() == 1);
            receipt.getRows().add(r);
            if (r.getStatus() != null && r.getStatus() == FlowRowEngine.STATUS_UNCHECKED) {
                receipt.getPendings().add(r);
            }
            if (r.isMismatched()) {
                receipt.getMismatches().add(r);
            }
        }

        List<TCtldFlowBatch> subs = overflowSubs(header.getBatchNo());
        for (TCtldFlowBatch s : subs) {
            receipt.getOverflowBatchNos().add(s.getBatchNo());
        }
        return receipt;
    }

    private String safeLoginName() {
        try {
            return ShiroUtils.getLoginName();
        } catch (RuntimeException e) {
            return "Task";
        }
    }
}
