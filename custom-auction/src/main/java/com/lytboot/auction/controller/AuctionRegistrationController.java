package com.lytboot.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lytboot.auction.domain.entity.AuctionDeposit;
import com.lytboot.auction.domain.entity.AuctionRegistration;
import com.lytboot.auction.service.IAuctionDepositService;
import com.lytboot.auction.service.IAuctionRegistrationService;
import com.lytboot.common.annotation.Log;
import com.lytboot.common.core.controller.BaseController;
import com.lytboot.common.core.domain.AjaxResult;
import com.lytboot.common.core.page.TableDataInfo;
import com.lytboot.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报名与保证金管理Controller
 */
@RestController
@RequestMapping("/auction/registration")
public class AuctionRegistrationController extends BaseController {

    @Autowired
    private IAuctionRegistrationService registrationService;

    @Autowired
    private IAuctionDepositService depositService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('auction:registration:list')")
    public TableDataInfo list(AuctionRegistration registration) {
        startPage();
        LambdaQueryWrapper<AuctionRegistration> wrapper = new LambdaQueryWrapper<>();
        if (registration.getActivityId() != null) {
            wrapper.eq(AuctionRegistration::getActivityId, registration.getActivityId());
        }
        if (registration.getAuditStatus() != null && !registration.getAuditStatus().isEmpty()) {
            wrapper.eq(AuctionRegistration::getAuditStatus, registration.getAuditStatus());
        }
        wrapper.orderByDesc(AuctionRegistration::getCreateTime);
        List<AuctionRegistration> list = registrationService.list(wrapper);
        return getDataTable(list);
    }

    @GetMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPermi('auction:registration:query')")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(registrationService.getById(id));
    }

    @Log(title = "新增报名申请", businessType = BusinessType.INSERT)
    @PostMapping
    @PreAuthorize("@ss.hasPermi('auction:registration:add')")
    public AjaxResult add(@RequestBody AuctionRegistration registration) {
        registrationService.register(registration.getActivityId(), getUserId(), registration);
        return AjaxResult.success();
    }

    @Log(title = "审批报名申请", businessType = BusinessType.UPDATE)
    @PutMapping("/audit")
    @PreAuthorize("@ss.hasPermi('auction:registration:audit')")
    public AjaxResult audit(@RequestBody AuctionRegistration registration) {
        registrationService.auditRegistration(registration.getId(), registration.getAuditStatus(), registration.getAuditRemark(), getUserId());
        return AjaxResult.success();
    }

    @Log(title = "新增保证金报名申请", businessType = BusinessType.UPDATE)
    @PutMapping("/deposit/apply")
    @PreAuthorize("@ss.hasPermi('auction:registration:edit')")
    public AjaxResult applyDeposit(@RequestBody Map<String, Object> params) {
        Long registrationId = Long.valueOf(params.get("id").toString());
        String payType = (String) params.get("payType");
        String payVoucher = (String) params.get("payVoucher");
        registrationService.applyDeposit(registrationId, payType, payVoucher);
        return AjaxResult.success();
    }

    @Log(title = "审批保证金报名申请", businessType = BusinessType.UPDATE)
    @PutMapping("/deposit/audit")
    @PreAuthorize("@ss.hasPermi('auction:registration:audit')")
    public AjaxResult auditDeposit(@RequestBody AuctionDeposit deposit) {
        registrationService.auditDeposit(deposit.getId(), deposit.getAuditStatus(), deposit.getAuditRemark(), getUserId());
        return AjaxResult.success();
    }

    @GetMapping("/deposit/list")
    @PreAuthorize("@ss.hasPermi('auction:registration:list')")
    public TableDataInfo depositList(AuctionDeposit deposit) {
        startPage();
        LambdaQueryWrapper<AuctionDeposit> wrapper = new LambdaQueryWrapper<>();
        if (deposit.getActivityId() != null) {
            wrapper.eq(AuctionDeposit::getActivityId, deposit.getActivityId());
        }
        wrapper.orderByDesc(AuctionDeposit::getCreateTime);
        List<AuctionDeposit> list = depositService.list(wrapper);
        return getDataTable(list);
    }
}
