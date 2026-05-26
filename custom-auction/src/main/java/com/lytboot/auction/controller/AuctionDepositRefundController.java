package com.lytboot.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lytboot.auction.domain.entity.AuctionDepositRefund;
import com.lytboot.auction.service.IAuctionDepositRefundService;
import com.lytboot.common.annotation.Log;
import com.lytboot.common.core.controller.BaseController;
import com.lytboot.common.core.domain.AjaxResult;
import com.lytboot.common.core.page.TableDataInfo;
import com.lytboot.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 保证金退款管理Controller
 */
@RestController
@RequestMapping("/auction/refund")
public class AuctionDepositRefundController extends BaseController {

    @Autowired
    private IAuctionDepositRefundService refundService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('auction:refund:list')")
    public TableDataInfo list(AuctionDepositRefund refund) {
        startPage();
        LambdaQueryWrapper<AuctionDepositRefund> wrapper = new LambdaQueryWrapper<>();
        if (refund.getActivityId() != null) {
            wrapper.eq(AuctionDepositRefund::getActivityId, refund.getActivityId());
        }
        wrapper.orderByDesc(AuctionDepositRefund::getCreateTime);
        List<AuctionDepositRefund> list = refundService.list(wrapper);
        return getDataTable(list);
    }

    @GetMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPermi('auction:refund:query')")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(refundService.getById(id));
    }

    @Log(title = "保证金退款确认", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermi('auction:refund:audit')")
    public AjaxResult confirm(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        BigDecimal actualRefundPrice = params.get("actualRefundPrice") != null
                ? new BigDecimal(params.get("actualRefundPrice").toString())
                : null;
        String refundVoucher = (String) params.get("refundVoucher");
        refundService.confirmRefund(id, actualRefundPrice, refundVoucher);
        return AjaxResult.success();
    }
}
