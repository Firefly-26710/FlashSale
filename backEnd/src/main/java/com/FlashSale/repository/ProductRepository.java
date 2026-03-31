package com.FlashSale.Repository;

import com.FlashSale.Entity.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
	@Query("select p.id from Product p")
	List<Integer> findAllIds();

	@Query("select p from Product p where lower(p.name) like lower(concat('%', :keyword, '%')) or lower(p.description) like lower(concat('%', :keyword, '%'))")
	List<Product> searchByKeyword(@Param("keyword") String keyword);

	@Modifying
	@Query("update Product p set p.stock = p.stock - :quantity where p.id = :productId and p.stock >= :quantity")
	int deductStockIfAvailable(@Param("productId") Integer productId, @Param("quantity") Integer quantity);

	@Modifying
	@Query("update Product p set p.stock = p.stock + :quantity where p.id = :productId")
	int increaseStock(@Param("productId") Integer productId, @Param("quantity") Integer quantity);
}
