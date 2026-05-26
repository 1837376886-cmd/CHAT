package com.lytboot.auction.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lytboot.auction.domain.entity.ProductItem;

import java.util.List;

/**
 * 商品Service接口
 */
public interface IProductItemService extends IService<ProductItem> {

    /**
     * 新增商品（含内容）
     */
    void addProduct(ProductItem product, String content);

    /**
     * 修改商品（含内容）
     */
    void updateProduct(ProductItem product, String content);

    /**
     * 根据编码查询
     */
    ProductItem selectByProductCode(String productCode);
}
