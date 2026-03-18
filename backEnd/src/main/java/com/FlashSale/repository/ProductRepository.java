package com.FlashSale.Repository;

import com.FlashSale.Entity.Product;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
	@Query("select p.id from Product p")
	List<Integer> findAllIds();
}
