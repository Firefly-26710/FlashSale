package com.FlashSale.Service;

import com.FlashSale.Entity.Product;
import com.FlashSale.Repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Cacheable(cacheNames = "product:list")
    public List<Product> listProducts() {
        return productRepository.findAll();
    }

    @Cacheable(cacheNames = "product:detail", key = "#id")
    public Optional<Product> getProductById(int id) {
        return productRepository.findById(id);
    }
}
