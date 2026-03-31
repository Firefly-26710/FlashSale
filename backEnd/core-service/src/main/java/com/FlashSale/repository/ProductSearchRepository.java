package com.FlashSale.Repository;

import com.FlashSale.Entity.ProductSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSearchRepository extends ElasticsearchRepository<ProductSearchDocument, Integer> {

    List<ProductSearchDocument> findTop50ByNameContainingOrDescriptionContaining(String name, String description);
}
