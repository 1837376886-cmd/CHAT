package com.lytboot.auction.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lytboot.auction.domain.entity.ProductContent;
import com.lytboot.auction.domain.entity.ProductItem;
import com.lytboot.auction.enums.ProductOperationTypeEnum;
import com.lytboot.auction.mapper.ProductContentMapper;
import com.lytboot.auction.mapper.ProductItemMapper;
import com.lytboot.auction.service.IProductItemService;
import com.lytboot.auction.service.IProductOperationLogService;
import com.lytboot.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 商品Service实现
 */
@Service
public class ProductItemServiceImpl extends ServiceImpl<ProductItemMapper, ProductItem> implements IProductItemService {

    @Autowired
    private ProductItemMapper productItemMapper;

    @Autowired
    private ProductContentMapper productContentMapper;

    @Autowired
    private IProductOperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProduct(ProductItem product, String content) {
        product.setProductCode(generateProductCode());
        productItemMapper.insert(product);

        if (content != null && !content.isEmpty()) {
            ProductContent pc = new ProductContent();
            pc.setProductId(product.getId());
            pc.setContent(content);
            productContentMapper.insert(pc);
        }

        operationLogService.addLog(product.getId(), ProductOperationTypeEnum.SUBMIT.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(ProductItem product, String content) {
        productItemMapper.updateById(product);

        if (content != null) {
            ProductContent exist = productContentMapper.selectByProductId(product.getId());
            if (exist != null) {
                exist.setContent(content);
                productContentMapper.updateById(exist);
            } else {
                ProductContent pc = new ProductContent();
                pc.setProductId(product.getId());
                pc.setContent(content);
                productContentMapper.insert(pc);
            }
        }
    }

    @Override
    public ProductItem selectByProductCode(String productCode) {
        return productItemMapper.selectByProductCode(productCode);
    }

    private String generateProductCode() {
        return "P" + DateUtils.dateTimeNow("yyyyMMddHHmmss") + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
