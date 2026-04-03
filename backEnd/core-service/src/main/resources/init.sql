CREATE DATABASE IF NOT EXISTS FlashSale
	DEFAULT CHARACTER SET utf8mb4
	DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS FlashSale_ds1
	DEFAULT CHARACTER SET utf8mb4
	DEFAULT COLLATE utf8mb4_unicode_ci;

USE FlashSale;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `orders` (
	`id` BIGINT NOT NULL,
	`user_id` INT NOT NULL,
	`product_id` INT NOT NULL,
	`quantity` INT NOT NULL,
	`amount` DECIMAL(10,2) NOT NULL,
	`status` VARCHAR(32) NOT NULL,
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	KEY `idx_orders_user_product` (`user_id`, `product_id`),
	KEY `idx_orders_user_id` (`user_id`),
	KEY `idx_orders_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `order_outbox` (
	`id` BIGINT NOT NULL AUTO_INCREMENT,
	`message_key` VARCHAR(64) DEFAULT NULL,
	`topic` VARCHAR(255) NOT NULL,
	`payload` TEXT NOT NULL,
	`payload_type` VARCHAR(255) NOT NULL,
	`status` VARCHAR(16) NOT NULL,
	`retry_count` INT NOT NULL DEFAULT 0,
	`next_retry_at` DATETIME DEFAULT NULL,
	`sent_at` DATETIME DEFAULT NULL,
	`last_error` VARCHAR(512) DEFAULT NULL,
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	KEY `idx_order_outbox_status_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分片物理表（ds0: FlashSale）
CREATE TABLE IF NOT EXISTS `orders_0` (
	`id` BIGINT NOT NULL,
	`user_id` INT NOT NULL,
	`product_id` INT NOT NULL,
	`quantity` INT NOT NULL,
	`amount` DECIMAL(10,2) NOT NULL,
	`status` VARCHAR(32) NOT NULL,
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	KEY `idx_orders_0_user_product` (`user_id`, `product_id`),
	KEY `idx_orders_0_user_id` (`user_id`),
	KEY `idx_orders_0_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `orders_1` (
	`id` BIGINT NOT NULL,
	`user_id` INT NOT NULL,
	`product_id` INT NOT NULL,
	`quantity` INT NOT NULL,
	`amount` DECIMAL(10,2) NOT NULL,
	`status` VARCHAR(32) NOT NULL,
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	KEY `idx_orders_1_user_product` (`user_id`, `product_id`),
	KEY `idx_orders_1_user_id` (`user_id`),
	KEY `idx_orders_1_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

USE FlashSale_ds1;

-- 分片物理表（ds1: FlashSale_ds1）
CREATE TABLE IF NOT EXISTS `orders_0` (
	`id` BIGINT NOT NULL,
	`user_id` INT NOT NULL,
	`product_id` INT NOT NULL,
	`quantity` INT NOT NULL,
	`amount` DECIMAL(10,2) NOT NULL,
	`status` VARCHAR(32) NOT NULL,
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	KEY `idx_orders_0_user_product` (`user_id`, `product_id`),
	KEY `idx_orders_0_user_id` (`user_id`),
	KEY `idx_orders_0_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `orders_1` (
	`id` BIGINT NOT NULL,
	`user_id` INT NOT NULL,
	`product_id` INT NOT NULL,
	`quantity` INT NOT NULL,
	`amount` DECIMAL(10,2) NOT NULL,
	`status` VARCHAR(32) NOT NULL,
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	KEY `idx_orders_1_user_product` (`user_id`, `product_id`),
	KEY `idx_orders_1_user_id` (`user_id`),
	KEY `idx_orders_1_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

