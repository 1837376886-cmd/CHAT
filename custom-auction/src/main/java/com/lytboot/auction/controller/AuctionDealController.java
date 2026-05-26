package com.lytboot.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lytboot.auction.domain.entity.AuctionDeal;
import com.lytboot.auction.domain.entity.AuctionSettlement;
import com.lytboot.auction.service.IAuctionDealService;
import com.lytboot.auction.service.IAuctionSettlementService;
import com.lytboot.common.annotation.Log;
import com.lytboot.common.core.controller.BaseController;
import com.lytboot.common.core.domain.AjaxResult;
import com.lytboot.common.core.page.TableDataInfo;
import com.lytboot.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 成交记录与尾款结算Controller
 */
@RestController
@RequestMapping("/auction/deal")
public class AuctionDealController extends BaseController {

    @Autowired
    private IAuctionDealService dealService;

    @Autowired
    private IAuctionSettlementService settlementService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('auction:deal:list')")
    public TableDataInfo list(AuctionDeal deal) {
        startPage();
        LambdaQueryWrapper<AuctionDeal> wrapper = new LambdaQueryWrapper<>();
        if (deal.getActivityId() != null) {
            wrapper.eq(AuctionDeal::getActivityId, deal.getActivityId());
        }
        wrapper.orderByDesc(AuctionDeal::getCreateTime);
        List<AuctionDeal> list = dealService.list(wrapper);
        return getDataTable(list);
    }

    @GetMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPermi('auction:deal:query')")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(dealService.getById(id));
    }

    @Log(title = "成交记录", businessType = BusinessType.UPDATE)
    @PutMapping("/breach/{id}")
    @PreAuthorize("@ss.hasPermi('auction:deal:edit')")
    public AjaxResult breach(@PathVariable("id") Long id) {
        dealService.markBreach(id);
        return AjaxResult.success();
    }

    @GetMapping("/settlement/list")
    @PreAuthorize("@ss.hasPermi('auction:settlement:list')")
    public TableDataInfo settlementList(AuctionSettlement settlement) {
        startPage();
        LambdaQueryWrapper<AuctionSettlement> wrapper = new LambdaQueryWrapper<>();
        if (settlement.getActivityId() != null) {
            wrapper.eq(AuctionSettlement::getActivityId, settlement.getActivityId());
        }
        if (settlement.getAuditStatus() != null && !settlement.getAuditStatus().isEmpty()) {
            wrapper.eq(AuctionSettlement::getAuditStatus, settlement.getAuditStatus());
        }
        wrapper.orderByDesc(AuctionSettlement::getCreateTime);
        List<AuctionSettlement> list = settlementService.list(wrapper);
        return getDataTable(list);
    }

    @Log(title = "提交尾款结算申请", businessType = BusinessType.INSERT)
    @PostMapping("/settlement")
    @PreAuthorize("@ss.hasPermi('auction:settlement:add')")
    public AjaxResult addSettlement(@RequestBody AuctionSettlement settlement) {
        settlementService.submitSettlement(settlement);
        return AjaxResult.success();
    }

    @Log(title = "审批尾款结算申请", businessType = BusinessType.UPDATE)
    @PutMapping("/settlement/audit")
    @PreAuthorize("@ss.hasPermi('auction:settlement:audit')")
    public AjaxResult auditSettlement(@RequestBody AuctionSettlement settlement) {
        settlementService.auditSettlement(settlement.getId(), settlement.getAuditStatus(), settlement.getAuditRemark(), getUserId());
        return AjaxResult.success();
    }
}
