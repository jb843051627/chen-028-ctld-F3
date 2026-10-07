package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.ctld.FlowRowException;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TCtldFlowRow;
import com.fc.v2.service.ITCtldFlowRowService;
import com.fc.v2.service.ctld.FlowRowEngine;
import com.fc.v2.service.ctld.FlowRowReceipt;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 土壤-农产品协同检测流水核收 Controller（batch-process 形状：整拨一次递齐，零散逐行不收）。
 * 四问口径只认后端 engine/service 这一处，屏上抢先亮出来的那一份不作凭；
 * 本层只照纸传话，不开单行增删改的口子（自行写库、自行改行两条路一律封死）。
 *
 * @author fuce
 * @date 2026-10-07
 */
@Api(value = "土壤-农产品协同检测流水核收")
@Controller
@RequestMapping("/ctldFlowRow")
public class CtldFlowRowController extends BaseController {

    @Autowired
    private ITCtldFlowRowService ctldFlowRowService;

    /** 被拒回话：固定码 + 点到具体拨/行/栏的话，不回笼统一句「信息不全」 */
    private AjaxResult reject(FlowRowException e) {
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    @Log(title = "协同检测流水核收台账", action = "list")
    @ApiOperation(value = "核收台账", notes = "老行在回库里一律照常查得到，一条不减")
    @GetMapping("/list")
    @RequiresPermissions("ctldFlowRow:list")
    @ResponseBody
    public ResultTable list(TCtldFlowRow record) {
        QueryWrapper<TCtldFlowRow> queryWrapper = new QueryWrapper<TCtldFlowRow>();
        queryWrapper.eq("del_flag", 0);
        if (record != null && record.getBatchNo() != null && !record.getBatchNo().trim().isEmpty()) {
            queryWrapper.eq("batch_no", record.getBatchNo().trim());
        }
        queryWrapper.orderByAsc("batch_no", "row_no");
        startPage();
        com.github.pagehelper.PageInfo<TCtldFlowRow> page =
                new com.github.pagehelper.PageInfo<TCtldFlowRow>(
                        ctldFlowRowService.selectTCtldFlowRowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "协同检测流水整拨核收", action = "intake")
    @ApiOperation(value = "整拨核收", notes = "点行数、看数据、定去处、出回执一口气走完；同一批次码重到只照面")
    @PostMapping("/intake")
    @RequiresPermissions("ctldFlowRow:intake")
    @ResponseBody
    public AjaxResult intake(@RequestBody FlowRowEngine.BatchCmd cmd) {
        try {
            FlowRowReceipt receipt = ctldFlowRowService.intake(cmd);
            return AjaxResult.success(200, receipt.getMsg(), receipt);
        } catch (FlowRowException e) {
            return reject(e);
        }
    }

    @ApiOperation(value = "回执复述", notes = "三个数从本拨流水照抄，与逐行去向同源；屏上那份只是复述")
    @GetMapping("/receipt")
    @RequiresPermissions("ctldFlowRow:list")
    @ResponseBody
    public AjaxResult receipt(String batchNo) {
        try {
            return AjaxResult.success("ok").put("data", ctldFlowRowService.receiptOf(batchNo));
        } catch (FlowRowException e) {
            return reject(e);
        }
    }

    @ApiOperation(value = "退回明细", notes = "哪一条行套了哪一条否决理由，逐行追溯")
    @GetMapping("/errors")
    @RequiresPermissions("ctldFlowRow:list")
    @ResponseBody
    public AjaxResult errors(String batchNo) {
        return AjaxResult.success("ok").put("data", ctldFlowRowService.listErrors(batchNo));
    }

    @ApiOperation(value = "待核清单", notes = "去处空悬在未勾那一地的行，由回核的人逐行定去向，系统不自作主张")
    @GetMapping("/pending")
    @RequiresPermissions("ctldFlowRow:list")
    @ResponseBody
    public AjaxResult pending(@RequestParam(required = false) String batchNo) {
        return AjaxResult.success("ok").put("data", ctldFlowRowService.listPending(batchNo));
    }
}
