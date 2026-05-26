package com.lytboot.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lytboot.auction.domain.entity.AuctionSurvey;
import com.lytboot.auction.service.IAuctionSurveyService;
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
 * 踏勘管理Controller
 */
@RestController
@RequestMapping("/auction/survey")
public class AuctionSurveyController extends BaseController {

    @Autowired
    private IAuctionSurveyService surveyService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('auction:survey:list')")
    public TableDataInfo list(AuctionSurvey survey) {
        startPage();
        LambdaQueryWrapper<AuctionSurvey> wrapper = new LambdaQueryWrapper<>();
        if (survey.getActivityId() != null) {
            wrapper.eq(AuctionSurvey::getActivityId, survey.getActivityId());
        }
        if (survey.getSurveyStatus() != null && !survey.getSurveyStatus().isEmpty()) {
            wrapper.eq(AuctionSurvey::getSurveyStatus, survey.getSurveyStatus());
        }
        wrapper.orderByDesc(AuctionSurvey::getCreateTime);
        List<AuctionSurvey> list = surveyService.list(wrapper);
        return getDataTable(list);
    }

    @GetMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPermi('auction:survey:query')")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(surveyService.getById(id));
    }

    @Log(title = "踏勘确认", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermi('auction:survey:edit')")
    public AjaxResult confirm(@RequestBody AuctionSurvey survey) {
        surveyService.confirmSurvey(survey.getId(), survey.getSurveyStatus(), survey.getSurveyFile(), survey.getRemark());
        return AjaxResult.success();
    }
}
