package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.service.ctld.FlowRowEngine;
import com.fc.v2.service.ctld.FlowRowReceipt;

/**
 * 土壤-农产品协同检测流水核收行 Service接口（batch-process 形状：整拨一次递齐，零散逐行不收）。
 * 行去处、重到批次码、整拨成不成、拨头三个数，四问只认 {@link FlowRowEngine} 这一处给出的答案；
 * 本接口不开单行增删改的口子（自行写库、自行改行两条路一律封死）。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITCtldFlowRowService {

    /** 按主键回查单行（老行在回库里一律照常查得到，一条不减） */
    TCtldFlowRow selectTCtldFlowRowById(Long id);

    /** 台账回查（只读） */
    List<TCtldFlowRow> selectTCtldFlowRowList(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TCtldFlowRow> queryWrapper);

    /**
     * 整拨核收：点行数、看数据、定去处、出回执，四件事一口气走完，中间不插人手。
     * 同一批次码第二回到门只照面：照头一拨的回执回话，不动行、不另立一拨。
     */
    FlowRowReceipt intake(FlowRowEngine.BatchCmd cmd);

    /**
     * 老入口：对象式整批提交，回**收下**行数；口径与 intake 同一处（上限按拨头缺省 300 认，
     * 到顶溢出另起一拨；空拨回 0；同一批次码重递照头一拨的数回话，不重复入库）。
     */
    int submitBatch(String batchNo, List<TCtldFlowRow> rows);

    /** 该批次的退回行明细（按行次升序，每行带着为哪一条退） */
    List<TCtldFlowRow> listErrors(String batchNo);

    /** 待核清单：去处空悬在未勾那一地的行，由回核的人逐行定去向，系统不自作主张替它收 */
    List<TCtldFlowRow> listPending(String batchNo);

    /** 回执复述：从本拨流水照抄出来，不另起一回去算；两处不同源就谈不上对得住 */
    FlowRowReceipt receiptOf(String batchNo);
}
