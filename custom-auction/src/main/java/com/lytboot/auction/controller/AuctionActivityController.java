package com.lytboot.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lytboot.auction.domain.entity.AuctionActivity;
import com.lytboot.auction.domain.entity.AuctionMessage;
import com.lytboot.auction.service.IAuctionActivityService;
import com.lytboot.auction.service.IAuctionMessageService;
import com.lytboot.common.annotation.Log;
import com.lytboot.common.core.controller.BaseController;
import com.lytboot.common.core.domain.AjaxResult;
import com.lytboot.common.core.page.TableDataInfo;
import com.lytboot.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 拍卖活动管理Controller
 */
@RestController
@RequestMapping("/auction/activity")
public class AuctionActivityController extends BaseController {

    @Autowired
    private IAuctionActivityService activityService;

    @Autowired
    private IAuctionMessageService messageService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('auction:activity:list')")
    public TableDataInfo list(AuctionActivity activity) {
        startPage();
        LambdaQueryWrapper<AuctionActivity> wrapper = new LambdaQueryWrapper<>();
        if (activity.getActivityName() != null && !activity.getActivityName().isEmpty()) {
            wrapper.like(AuctionActivity::getActivityName, activity.getActivityName());
        }
        if (activity.getStatus() != null && !activity.getStatus().isEmpty()) {
            wrapper.eq(AuctionActivity::getStatus, activity.getStatus());
        }
        if (activity.getAuditStatus() != null && !activity.getAuditStatus().isEmpty()) {
            wrapper.eq(AuctionActivity::getAuditStatus, activity.getAuditStatus());
        }
        wrapper.orderByDesc(AuctionActivity::getCreateTime);
        List<AuctionActivity> list = activityService.list(wrapper);
        return getDataTable(list);
    }

    @GetMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPermi('auction:activity:query')")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(activityService.getById(id));
    }

    @Log(title = "发布拍卖活动", businessType = BusinessType.INSERT)
    @PostMapping
    @PreAuthorize("@ss.hasPermi('auction:activity:add')")
    public AjaxResult add(@RequestBody AuctionActivity activity) {
        activityService.publishActivity(activity);
        return AjaxResult.success();
    }

    @Log(title = "编辑拍卖活动", businessType = BusinessType.UPDATE)
    @PutMapping
    @PreAuthorize("@ss.hasPermi('auction:activity:edit')")
    public AjaxResult edit(@RequestBody AuctionActivity activity) {
        activityService.updateById(activity);
        return AjaxResult.success();
    }

    @Log(title = "删除拍卖活动", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('auction:activity:remove')")
    public AjaxResult remove(@PathVariable Long[] ids) {
        activityService.removeByIds(Arrays.asList(ids));
        return AjaxResult.success();
    }

    @Log(title = "审核拍卖活动", businessType = BusinessType.UPDATE)
    @PutMapping("/audit")
    @PreAuthorize("@ss.hasPermi('auction:activity:audit')")
    public AjaxResult audit(@RequestBody AuctionActivity activity) {
        activityService.auditActivity(activity.getId(), activity.getAuditStatus(), activity.getAuditRemark(), getUserId());
        return AjaxResult.success();
    }

    @Log(title = "取消拍卖活动", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{id}")
    @PreAuthorize("@ss.hasPermi('auction:activity:edit')")
    public AjaxResult cancel(@PathVariable("id") Long id) {
        activityService.cancelActivity(id);
        return AjaxResult.success();
    }

    @GetMapping("/message/{activityId}")
    @PreAuthorize("@ss.hasPermi('auction:activity:query')")
    public AjaxResult messages(@PathVariable("activityId") Long activityId) {
        List<AuctionMessage> list = messageService.selectByActivityId(activityId);
        return AjaxResult.success(list);
    }
}
