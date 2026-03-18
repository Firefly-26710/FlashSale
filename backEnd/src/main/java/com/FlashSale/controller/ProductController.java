package com.FlashSale.Controller;

import com.FlashSale.Entity.Product;
import com.FlashSale.Service.ApiRateLimitService;
import com.FlashSale.Service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("api/products")
@CrossOrigin(origins = "http://localhost:5173")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ApiRateLimitService apiRateLimitService;

    @GetMapping
    public ResponseEntity<?> listProducts() {
        // 限流降级：高峰时快速失败，保护后端与数据库
        if (!apiRateLimitService.allowProductListRequest()) {
            return ResponseEntity.status(429).body(Map.of("message", "请求过于频繁，请稍后再试"));
        }
        return ResponseEntity.ok(productService.listProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductDetail(@PathVariable int id) {
        // 限流
        if (!apiRateLimitService.allowProductDetailRequest()) {
            return ResponseEntity.status(429).body(Map.of("message", "请求过于频繁，请稍后再试"));
        }

        // 参数校验：提前拦截非法 ID，减少无效流量
        if (id <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "商品ID必须大于0"));
        }

        Optional<Product> product = productService.getProductById(id);
        if (product.isPresent()) {
            return ResponseEntity.ok(product.get());
        }
        return ResponseEntity.status(404).body(Map.of("message", "商品不存在"));
    }
}
