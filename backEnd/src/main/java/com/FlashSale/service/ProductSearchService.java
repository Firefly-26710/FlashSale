package com.FlashSale.Service;

import com.FlashSale.Entity.Product;
import com.FlashSale.Entity.ProductSearchDocument;
import com.FlashSale.Repository.ProductRepository;
import com.FlashSale.Repository.ProductSearchRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ProductSearchService {

    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchService(ProductRepository productRepository,
                                ProductSearchRepository productSearchRepository,
                                ElasticsearchOperations elasticsearchOperations) {
        this.productRepository = productRepository;
        this.productSearchRepository = productSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    // 应用启动完成后初始化 ES 索引并同步商品数据
    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        try {
            // 启动后检查索引是否存在；不存在则创建索引及映射
            IndexOperations indexOperations = elasticsearchOperations.indexOps(ProductSearchDocument.class);
            if (!indexOperations.exists()) {
                indexOperations.create();
                indexOperations.putMapping(indexOperations.createMapping(ProductSearchDocument.class));
            }

            // 将数据库中的商品全量同步到 ES，保证搜索冷启动可用
            List<Product> products = productRepository.findAll();
            List<ProductSearchDocument> docs = new ArrayList<>(products.size());
            for (Product product : products) {
                docs.add(toDocument(product));
            }
            productSearchRepository.saveAll(docs);
        } catch (Exception ignored) {
            // ES 不可用时降级，不阻塞应用启动
        }
    }

    // 按关键字搜索商品：优先 ES，失败或无命中时回退数据库
    public List<Product> search(String keyword, int size) {
        // 关键字标准化：null/空白统一按空处理
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isEmpty()) {
            return Collections.emptyList();
        }

        // 限制返回条数，避免一次查询返回过大结果集
        int limit = Math.max(1, Math.min(size, 50));
        try {
            List<ProductSearchDocument> docs = productSearchRepository
                    .findTop50ByNameContainingOrDescriptionContaining(normalizedKeyword, normalizedKeyword);
            if (docs.isEmpty()) {
                // ES 命中为空时，回退数据库模糊搜索
                return fallbackFromDatabase(normalizedKeyword, limit);
            }

            List<Product> products = new ArrayList<>();
            for (int i = 0; i < docs.size() && i < limit; i++) {
                products.add(toProduct(docs.get(i)));
            }
            return products;
        } catch (Exception exception) {
            // ES 查询异常时，回退数据库兜底，保证接口可用
            return fallbackFromDatabase(normalizedKeyword, limit);
        }
    }

    // 数据库兜底搜索，并按 limit 截断结果
    private List<Product> fallbackFromDatabase(String keyword, int limit) {
        List<Product> fallbackResults = productRepository.searchByKeyword(keyword);
        if (fallbackResults.size() <= limit) {
            return fallbackResults;
        }
        return fallbackResults.subList(0, limit);
    }

    // 将数据库商品实体转换为 ES 文档
    private ProductSearchDocument toDocument(Product product) {
        ProductSearchDocument doc = new ProductSearchDocument();
        doc.setId(product.getId());
        doc.setName(product.getName());
        doc.setDescription(product.getDescription());
        doc.setPrice(product.getPrice());
        doc.setStock(product.getStock());
        doc.setImageUrl(product.getImageUrl());
        return doc;
    }

    // 将 ES 文档转换为接口返回使用的商品实体
    private Product toProduct(ProductSearchDocument doc) {
        Product product = new Product();
        product.setId(doc.getId() == null ? 0 : doc.getId());
        product.setName(doc.getName());
        product.setDescription(doc.getDescription());
        product.setPrice(doc.getPrice());
        product.setStock(doc.getStock() == null ? 0 : doc.getStock());
        product.setImageUrl(doc.getImageUrl());
        return product;
    }
}
