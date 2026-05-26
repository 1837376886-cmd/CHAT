package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.ProductItem;
import org.apache.ibatis.annotations.Param;

/**
 * 商品Mapper接口
 */
public interface ProductItemMapper extends BaseMapper<ProductItem> {

    ProductItem selectByProductCode(@Param("productCode") String productCode);
}
