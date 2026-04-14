package com.FlashSale.Controller;

import com.FlashSale.Entity.Product;
import com.FlashSale.Service.ProductSearchService;
import com.FlashSale.Service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.List;

@RestController
@RequestMapping("api/products")
@CrossOrigin(origins = "http://localhost:5173")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductSearchService productSearchService;

    @GetMapping
    public ResponseEntity<?> listProducts() {
        return ResponseEntity.ok(productService.listProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductDetail(@PathVariable int id) {
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

    @GetMapping("/search")
    public ResponseEntity<?> searchProducts(@RequestParam("q") String keyword,
                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        List<Product> results = productSearchService.search(keyword, size);
        return ResponseEntity.ok(results);
    }
}
