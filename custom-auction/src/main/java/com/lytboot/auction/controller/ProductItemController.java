package com.lytboot.auction.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lytboot.auction.domain.entity.ProductContent;
import com.lytboot.auction.domain.entity.ProductItem;
import com.lytboot.auction.mapper.ProductContentMapper;
import com.lytboot.auction.service.IProductItemService;
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
 * 商品管理Controller
 */
@RestController
@RequestMapping("/auction/product")
public class ProductItemController extends BaseController {

    @Autowired
    private IProductItemService productItemService;

    @Autowired
    private ProductContentMapper productContentMapper;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('auction:product:list')")
    public TableDataInfo list(ProductItem productItem) {
        startPage();
        LambdaQueryWrapper<ProductItem> wrapper = new LambdaQueryWrapper<>();
        if (productItem.getProductName() != null && !productItem.getProductName().isEmpty()) {
            wrapper.like(ProductItem::getProductName, productItem.getProductName());
        }
        wrapper.orderByDesc(ProductItem::getCreateTime);
        List<ProductItem> list = productItemService.list(wrapper);
        return getDataTable(list);
    }

    @GetMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPermi('auction:product:query')")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        ProductItem item = productItemService.getById(id);
        AjaxResult result = AjaxResult.success(item);
        ProductContent content = productContentMapper.selectByProductId(id);
        if (content != null) {
            result.put("content", content.getContent());
        }
        return result;
    }

    @Log(title = "商品管理", businessType = BusinessType.INSERT)
    @PostMapping
    @PreAuthorize("@ss.hasPermi('auction:product:add')")
    public AjaxResult add(@RequestBody ProductItem productItem) {
        String content = productItem.getRemark();
        productItemService.addProduct(productItem, content);
        return AjaxResult.success();
    }

    @Log(title = "商品管理", businessType = BusinessType.UPDATE)
    @PutMapping
    @PreAuthorize("@ss.hasPermi('auction:product:edit')")
    public AjaxResult edit(@RequestBody ProductItem productItem) {
        String content = productItem.getRemark();
        productItemService.updateProduct(productItem, content);
        return AjaxResult.success();
    }

    @Log(title = "商品管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPermi('auction:product:remove')")
    public AjaxResult remove(@PathVariable Long[] ids) {
        productItemService.removeByIds(Arrays.asList(ids));
        return AjaxResult.success();
    }

}
