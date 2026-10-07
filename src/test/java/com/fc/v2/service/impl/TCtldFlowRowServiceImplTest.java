package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.fc.v2.mapper.auto.TCtldFlowArriveMapper;
import com.fc.v2.mapper.auto.TCtldFlowBatchMapper;
import com.fc.v2.mapper.auto.TCtldFlowRowMapper;
import com.fc.v2.mapper.auto.TCtldLimitLineMapper;
import com.fc.v2.mapper.auto.TCtldPlotMapper;
import com.fc.v2.model.auto.TCtldFlowArrive;
import com.fc.v2.model.auto.TCtldFlowBatch;
import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.service.ctld.FlowRowEngine;
import com.fc.v2.service.ctld.FlowRowReceipt;

/**
 * 服务层规矩：收拨与流水联动（拨头、各行、到门账同一事务）、同一批次码重到只照面、
 * 到顶溢出另起一拨、行不漂拨、回执三栏与逐行去向同源。库层全部 mock 成内存态，事务用立刻提交的假事务。
 */
class TCtldFlowRowServiceImplTest {

    private TCtldFlowRowMapper rowMapper;
    private TCtldFlowBatchMapper batchMapper;
    private TCtldFlowArriveMapper arriveMapper;
    private TCtldPlotMapper plotMapper;
    private TCtldLimitLineMapper limitLineMapper;
    private TCtldFlowRowServiceImpl svc;

    private final AtomicLong ids = new AtomicLong(100);
    private final List<TCtldFlowRow> rowStore = new ArrayList<>();
    private final Map<String, TCtldFlowBatch> headers = new LinkedHashMap<>();
    private final List<TCtldFlowArrive> arrives = new ArrayList<>();
    private final Map<String, FlowRowEngine.PlotRef> plots = new HashMap<>();
    private BigDecimal alarm;

    @BeforeEach
    void setUp() {
        rowMapper = mock(TCtldFlowRowMapper.class);
        batchMapper = mock(TCtldFlowBatchMapper.class);
        arriveMapper = mock(TCtldFlowArriveMapper.class);
        plotMapper = mock(TCtldPlotMapper.class);
        limitLineMapper = mock(TCtldLimitLineMapper.class);

        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        svc = new TCtldFlowRowServiceImpl(txm) {
            @Override
            FlowRowEngine.PlotRef plotRefOf(String plotCode) {
                return plots.get(plotCode);
            }

            @Override
            BigDecimal alarmLineNow() {
                return alarm;
            }

            @Override
            TCtldFlowBatch findBatch(String batchNo) {
                return headers.get(batchNo);
            }

            @Override
            int countArrive(String batchNo) {
                int n = 0;
                for (TCtldFlowArrive a : arrives) {
                    if (batchNo.equals(a.getBatchNo())) {
                        n++;
                    }
                }
                return n;
            }

            @Override
            List<TCtldFlowRow> rowsOfBatch(String batchNo) {
                return rowStore.stream().filter(r -> batchNo.equals(r.getBatchNo()))
                        .collect(Collectors.toList());
            }

            @Override
            List<TCtldFlowBatch> overflowSubs(String batchNo) {
                return headers.values().stream()
                        .filter(h -> batchNo.equals(h.getOverflowFrom()))
                        .collect(Collectors.toList());
            }
        };
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "ctldFlowRowMapper", rowMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "flowBatchMapper", batchMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "flowArriveMapper", arriveMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "plotMapper", plotMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "limitLineMapper", limitLineMapper);

        doAnswer((InvocationOnMock inv) -> {
            TCtldFlowRow r = inv.getArgument(0);
            r.setId(ids.incrementAndGet());
            rowStore.add(r);
            return 1;
        }).when(rowMapper).insert(any());
        doAnswer((InvocationOnMock inv) -> {
            TCtldFlowBatch h = inv.getArgument(0);
            h.setId(ids.incrementAndGet());
            headers.put(h.getBatchNo(), h);
            return 1;
        }).when(batchMapper).insert(any());
        doAnswer((InvocationOnMock inv) -> {
            TCtldFlowArrive a = inv.getArgument(0);
            a.setId(ids.incrementAndGet());
            arrives.add(a);
            return 1;
        }).when(arriveMapper).insert(any());

        plots.put("P0001", new FlowRowEngine.PlotRef("P0001", "东片一斗", "QC00"));
        alarm = new BigDecimal("0.40");
    }

    private static FlowRowEngine.BatchCmd cmd(String batchNo, Integer limit, String... lines) {
        FlowRowEngine.BatchCmd c = new FlowRowEngine.BatchCmd();
        c.setBatchNo(batchNo);
        c.setDeclaredLimit(limit);
        c.setParseMode(FlowRowEngine.MODE_DELIMITED);
        c.setPutKind("镇级补报");
        c.setLines(new ArrayList<>(Arrays.asList(lines)));
        return c;
    }

    private List<TCtldFlowArrive> arrivesOf(String batchNo) {
        return arrives.stream().filter(a -> batchNo.equals(a.getBatchNo())).collect(Collectors.toList());
    }

    @Test
    void 整拨落地_各行各的去向原样落库_收下的不被抹回() {
        FlowRowReceipt r = svc.intake(cmd("B1", null,
                "P0001|东片一斗|QC00|10|8|0.30",   // 收下
                "P0001|东片一斗|QC00|0|0|0.30",    // 退回：送检样品数为0
                "|东片一斗|QC00|3|2|0.30",         // 退回：地块码没填
                "P9999|某块|QC01|3|2|0.30"));      // 待核：底册查无此码

        assertTrue(r.balanced());
        assertEquals(3, r.getInCount());
        assertEquals(1, r.getAcceptCount());
        assertEquals(2, r.getRejectCount());
        assertEquals(1, r.getPendingCount());

        TCtldFlowBatch h = headers.get("B1");
        assertNotNull(h, "拨一进门先把行数点出来，连同写定的上限一并存进本拨流水");
        assertEquals(3, h.getInCount());
        assertEquals(1, h.getAcceptCount());
        assertEquals(2, h.getRejectCount());
        assertEquals(1, h.getPendingCount());
        assertEquals(300, h.getLimitResolved(), "抬头空着按 300 认");

        assertEquals(4, rowStore.size(), "退回几行算几行，收下几行算几行，两类都原样留在库内");
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, rowStore.get(0).getStatus(),
                "个别行不成，已收下的行不许被一并抹回未勾");
        assertEquals(FlowRowEngine.STATUS_REJECTED, rowStore.get(1).getStatus());
        assertTrue(rowStore.get(1).getRejectReason().contains(FlowRowEngine.REASON_QTY_ZERO),
                "为哪一条退逐行可追溯");
        assertEquals(FlowRowEngine.STATUS_UNCHECKED, rowStore.get(3).getStatus());
        assertEquals(FlowRowEngine.FLAG_NO_PLOT, rowStore.get(3).getFlagKind());
        assertEquals(1, arrivesOf("B1").size());
        assertEquals("LANDED", arrivesOf("B1").get(0).getResultKind());
    }

    @Test
    void 空拨只落空拨_不收讫那句不出() {
        FlowRowReceipt r = svc.intake(cmd("B2", null));
        assertTrue(r.isEmptyBatch());
        assertEquals("空拨", r.getMsg());
        assertFalse(r.getMsg().contains("收讫"));
        TCtldFlowBatch h = headers.get("B2");
        assertNotNull(h, "空拨也是到门的一拨，流水照留");
        assertEquals(1, h.getStatus(), "拨面落「空拨」");
        assertEquals(0, rowStore.size(), "一行也没收着，不许去够集合里的头一条");
    }

    @Test
    void 同一批次码第二回到门只照面_不翻行不另立一拨() {
        FlowRowReceipt first = svc.intake(cmd("B3", null,
                "P0001|东片一斗|QC00|10|8|0.30",
                "P0001|东片一斗|QC00|6|5|0.20"));
        assertEquals(2, first.getAcceptCount());
        int rowsAfterFirst = rowStore.size();

        FlowRowReceipt again = svc.intake(cmd("B3", null,
                "P0001|东片一斗|QC00|0|0|0.99",   // 就算内容全换，也只照头一拨
                "|x|y|1|1|0.10"));
        assertTrue(again.isReplayed(), "第二遍到只能照面");
        assertEquals(2, again.getAcceptCount(), "照头一拨的回执回话");
        assertEquals(rowsAfterFirst, rowStore.size(), "不许把头一遍落定的行翻回未勾，也不添行");
        assertEquals(1, headers.size(), "不许另立一拨");
        assertEquals(2, arrivesOf("B3").size(), "两回各留一份流水，并起来期数才对得平");
        assertEquals("REPLAYED", arrivesOf("B3").get(1).getResultKind());
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, rowStore.get(0).getStatus(), "落定的行原地不动");
    }

    @Test
    void 并发撞唯一拨头_整笔回滚照头一拨回话() {
        svc.intake(cmd("B4", null, "P0001|东片一斗|QC00|10|8|0.30"));
        int rowsAfterFirst = rowStore.size();
        // 模拟并发：第二回 findBatch 时还看不见，insert 时撞 uk_flow_batch_no
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_flow_batch_no"))
                .when(batchMapper).insert(any());

        FlowRowReceipt r = svc.intake(cmd("B4", null, "P0001|东片一斗|QC00|0|0|0.99"));
        assertTrue(r.isReplayed(), "撞唯一拨头照头一拨回话（幂等）");
        assertEquals(1, r.getAcceptCount());
        assertEquals(rowsAfterFirst, rowStore.size(), "整笔回滚，一行不添");
        assertEquals(1, headers.size());
    }

    @Test
    void 到顶溢出另起一拨接住_行不许从这一拨漂到那一拨() {
        FlowRowReceipt r = svc.intake(cmd("B5", 2,
                "P0001|东片一斗|QC00|1|1|0.10",
                "P0001|东片一斗|QC00|2|2|0.10",
                "P0001|东片一斗|QC00|3|3|0.10",
                "P0001|东片一斗|QC00|4|4|0.10",
                "P0001|东片一斗|QC00|5|5|0.10"));

        assertEquals(2, r.getInCount(), "头一拨到顶即止");
        assertEquals(Arrays.asList("B5-溢1", "B5-溢2"), r.getOverflowBatchNos(), "多出来的另起一拨接住");
        assertEquals(3, headers.size());
        assertEquals(2, headers.get("B5-溢1").getInCount());
        assertEquals(1, headers.get("B5-溢2").getInCount());
        assertEquals("B5", headers.get("B5-溢1").getOverflowFrom());

        Map<String, Long> byBatch = rowStore.stream()
                .collect(Collectors.groupingBy(TCtldFlowRow::getBatchNo, Collectors.counting()));
        assertEquals(3, byBatch.size(), "行不许从这一拨漂到那一拨去");
        assertEquals(2L, byBatch.get("B5"));
        assertEquals(2L, byBatch.get("B5-溢1"));
        assertEquals(1L, byBatch.get("B5-溢2"));
        // 各拨行次各自从 1 排上
        assertEquals(Integer.valueOf(1), rowStore.get(2).getRowNo());
        assertEquals("B5-溢1", rowStore.get(2).getBatchNo());
    }

    @Test
    void 回执复述照抄本拨流水_不另起一回去算() {
        FlowRowReceipt landed = svc.intake(cmd("B6", null,
                "P0001|东片一斗|QC00|10|8|0.30",
                "P0001|东片一斗|QC00|0|0|0.30"));
        FlowRowReceipt reread = svc.receiptOf("B6");
        assertFalse(reread.isReplayed());
        assertEquals(landed.getInCount(), reread.getInCount());
        assertEquals(landed.getAcceptCount(), reread.getAcceptCount());
        assertEquals(landed.getRejectCount(), reread.getRejectCount());
        assertTrue(reread.balanced(), "复述的三栏与逐行去向同源，后两栏相加等于头一栏");
        assertEquals(2, reread.getRows().size(), "抽任意一行回核，本行去处与三栏相互印证");
        assertEquals(landed.getMsg(), reread.getMsg());
    }

    @Test
    void 老入口对象式提交_空拨回0_重报行留当初行次_重递照头一拨的数() {
        // 空拨：不许去够集合里的头一条
        assertEquals(0, svc.submitBatch("L0", new ArrayList<>()));
        assertEquals(1, headers.get("L0").getStatus());

        TCtldFlowRow good = new TCtldFlowRow();
        good.setItemCode("P0001");
        good.setQty(new BigDecimal("3"));
        good.setChkNum(new BigDecimal("2"));
        TCtldFlowRow zero = new TCtldFlowRow();
        zero.setItemCode("P0001");
        zero.setQty(new BigDecimal("0"));
        zero.setChkNum(new BigDecimal("0"));
        TCtldFlowRow resub = new TCtldFlowRow();
        resub.setItemCode("P0001");
        resub.setQty(new BigDecimal("4"));
        resub.setChkNum(new BigDecimal("3"));
        resub.setRowNo(9); // 吃了退回重报，行面仍显示当初那一个行次

        int ok = svc.submitBatch("L1", Arrays.asList(good, zero, resub));
        assertEquals(2, ok, "回收下行数：好行与重报行收下，零数行退回");
        List<TCtldFlowRow> mine = rowStore.stream().filter(r -> "L1".equals(r.getBatchNo()))
                .collect(Collectors.toList());
        assertEquals(Integer.valueOf(9), mine.get(2).getRowNo(), "重报行不重新排队");
        assertEquals(Integer.valueOf(1), mine.get(0).getRowNo());
        assertEquals(Integer.valueOf(2), mine.get(1).getRowNo());

        int before = rowStore.size();
        int again = svc.submitBatch("L1", Arrays.asList(good));
        assertEquals(2, again, "同一批次码重递照头一拨的数回话");
        assertEquals(before, rowStore.size(), "不重复入库");
    }

    @Test
    void 批次码空着整拨留在门外() {
        com.fc.v2.common.exception.ctld.FlowRowException ex = org.junit.jupiter.api.Assertions
                .assertThrows(com.fc.v2.common.exception.ctld.FlowRowException.class,
                        () -> svc.submitBatch("  ", Arrays.asList(new TCtldFlowRow())));
        assertTrue(ex.getMessage().contains("批次码"));
        assertEquals(0, headers.size());
        assertNull(headers.get("  "));
    }
}
