package com.FlashSale.Service;

import com.FlashSale.Entity.Order;
import com.FlashSale.Repository.OrderRepository;
import com.FlashSale.Repository.ProductRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
// 秒杀订单消费者：消费Kafka消息并在数据库中落单。
public class SeckillOrderConsumer {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product:detail:";
    private static final String PRODUCT_LIST_KEY = "product:list:all";
    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("PENDING_PAYMENT", "PAID", "PAY_FAILED");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    public SeckillOrderConsumer(OrderRepository orderRepository,
                                ProductRepository productRepository,
                                StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
    }

    // Kafka消费订单创建消息。
    // 通过事务保证“扣减数据库库存 + 保存订单”原子性。
    @Transactional
    @KafkaListener(topics = "${flashsale.seckill.kafka.topic:flashsale.order.create}", groupId = "${flashsale.seckill.kafka.group:flashsale-order-consumer}")
    public void consumeOrderCreate(SeckillOrderMessage message) {
        if (message == null || message.getOrderId() == null) {
            return;
        }

        if (orderRepository.existsById(message.getOrderId())) {
            return;
        }

        if (orderRepository.existsByUserIdAndProductIdAndStatusIn(message.getUserId(), message.getProductId(), ACTIVE_ORDER_STATUSES)) {
            // 数据库已存在订单，说明本次消息是重复消费或Redis状态丢失，补回一次Redis库存。
            compensateRedisStock(message.getProductId());
            return;
        }

        int updatedRows = productRepository.deductStockIfAvailable(message.getProductId(), message.getQuantity());
        if (updatedRows <= 0) {
            // Redis与DB库存短暂不一致时，回补Redis库存，避免库存越扣越少。
            compensateRedisStock(message.getProductId());
            return;
        }

        Order order = new Order();
        order.setId(message.getOrderId());
        order.setUserId(message.getUserId());
        order.setProductId(message.getProductId());
        order.setQuantity(message.getQuantity());
        order.setAmount(message.getAmount());
        order.setStatus("PENDING_PAYMENT");

        orderRepository.save(order);

        // 订单创建成功后清理商品缓存，确保前端尽快看到库存变化。
        clearProductCache(message.getProductId());
    }

    // Redis库存补偿：用于消费端发现重复单或DB扣减失败时恢复库存。
    private void compensateRedisStock(Integer productId) {
        redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + productId);
    }

    // 清理商品详情和列表缓存，触发后续请求回源刷新。
    private void clearProductCache(Integer productId) {
        redisTemplate.delete(PRODUCT_DETAIL_KEY_PREFIX + productId);
        redisTemplate.delete(PRODUCT_LIST_KEY);
    }
}
