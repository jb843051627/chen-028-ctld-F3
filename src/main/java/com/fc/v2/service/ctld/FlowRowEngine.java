package com.fc.v2.service.ctld;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fc.v2.common.exception.ctld.FlowRowException;

/**
 * 核收流水纯引擎：四问只此一处给口径。
 * 一、这一拨进来后各行落到什么下场（未勾/收下/退回，第四种写法落不了库）；
 * 二、同一个批次码重到门口怎么办（服务层凭唯一拨头照面，引擎不另开口子）；
 * 三、整拨成不成（空拨只落「空拨」，其余收讫，逐行去向说了算）；
 * 四、拨头上那三个数从哪里出来（与逐行去向同一次点算，后两栏相加等于头一栏）。
 * 引擎不落库、不取时刻，只改入参状态、交出回执；将来按镇收、按季收各另起一条，不动本类参数。
 *
 * @author fuce
 * @date 2026-10-07
 */
public final class FlowRowEngine {

    private FlowRowEngine() {
    }

    /** 行落地情形三态：0未勾 1收下 2退回；第四种写法落不了库 */
    public static final int STATUS_UNCHECKED = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;

    /** 拨头抬头空着按 300 认；写成负数、写成大得离谱的数，都视同没写 */
    public static final int DEFAULT_LIMIT = 300;
    /** 「大得离谱」的认定线：高过它一律视同没写 */
    public static final int MAX_REASONABLE_LIMIT = 100000;

    /** 地块码长规范（局标 4~20 位）：不足不算、超出规范一律退回 */
    public static final int PLOT_CODE_MIN_LEN = 4;
    public static final int PLOT_CODE_MAX_LEN = 20;

    /** 切法只有两种，一拨之内认一种 */
    public static final String MODE_DELIMITED = "DELIMITED";
    public static final String MODE_FIXED_WIDTH = "FIXED_WIDTH";
    public static final String DEFAULT_DELIMITER = "|";
    /** 定宽各栏宽度：地块码16 题名30 分区8 样品数10 达标数10 镉实测10 原报行次6 */
    public static final int[] FIXED_WIDTHS = {16, 30, 8, 10, 10, 10, 6};
    /** 定宽前六栏（必填）合计长度 */
    private static final int FIXED_REQUIRED_LEN = 16 + 30 + 8 + 10 + 10 + 10;

    /** 待核标注类别：切不开的、带不上编码的、两数未同到的、底册查无码的，归进各自一类 */
    public static final String FLAG_PARSE = "PARSE";
    public static final String FLAG_ENCODING = "ENCODING";
    public static final String FLAG_INCOMPLETE = "INCOMPLETE";
    public static final String FLAG_NO_PLOT = "NO_PLOT";

    /** 否决理由：下列情形一律退回并写明为哪一条退 */
    public static final String REASON_CD_OVER = "土壤镉实测数高过当地那条告警线";
    public static final String REASON_QTY_ZERO = "送检样品数为0";
    public static final String REASON_QTY_NEGATIVE = "样品数为负";
    public static final String REASON_CODE_MISSING = "地块码没填";
    public static final String REASON_CODE_LENGTH = "码长超出规范";

    /** 门槛外部取数：底册与告警线 */
    public interface GateContext {
        /** 对地块只认地块码：按码查底册，查无返回 null；题名近似不算、码长不足不算 */
        PlotRef plotOf(String plotCode);

        /** 当地那条告警线（作物镉限量贴线档 th2）；查无线返回 null，该条否决线即不启用 */
        BigDecimal alarmLine();
    }

    /** 底册带出的那一份：行面的地块题名与分区代号一律以它作准 */
    public static class PlotRef {
        private final String plotNo;
        private final String title;
        private final String zoneNo;

        public PlotRef(String plotNo, String title, String zoneNo) {
            this.plotNo = plotNo;
            this.title = title;
            this.zoneNo = zoneNo;
        }

        public String getPlotNo() {
            return plotNo;
        }

        public String getTitle() {
            return title;
        }

        public String getZoneNo() {
            return zoneNo;
        }
    }

    /** 整拨入参：拨头各格 + 报文各行 */
    public static class BatchCmd {
        private String batchNo;
        /** 拨头抬头那一格：容行上限唯一出处 */
        private Integer declaredLimit;
        /** 本拨认的切法：DELIMITED / FIXED_WIDTH，空着按分隔符认 */
        private String parseMode;
        /** 分隔符那一式用的符号，空着按 | 认 */
        private String delimiter;
        private String putKind;
        /** 报文各行（空行不算行）；对象式提交走 rowOf 另搭，不经报文 */
        private List<String> lines = new ArrayList<>();

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        public Integer getDeclaredLimit() {
            return declaredLimit;
        }

        public void setDeclaredLimit(Integer declaredLimit) {
            this.declaredLimit = declaredLimit;
        }

        public String getParseMode() {
            return parseMode;
        }

        public void setParseMode(String parseMode) {
            this.parseMode = parseMode;
        }

        public String getDelimiter() {
            return delimiter;
        }

        public void setDelimiter(String delimiter) {
            this.delimiter = delimiter;
        }

        public String getPutKind() {
            return putKind;
        }

        public void setPutKind(String putKind) {
            this.putKind = putKind;
        }

        public List<String> getLines() {
            return lines;
        }

        public void setLines(List<String> lines) {
            this.lines = lines;
        }
    }

    /**
     * 一行：报文切出来的原样（看数据）+ 定下来的去向（定去处）。
     * 行面（地块题名、分区代号）只以底册带出的作准；报文里那串字符只用来比对。
     */
    public static class Row {
        // —— 报文侧 ——
        private String msgRaw;
        private String plotCode;
        private String msgTitle;
        private String msgZoneNo;
        private BigDecimal qty;
        private BigDecimal chkNum;
        private BigDecimal cdValue;
        /** 重报行带来的当初行次：吃了退回的那一行过几日重报，行面仍显示当初那一个行次 */
        private Integer origRowNo;
        private String putKind;
        // —— 标注侧（待核） ——
        private String flagKind;
        private String flagNote;
        /** 数栏里不是数的那几串，逐一点名（内部捎带，拼进待核记要） */
        private String junkNote;
        // —— 去向侧 ——
        private Integer rowNo;
        private Integer status;
        private String rejectReason;
        private String plotTitle;
        private String zoneNo;
        private boolean mismatched;

        public String getMsgRaw() {
            return msgRaw;
        }

        public void setMsgRaw(String msgRaw) {
            this.msgRaw = msgRaw;
        }

        public String getPlotCode() {
            return plotCode;
        }

        public void setPlotCode(String plotCode) {
            this.plotCode = plotCode;
        }

        public String getMsgTitle() {
            return msgTitle;
        }

        public void setMsgTitle(String msgTitle) {
            this.msgTitle = msgTitle;
        }

        public String getMsgZoneNo() {
            return msgZoneNo;
        }

        public void setMsgZoneNo(String msgZoneNo) {
            this.msgZoneNo = msgZoneNo;
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

        public Integer getOrigRowNo() {
            return origRowNo;
        }

        public void setOrigRowNo(Integer origRowNo) {
            this.origRowNo = origRowNo;
        }

        public String getPutKind() {
            return putKind;
        }

        public void setPutKind(String putKind) {
            this.putKind = putKind;
        }

        public String getFlagKind() {
            return flagKind;
        }

        public void setFlagKind(String flagKind) {
            this.flagKind = flagKind;
        }

        public String getFlagNote() {
            return flagNote;
        }

        public void setFlagNote(String flagNote) {
            this.flagNote = flagNote;
        }

        public String getJunkNote() {
            return junkNote;
        }

        public void setJunkNote(String junkNote) {
            this.junkNote = junkNote;
        }

        public Integer getRowNo() {
            return rowNo;
        }

        public void setRowNo(Integer rowNo) {
            this.rowNo = rowNo;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public String getRejectReason() {
            return rejectReason;
        }

        public void setRejectReason(String rejectReason) {
            this.rejectReason = rejectReason;
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

        public boolean isMismatched() {
            return mismatched;
        }

        public void setMismatched(boolean mismatched) {
            this.mismatched = mismatched;
        }
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 行去处只有三种写法，第四种落不了库：落库前只认这一处把门 */
    public static boolean isValidStatus(Integer status) {
        return status != null
                && (status == STATUS_UNCHECKED || status == STATUS_ACCEPTED || status == STATUS_REJECTED);
    }

    /** 拨头抬头那一格：空着按 300 认；负数、大得离谱，都视同没写 */
    public static int resolveLimit(Integer declared) {
        if (declared == null || declared <= 0 || declared > MAX_REASONABLE_LIMIT) {
            return DEFAULT_LIMIT;
        }
        return declared;
    }

    /** 对象式提交搭一行（不经报文切分）；两数同到与否仍由 intakeRows 统一把门 */
    public static Row rowOf(String plotCode, BigDecimal qty, BigDecimal chkNum, BigDecimal cdValue,
                            Integer origRowNo, String putKind) {
        Row r = new Row();
        r.setPlotCode(plotCode);
        r.setQty(qty);
        r.setChkNum(chkNum);
        r.setCdValue(cdValue);
        r.setOrigRowNo(origRowNo);
        r.setPutKind(putKind);
        return r;
    }

    // ============================ 看数据之一：报文解析 ============================

    /**
     * 切一行。切法只有两种，一拨之内认一种；切不开的、整段带不上编码的（换页符混进来、
     * 全宽半宽混着用）各归一类标注，行留待核，不许因它把整拨按住。
     */
    public static Row parseLine(String raw, String parseMode, String delimiter) {
        Row r = new Row();
        r.setMsgRaw(raw);

        // 编码先把门：换页符混进来、全宽半宽混着用，归进同一类标注
        if (raw.indexOf('\f') >= 0) {
            r.setFlagKind(FLAG_ENCODING);
            r.setFlagNote("换页符混进来，整段带不上编码");
            return r;
        }
        if (hasHalfWidthDigit(raw) && hasFullWidthDigit(raw)) {
            r.setFlagKind(FLAG_ENCODING);
            r.setFlagNote("全宽半宽混着用，整段带不上编码");
            return r;
        }
        String line = toHalfWidth(raw);

        String[] f;
        if (MODE_FIXED_WIDTH.equals(parseMode)) {
            f = cutFixed(line);
            if (f == null) {
                r.setFlagKind(FLAG_PARSE);
                r.setFlagNote("定宽切不开（行长不足 " + FIXED_REQUIRED_LEN + "）");
                return r;
            }
        } else {
            String d = blank(delimiter) ? DEFAULT_DELIMITER : delimiter;
            f = line.split(Pattern.quote(d), -1);
            if (f.length < 6 || f.length > 7) {
                r.setFlagKind(FLAG_PARSE);
                r.setFlagNote("按分隔符切不开（应 6 或 7 栏，实到 " + f.length + " 栏）");
                return r;
            }
        }

        r.setPlotCode(emptyToNull(f[0]));
        r.setMsgTitle(emptyToNull(f[1]));
        r.setMsgZoneNo(emptyToNull(f[2]));
        r.setQty(parseDecimal(f[3], "送检样品数", r));
        r.setChkNum(parseDecimal(f[4], "配对达标数", r));
        r.setCdValue(parseDecimal(f[5], "土壤镉实测数", r));
        if (f.length > 6) {
            r.setOrigRowNo(parseIntOrNull(f[6]));
        }
        return r;
    }

    private static String emptyToNull(String s) {
        String t = s == null ? null : s.trim();
        return t == null || t.isEmpty() ? null : t;
    }

    private static String[] cutFixed(String line) {
        if (line.length() < FIXED_REQUIRED_LEN) {
            return null;
        }
        String[] f = new String[FIXED_WIDTHS.length];
        int pos = 0;
        for (int i = 0; i < FIXED_WIDTHS.length; i++) {
            int end = Math.min(pos + FIXED_WIDTHS[i], line.length());
            f[i] = line.substring(pos, end);
            pos = end;
        }
        return f;
    }

    private static BigDecimal parseDecimal(String s, String fieldName, Row r) {
        String t = emptyToNull(s);
        if (t == null) {
            return null;
        }
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            String junk = fieldName + "那串「" + t + "」不是数";
            r.setJunkNote(r.getJunkNote() == null ? junk : r.getJunkNote() + "、" + junk);
            return null;
        }
    }

    private static Integer parseIntOrNull(String s) {
        String t = emptyToNull(s);
        if (t == null) {
            return null;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasHalfWidthDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFullWidthDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') {
                return true;
            }
        }
        return false;
    }

    /** 纯全宽段落的数字与字母折回半宽；混着用的在上游已被标注拦下，走不到这 */
    private static String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else if (c >= 'Ａ' && c <= 'Ｚ') {
                sb.append((char) (c - 'Ａ' + 'A'));
            } else if (c >= 'ａ' && c <= 'ｚ') {
                sb.append((char) (c - 'ａ' + 'a'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ============================ 四件事一口气走完 ============================

    /** 报文路径：切 + 点行数 + 定去处 + 出回执，一口气走完，中间不插人手 */
    public static FlowRowReceipt intake(BatchCmd cmd, GateContext gates) {
        if (cmd == null || blank(cmd.getBatchNo())) {
            throw new FlowRowException("拨头批次码空着，拨立不起来；整拨留在门外");
        }
        String mode = blank(cmd.getParseMode()) ? MODE_DELIMITED : cmd.getParseMode().trim();
        if (!MODE_DELIMITED.equals(mode) && !MODE_FIXED_WIDTH.equals(mode)) {
            throw new FlowRowException("切法只有两种（按分隔符切、按定宽切），本拨认的是「" + mode
                    + "」，对不上；整拨留在门外");
        }
        List<Row> rows = new ArrayList<>();
        if (cmd.getLines() != null) {
            for (String raw : cmd.getLines()) {
                if (raw == null || raw.trim().isEmpty()) {
                    continue; // 空行不算行
                }
                rows.add(parseLine(raw, mode, cmd.getDelimiter()));
            }
        }
        FlowRowReceipt receipt = intakeRows(rows, cmd.getDeclaredLimit(), gates);
        receipt.setBatchNo(cmd.getBatchNo().trim());
        receipt.setLimitDeclared(cmd.getDeclaredLimit());
        receipt.setParseMode(mode);
        receipt.setPutKind(cmd.getPutKind());
        return receipt;
    }

    /**
     * 点行数、定去处、出回执：三栏与逐行去向在同一次点算里出来。
     * 到顶即止，多出来的放进 overflow 另起一拨接住，不许硬塞。
     */
    public static FlowRowReceipt intakeRows(List<Row> rows, Integer declaredLimit, GateContext gates) {
        FlowRowReceipt receipt = new FlowRowReceipt();
        int limit = resolveLimit(declaredLimit);
        receipt.setLimitResolved(limit);

        // 空拨是合法输入：回执只落「空拨」两个字，不收讫那句不出，更不许去够集合里的头一条
        if (rows == null || rows.isEmpty()) {
            receipt.setEmptyBatch(true);
            receipt.setMsg("空拨");
            return receipt;
        }

        // 到顶即止：多出来的另起一拨接住
        List<Row> admitted = new ArrayList<>(rows.subList(0, Math.min(limit, rows.size())));
        if (rows.size() > limit) {
            receipt.getOverflow().addAll(rows.subList(limit, rows.size()));
        }

        int in = 0;
        int accept = 0;
        int reject = 0;
        int pending = 0;
        for (Row r : admitted) {
            decide(r, gates);
            receipt.getRows().add(r);
            if (r.getStatus() == STATUS_UNCHECKED) {
                pending++;
                receipt.getPendings().add(r);
            } else {
                in++;
                if (r.getStatus() == STATUS_ACCEPTED) {
                    accept++;
                } else {
                    reject++;
                }
                if (r.isMismatched()) {
                    receipt.getMismatches().add(r);
                }
            }
        }
        numberRows(admitted);

        receipt.setInCount(in);
        receipt.setAcceptCount(accept);
        receipt.setRejectCount(reject);
        receipt.setPendingCount(pending);
        StringBuilder msg = new StringBuilder("收讫：进门 ").append(in).append(" 行、收下 ").append(accept)
                .append(" 行、退回 ").append(reject).append(" 行");
        if (pending > 0) {
            msg.append("、待核 ").append(pending).append(" 行");
        }
        if (!receipt.getOverflow().isEmpty()) {
            msg.append("、到顶溢出 ").append(receipt.getOverflow().size()).append(" 行另起一拨");
        }
        receipt.setMsg(msg.toString());
        return receipt;
    }

    /**
     * 定一行的去处。两处不到（标注、两数未同到）留未勾进待核；五条否决线逐条过，
     * 套上哪一条写明哪一条；底册查无码的不自作主张，留待核；对上的行面以底册作准。
     */
    private static void decide(Row r, GateContext gates) {
        // 切不开的、带不上编码的：另做标注，递给回核看的人
        if (r.getFlagKind() != null) {
            r.setStatus(STATUS_UNCHECKED);
            return;
        }
        // 两个数要同时到；只到一个就不进——系统不代填，更不许按 0 认下
        if (r.getQty() == null || r.getChkNum() == null) {
            r.setFlagKind(FLAG_INCOMPLETE);
            String which = r.getQty() == null && r.getChkNum() == null ? "两个数都没到"
                    : (r.getQty() == null ? "只到一个就不进（缺送检样品数）" : "只到一个就不进（缺配对达标数）");
            r.setFlagNote(r.getJunkNote() == null ? which : which + "；" + r.getJunkNote());
            r.setStatus(STATUS_UNCHECKED);
            return;
        }
        // 五条否决线，逐条过；套上哪一条写明哪一条
        String code = r.getPlotCode();
        if (blank(code)) {
            reject(r, REASON_CODE_MISSING);
            return;
        }
        String trimmedCode = code.trim();
        if (trimmedCode.length() < PLOT_CODE_MIN_LEN || trimmedCode.length() > PLOT_CODE_MAX_LEN) {
            reject(r, REASON_CODE_LENGTH + "（局标 " + PLOT_CODE_MIN_LEN + "~" + PLOT_CODE_MAX_LEN
                    + " 位，实到 " + trimmedCode.length() + " 位）");
            return;
        }
        if (r.getQty().compareTo(BigDecimal.ZERO) < 0) {
            reject(r, REASON_QTY_NEGATIVE);
            return;
        }
        if (r.getQty().compareTo(BigDecimal.ZERO) == 0) {
            reject(r, REASON_QTY_ZERO);
            return;
        }
        BigDecimal alarm = gates == null ? null : gates.alarmLine();
        if (r.getCdValue() != null && alarm != null && r.getCdValue().compareTo(alarm) > 0) {
            reject(r, REASON_CD_OVER + "（实测 " + r.getCdValue().stripTrailingZeros().toPlainString()
                    + " 高过告警线 " + alarm.stripTrailingZeros().toPlainString() + "）");
            return;
        }
        // 对地块只认地块码：查无底册的不自作主张，留待核，由回核的人逐行定去向
        PlotRef ref = gates == null ? null : gates.plotOf(trimmedCode);
        if (ref == null) {
            r.setFlagKind(FLAG_NO_PLOT);
            r.setFlagNote("底册查无此地块码「" + trimmedCode + "」，待回核");
            r.setStatus(STATUS_UNCHECKED);
            return;
        }
        // 行面一律以底册带出的那一份作准；报文里那串字符只用来比对
        r.setPlotCode(trimmedCode);
        r.setPlotTitle(ref.getTitle());
        r.setZoneNo(ref.getZoneNo());
        boolean titleOff = !blank(r.getMsgTitle()) && !r.getMsgTitle().equals(ref.getTitle());
        boolean zoneOff = !blank(r.getMsgZoneNo()) && !r.getMsgZoneNo().equals(ref.getZoneNo());
        r.setMismatched(titleOff || zoneOff);
        r.setStatus(STATUS_ACCEPTED);
    }

    private static void reject(Row r, String reason) {
        r.setStatus(STATUS_REJECTED);
        r.setRejectReason(reason);
    }

    /**
     * 行次由系统在本拨内排上，不留填数的空位；吃了退回重报的行仍显示当初那一个行次，不重新排队。
     * 原报行次撞了号的，先到的先留，后到的由系统补排。
     */
    private static void numberRows(List<Row> admitted) {
        Set<Integer> used = new HashSet<>();
        for (Row r : admitted) {
            Integer orig = r.getOrigRowNo();
            if (orig != null && orig > 0 && !used.contains(orig)) {
                r.setRowNo(orig);
                used.add(orig);
            }
        }
        int next = 1;
        for (Row r : admitted) {
            if (r.getRowNo() != null) {
                continue;
            }
            while (used.contains(next)) {
                next++;
            }
            r.setRowNo(next);
            used.add(next);
            next++;
        }
    }
}
