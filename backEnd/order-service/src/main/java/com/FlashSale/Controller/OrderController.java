package com.FlashSale.Controller;

import com.FlashSale.Entity.Order;
import com.FlashSale.Service.SeckillOrderService;
import com.FlashSale.Util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("api/orders")
@CrossOrigin(origins = "http://localhost:5173")
// 订单控制器：提供秒杀下单、按订单ID查询、按用户查询。
public class OrderController {

    private final SeckillOrderService seckillOrderService;

    public OrderController(SeckillOrderService seckillOrderService) {
        this.seckillOrderService = seckillOrderService;
    }

    // 秒杀下单接口（异步创建订单）。
    @PostMapping("/seckill/{productId}")
    public ResponseEntity<?> seckill(@PathVariable Integer productId,
                                     @RequestHeader("Authorization") String authorization) {
        try {
            Integer userId = JwtUtil.validateToken(authorization.replace("Bearer ", ""));
            Map<String, Object> result = seckillOrderService.createSeckillOrder(userId, productId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.accepted().body(result);
            }
            return ResponseEntity.badRequest().body(result);
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户身份无效"));
        }
    }

    // 按订单ID查询。
    @GetMapping("/{orderId}")
    public ResponseEntity<?> queryByOrderId(@PathVariable Long orderId,
                                            @RequestHeader("Authorization") String authorization) {
        try {
            Integer userId = JwtUtil.validateToken(authorization.replace("Bearer ", ""));
            Optional<Order> orderOpt = seckillOrderService.queryByOrderId(orderId);
            if (orderOpt.isEmpty()) {
                Optional<Map<String, Object>> statusOpt = seckillOrderService.queryOrderStatus(orderId, userId);
                if (statusOpt.isPresent()) {
                    Map<String, Object> status = statusOpt.get();
                    if ("FORBIDDEN".equals(status.get("status"))) {
                        return ResponseEntity.status(403).body(Map.of("message", status.get("message")));
                    }
                    return ResponseEntity.ok(status);
                }
                return ResponseEntity.status(404).body(Map.of("message", "订单不存在"));
            }
            if (!userId.equals(orderOpt.get().getUserId())) {
                return ResponseEntity.status(403).body(Map.of("message", "无权限查看该订单"));
            }
            return ResponseEntity.ok(orderOpt.get());
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户身份无效"));
        }
    }

    // 按用户ID查询订单（仅允许查询当前登录用户）。
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> queryByUserId(@PathVariable Integer userId,
                                           @RequestHeader("Authorization") String authorization) {
        try {
            Integer loginUserId = JwtUtil.validateToken(authorization.replace("Bearer ", ""));
            if (!loginUserId.equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("message", "无权限查看其他用户订单"));
            }
            List<Order> orders = seckillOrderService.queryByUserId(userId);
            return ResponseEntity.ok(orders);
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(Map.of("message", "用户身份无效"));
        }
    }

    // 模拟支付接口：只发起支付请求消息，不直接改订单状态。
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<?> payOrder(@PathVariable Long orderId,
                                      @RequestHeader("Authorization") String authorization) {
        try {
            Integer userId = JwtUtil.validateToken(authorization.replace("Bearer ", ""));
            Map<String, Object> result = seckillOrderService.requestPayment(orderId, userId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.accepted().body(result);
            }
            return ResponseEntity.badRequest().body(result);
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户身份无效"));
        }
    }

    // 取消待支付订单并回补库存。
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId,
                                         @RequestHeader("Authorization") String authorization) {
        try {
            Integer userId = JwtUtil.validateToken(authorization.replace("Bearer ", ""));
            Map<String, Object> result = seckillOrderService.cancelPendingOrder(orderId, userId);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.badRequest().body(result);
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "用户身份无效"));
        }
    }
}
