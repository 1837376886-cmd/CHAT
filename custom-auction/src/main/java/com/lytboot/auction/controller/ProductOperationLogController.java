package com.lytboot.auction.controller;

import com.lytboot.auction.domain.entity.ProductOperationLog;
import com.lytboot.auction.service.IProductOperationLogService;
import com.lytboot.common.core.controller.BaseController;
import com.lytboot.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品操作记录Controller
 */
@RestController
@RequestMapping("/auction/product/log")
public class ProductOperationLogController extends BaseController {

    @Autowired
    private IProductOperationLogService operationLogService;

    @GetMapping("/list/{productId}")
    @PreAuthorize("@ss.hasPermi('auction:product:query')")
    public AjaxResult list(@PathVariable("productId") Long productId) {
        List<ProductOperationLog> list = operationLogService.selectByProductId(productId);
        return AjaxResult.success(list);
    }
}
