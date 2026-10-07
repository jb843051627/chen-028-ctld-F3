package com.fc.v2.service.ctld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fc.v2.common.exception.ctld.FlowRowException;
import com.fc.v2.service.ctld.FlowRowEngine.Row;

/**
 * 四问唯一出处 {@link FlowRowEngine} 的规矩测试：
 * 拨（上限认定、到顶即止、空拨）、行（三态、各行各定、行次系统排）、
 * 数（两数同到、切法两式、编码标注）、去处与回执（五条否决、三栏同源、底册作准）。
 */
class FlowRowEngineTest {

    /** 底册与告警线的内存顶替 */
    private static final class Gates implements FlowRowEngine.GateContext {
        final Map<String, FlowRowEngine.PlotRef> plots = new HashMap<>();
        BigDecimal alarm;

        Gates withPlot(String code, String title, String zoneNo) {
            plots.put(code, new FlowRowEngine.PlotRef(code, title, zoneNo));
            return this;
        }

        Gates withAlarm(String v) {
            alarm = new BigDecimal(v);
            return this;
        }

        @Override
        public FlowRowEngine.PlotRef plotOf(String plotCode) {
            return plots.get(plotCode);
        }

        @Override
        public BigDecimal alarmLine() {
            return alarm;
        }
    }

    private static Row row(String code, String qty, String chk) {
        return FlowRowEngine.rowOf(code, qty == null ? null : new BigDecimal(qty),
                chk == null ? null : new BigDecimal(chk), null, null, null);
    }

    private static FlowRowEngine.BatchCmd cmd(String batchNo, Integer limit, List<String> lines) {
        FlowRowEngine.BatchCmd c = new FlowRowEngine.BatchCmd();
        c.setBatchNo(batchNo);
        c.setDeclaredLimit(limit);
        c.setParseMode(FlowRowEngine.MODE_DELIMITED);
        c.setLines(lines);
        return c;
    }

    // ============================ 第一节 说拨 ============================

    @Test
    void 上限只认拨头那一格_空负离谱都按300() {
        assertEquals(300, FlowRowEngine.resolveLimit(null), "空着按 300 认");
        assertEquals(300, FlowRowEngine.resolveLimit(0), "零视同没写");
        assertEquals(300, FlowRowEngine.resolveLimit(-8), "负数视同没写");
        assertEquals(300, FlowRowEngine.resolveLimit(99999999), "大得离谱视同没写");
        assertEquals(50, FlowRowEngine.resolveLimit(50), "写定的照认");
        assertEquals(100000, FlowRowEngine.resolveLimit(100000), "离谱线本身还作数");
    }

    @Test
    void 空拨合法_回执只落空拨两个字_不去够头一条() {
        FlowRowReceipt r = FlowRowEngine.intakeRows(new ArrayList<>(), null, new Gates());
        assertTrue(r.isEmptyBatch());
        assertEquals("空拨", r.getMsg(), "空拨只落「空拨」两个字");
        assertFalse(r.getMsg().contains("收讫"), "不收讫那句不出");
        assertEquals(0, r.getInCount());
        assertEquals(0, r.getAcceptCount());
        assertEquals(0, r.getRejectCount());

        // 空集合走 intake 也不许去够集合里的头一条
        FlowRowReceipt r2 = FlowRowEngine.intake(cmd("B0", null, new ArrayList<>()), new Gates());
        assertTrue(r2.isEmptyBatch());
        assertEquals("空拨", r2.getMsg());
    }

    @Test
    void 到顶即止_多出来的另起一拨接住_不许硬塞() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(row("P0001", "3", "2"));
        }
        FlowRowReceipt r = FlowRowEngine.intakeRows(rows, 3, g);
        assertEquals(3, r.getRows().size(), "到顶即止，本拨只装 3 行");
        assertEquals(2, r.getOverflow().size(), "多出来的另起一拨接住");
        assertEquals(3, r.getInCount());
        assertEquals(3, r.getAcceptCount());
        assertEquals(3, r.getLimitResolved());
    }

    // ============================ 第二节 说行 ============================

    @Test
    void 行去处只有三种写法_第四种落不了库() {
        assertTrue(FlowRowEngine.isValidStatus(0));
        assertTrue(FlowRowEngine.isValidStatus(1));
        assertTrue(FlowRowEngine.isValidStatus(2));
        assertFalse(FlowRowEngine.isValidStatus(3), "第四种写法落不了库");
        assertFalse(FlowRowEngine.isValidStatus(-1));
        assertFalse(FlowRowEngine.isValidStatus(null));
    }

    @Test
    void 同一处地块一拨占两行_各定各的去向互不覆盖() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        List<Row> rows = Arrays.asList(row("P0001", "3", "2"), row("P0001", "0", "0"));
        FlowRowReceipt r = FlowRowEngine.intakeRows(rows, null, g);
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, r.getRows().get(0).getStatus(), "土壤样那行收下");
        assertEquals(FlowRowEngine.STATUS_REJECTED, r.getRows().get(1).getStatus(), "农产品样那行退回");
        assertTrue(r.getRows().get(1).getRejectReason().contains(FlowRowEngine.REASON_QTY_ZERO));
        assertEquals(1, r.getAcceptCount());
        assertEquals(1, r.getRejectCount());
    }

    @Test
    void 行次系统在本拨内排上_重报行仍显示当初那一个行次() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        Row fresh1 = row("P0001", "3", "2");
        Row resub = FlowRowEngine.rowOf("P0001", new BigDecimal("4"), new BigDecimal("3"), null, 7, null);
        Row fresh2 = row("P0001", "5", "4");
        FlowRowReceipt r = FlowRowEngine.intakeRows(Arrays.asList(fresh1, resub, fresh2), null, g);
        assertEquals(Integer.valueOf(1), r.getRows().get(0).getRowNo(), "新行由系统排上");
        assertEquals(Integer.valueOf(7), r.getRows().get(1).getRowNo(), "重报行仍显示当初那一个行次，不重新排队");
        assertEquals(Integer.valueOf(2), r.getRows().get(2).getRowNo(), "后续新行接着排，不撞重报行次");
    }

    // ============================ 第三节 说数 ============================

    @Test
    void 两数要同时到_只到一个就不进_不代填不按0认() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        List<Row> rows = Arrays.asList(
                row("P0001", "3", null),   // 只到样品数
                row("P0001", null, "2"),   // 只到达标数
                row("P0001", null, null),  // 都没到
                row("P0001", "3", "2"));   // 齐身
        FlowRowReceipt r = FlowRowEngine.intakeRows(rows, null, g);
        for (int i = 0; i < 3; i++) {
            Row p = r.getRows().get(i);
            assertEquals(FlowRowEngine.STATUS_UNCHECKED, p.getStatus(), "两数未同到留未勾，第 " + i + " 行");
            assertEquals(FlowRowEngine.FLAG_INCOMPLETE, p.getFlagKind());
        }
        assertNull(r.getRows().get(0).getChkNum(), "系统不代填，更不许按 0 认下");
        assertEquals(3, r.getPendingCount());
        assertEquals(1, r.getInCount(), "待核行不进三栏");
        assertEquals(1, r.getAcceptCount());
        assertTrue(r.balanced());
    }

    @Test
    void 按分隔符切_栏数不对的另做标注_不按住整拨() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        List<String> lines = Arrays.asList(
                "P0001|东片一斗|QC00|10|8|0.50",
                "P0001|东片一斗|QC00|10",          // 栏数不够，切不开
                "P0001|东片一斗|QC00|6|5|0.40");
        FlowRowReceipt r = FlowRowEngine.intake(cmd("B1", null, lines), g);
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, r.getRows().get(0).getStatus());
        Row bad = r.getRows().get(1);
        assertEquals(FlowRowEngine.STATUS_UNCHECKED, bad.getStatus());
        assertEquals(FlowRowEngine.FLAG_PARSE, bad.getFlagKind(), "切不开的另做标注");
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, r.getRows().get(2).getStatus(), "不许因它把整拨按住");
        assertEquals(2, r.getAcceptCount());
        assertEquals(1, r.getPendingCount());
    }

    @Test
    void 按定宽切_行长不足的另做标注() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        String ok = fixed("P0001", "东片一斗", "QC00", "10", "8", "0.50", null);
        FlowRowEngine.BatchCmd c = cmd("B2", null, Arrays.asList(ok, "P0001  东片一斗"));
        c.setParseMode(FlowRowEngine.MODE_FIXED_WIDTH);
        FlowRowReceipt r = FlowRowEngine.intake(c, g);
        Row good = r.getRows().get(0);
        assertEquals("P0001", good.getPlotCode());
        assertEquals(new BigDecimal("10"), good.getQty());
        assertEquals(new BigDecimal("0.50"), good.getCdValue());
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, good.getStatus());
        assertEquals(FlowRowEngine.FLAG_PARSE, r.getRows().get(1).getFlagKind(), "行长不足定宽切不开");
    }

    private static String fixed(String code, String title, String zone, String qty, String chk, String cd,
                                String orig) {
        return pad(code, 16) + pad(title, 30) + pad(zone, 8) + pad(qty, 10) + pad(chk, 10) + pad(cd, 10)
                + (orig == null ? "" : pad(orig, 6));
    }

    private static String pad(String s, int w) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < w) {
            sb.append(' ');
        }
        return sb.toString();
    }

    @Test
    void 编码带不上的归进同一类标注() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        List<String> lines = Arrays.asList(
                "P0001|东片一斗|QC00|10|8|0.50\f",   // 换页符混进来
                "P0001|东片一斗|QC00|１０|8|0.50");   // 全宽半宽混着用
        FlowRowReceipt r = FlowRowEngine.intake(cmd("B3", null, lines), g);
        assertEquals(FlowRowEngine.FLAG_ENCODING, r.getRows().get(0).getFlagKind(), "换页符归进标注");
        assertEquals(FlowRowEngine.FLAG_ENCODING, r.getRows().get(1).getFlagKind(), "全半宽混用归进同一类标注");
        assertEquals(2, r.getPendingCount());
        assertEquals(0, r.getInCount());
    }

    @Test
    void 纯全宽段落折回半宽照常核收() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        List<String> lines = Arrays.asList("Ｐ０００１|东片一斗|ＱＣ００|１０|８|０.５０");
        FlowRowReceipt r = FlowRowEngine.intake(cmd("B4", null, lines), g);
        Row good = r.getRows().get(0);
        assertEquals("P0001", good.getPlotCode());
        assertEquals(new BigDecimal("10"), good.getQty());
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, good.getStatus());
    }

    @Test
    void 数栏不是数_当那个数没到_逐一点名() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00");
        List<String> lines = Arrays.asList("P0001|东片一斗|QC00|abc|8|0.50");
        FlowRowReceipt r = FlowRowEngine.intake(cmd("B5", null, lines), g);
        Row bad = r.getRows().get(0);
        assertEquals(FlowRowEngine.FLAG_INCOMPLETE, bad.getFlagKind());
        assertTrue(bad.getFlagNote().contains("abc"), "不是数的那串要点名：" + bad.getFlagNote());
    }

    // ============================ 第四节 去处与回执 ============================

    @Test
    void 五条否决线逐条过_套上哪条写明哪条() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00").withAlarm("0.40");
        List<Row> rows = Arrays.asList(
                FlowRowEngine.rowOf("P0001", new BigDecimal("3"), new BigDecimal("2"),
                        new BigDecimal("0.60"), null, null),   // 镉高过告警线
                row("P0001", "0", "0"),                          // 样品数为 0
                row("P0001", "-2", "0"),                         // 样品数为负
                row(null, "3", "2"),                             // 地块码没填
                row("P0001000100010001000100010", "3", "2"));    // 码长超出规范
        FlowRowReceipt r = FlowRowEngine.intakeRows(rows, null, g);
        assertTrue(r.getRows().get(0).getRejectReason().contains(FlowRowEngine.REASON_CD_OVER));
        assertEquals(FlowRowEngine.REASON_QTY_ZERO, r.getRows().get(1).getRejectReason());
        assertTrue(r.getRows().get(2).getRejectReason().contains(FlowRowEngine.REASON_QTY_NEGATIVE));
        assertEquals(FlowRowEngine.REASON_CODE_MISSING, r.getRows().get(3).getRejectReason());
        assertTrue(r.getRows().get(4).getRejectReason().contains(FlowRowEngine.REASON_CODE_LENGTH));
        assertEquals(0, r.getAcceptCount());
        assertEquals(5, r.getRejectCount(), "哪一行套了哪一条否决理由，逐行追溯得出");
        assertTrue(r.balanced());
    }

    @Test
    void 镉实测贴着线不算高过_无线可比不启用该条() {
        Gates onLine = new Gates().withPlot("P0001", "东片一斗", "QC00").withAlarm("0.40");
        Row r1 = FlowRowEngine.rowOf("P0001", new BigDecimal("3"), new BigDecimal("2"),
                new BigDecimal("0.40"), null, null);
        FlowRowReceipt a = FlowRowEngine.intakeRows(Arrays.asList(r1), null, onLine);
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, a.getRows().get(0).getStatus(), "贴着线不算高过");

        Gates noLine = new Gates().withPlot("P0001", "东片一斗", "QC00");
        Row r2 = FlowRowEngine.rowOf("P0001", new BigDecimal("3"), new BigDecimal("2"),
                new BigDecimal("9.99"), null, null);
        FlowRowReceipt b = FlowRowEngine.intakeRows(Arrays.asList(r2), null, noLine);
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, b.getRows().get(0).getStatus(), "查无线该条不启用");
    }

    @Test
    void 行面以底册带出作准_比对不合单列一份() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00").withPlot("P0002", "西片二斗", "QC00");
        List<String> lines = Arrays.asList(
                "P0001|东片一斗|QC00|10|8|0.30",     // 比对合
                "P0002|西片九斗|QC00|10|8|0.30");    // 题名与底册不合
        FlowRowReceipt r = FlowRowEngine.intake(cmd("B6", null, lines), g);
        Row good = r.getRows().get(0);
        assertEquals("东片一斗", good.getPlotTitle(), "行面题名以底册带出作准");
        assertEquals("QC00", good.getZoneNo());
        assertFalse(good.isMismatched());
        Row off = r.getRows().get(1);
        assertEquals("西片二斗", off.getPlotTitle(), "报文那串只用来比对，行面仍以底册作准");
        assertTrue(off.isMismatched());
        assertEquals(1, r.getMismatches().size(), "比对不合的行由回执单列一份");
        assertEquals(FlowRowEngine.STATUS_ACCEPTED, off.getStatus(), "比对不合不挡本行去处");
    }

    @Test
    void 底册查无码不自作主张_留待核清单() {
        Gates g = new Gates(); // 底册一空二白
        List<Row> rows = Arrays.asList(row("P9999", "3", "2"));
        FlowRowReceipt r = FlowRowEngine.intakeRows(rows, null, g);
        Row p = r.getRows().get(0);
        assertEquals(FlowRowEngine.STATUS_UNCHECKED, p.getStatus());
        assertEquals(FlowRowEngine.FLAG_NO_PLOT, p.getFlagKind());
        assertEquals(1, r.getPendings().size(), "空悬未勾的单列一份待核清单");
        assertEquals(0, r.getInCount(), "待核行不进三栏");
    }

    @Test
    void 三栏与逐行去向同一次点算_后两栏相加等于头一栏() {
        Gates g = new Gates().withPlot("P0001", "东片一斗", "QC00").withAlarm("0.40");
        List<Row> rows = Arrays.asList(
                row("P0001", "3", "2"),      // 收下
                row("P0001", "0", "0"),      // 退回
                row("P0001", null, "2"),     // 待核
                row("P0001", "5", "4"));     // 收下
        FlowRowReceipt r = FlowRowEngine.intakeRows(rows, null, g);
        assertEquals(3, r.getInCount());
        assertEquals(2, r.getAcceptCount());
        assertEquals(1, r.getRejectCount());
        assertEquals(1, r.getPendingCount());
        assertTrue(r.balanced(), "后两栏相加须等于头一栏");
        assertEquals(4, r.getRows().size(), "抽任意一行回核都在全量逐行去向里");
    }

    @Test
    void 拨头缺件整拨留在门外() {
        assertThrows(FlowRowException.class,
                () -> FlowRowEngine.intake(cmd(" ", null, new ArrayList<>()), new Gates()));
        FlowRowEngine.BatchCmd bad = cmd("B7", null, Arrays.asList("P0001|t|Z|1|1|0.1"));
        bad.setParseMode("随便切");
        assertThrows(FlowRowException.class, () -> FlowRowEngine.intake(bad, new Gates()),
                "切法只有两种，第三种整拨留在门外");
    }
}
