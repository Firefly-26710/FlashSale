package com.FlashSale.Service;

import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
// 雪花ID生成器：用于生成全局唯一订单ID。
public class SnowflakeIdService {

    private static final long EPOCH = 1704067200000L;
    private static final long WORKER_ID = 1L;
    private static final long DATACENTER_ID = 1L;

    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;

    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    // 生成全局唯一订单ID。
    public synchronized long nextId() {
        long timestamp = currentMillis();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("系统时钟回拨，无法生成订单ID");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitUntilNextMillis(lastTimestamp);
                // 跨毫秒后重新设置起始序列，避免ID长期偏偶数。
                sequence = initialSequenceForTimestamp(timestamp);
            }
        } else {
            // 每个新毫秒按时间奇偶设置初始序列，提升ID低位奇偶分布均衡性。
            sequence = initialSequenceForTimestamp(timestamp);
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (DATACENTER_ID << DATACENTER_ID_SHIFT)
                | (WORKER_ID << WORKER_ID_SHIFT)
                | sequence;
    }

    // 自旋等待到下一个毫秒，避免序列号溢出冲突。
    private long waitUntilNextMillis(long lastTimestamp) {
        long timestamp = currentMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentMillis();
        }
        return timestamp;
    }

    // 统一获取当前毫秒时间戳。
    private long currentMillis() {
        return Instant.now().toEpochMilli();
    }

    // 根据毫秒时间戳设置初始序列（0或1），用于改善ID奇偶分布。
    private long initialSequenceForTimestamp(long timestamp) {
        return timestamp & 1L;
    }
}
