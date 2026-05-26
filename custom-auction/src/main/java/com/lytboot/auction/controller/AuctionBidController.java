package com.lytboot.auction.controller;

import com.lytboot.auction.domain.entity.AuctionBid;
import com.lytboot.auction.service.IAuctionActivityService;
import com.lytboot.auction.service.IAuctionBidService;
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

/**
 * 出价管理Controller
 */
@RestController
@RequestMapping("/auction/bid")
public class AuctionBidController extends BaseController {

    @Autowired
    private IAuctionBidService bidService;

    @Autowired
    private IAuctionActivityService activityService;

    @GetMapping("/list/{activityId}")
    @PreAuthorize("@ss.hasPermi('auction:bid:list')")
    public AjaxResult list(@PathVariable("activityId") Long activityId) {
        List<AuctionBid> list = bidService.selectByActivityId(activityId);
        for (AuctionBid bid : list) {
            bid.setUserId(null);
            bid.setRealName(null);
        }
        return AjaxResult.success(list);
    }

    @Log(title = "出价", businessType = BusinessType.INSERT)
    @PostMapping
    @PreAuthorize("@ss.hasPermi('auction:bid:add')")
    public AjaxResult add(@RequestBody AuctionBid bid) {
        Long userId = getUserId();
        activityService.placeBid(bid.getActivityId(), userId, getUsername(), bid.getBidPrice());
        return AjaxResult.success();
    }

    @GetMapping("/highest/{activityId}")
    @PreAuthorize("@ss.hasPermi('auction:bid:query')")
    public AjaxResult highest(@PathVariable("activityId") Long activityId) {
        AuctionBid bid = bidService.selectHighestBid(activityId);
        if (bid != null) {
            bid.setUserId(null);
            bid.setRealName(null);
        }
        return AjaxResult.success(bid);
    }
}
