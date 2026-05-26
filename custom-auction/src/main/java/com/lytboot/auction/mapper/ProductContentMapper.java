package com.lytboot.auction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lytboot.auction.domain.entity.ProductContent;
import org.apache.ibatis.annotations.Param;

/**
 * 商品内容详情Mapper接口
 */
public interface ProductContentMapper extends BaseMapper<ProductContent> {

    ProductContent selectByProductId(@Param("productId") Long productId);
}
