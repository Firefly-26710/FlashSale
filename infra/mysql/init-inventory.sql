CREATE DATABASE IF NOT EXISTS FlashSale
	DEFAULT CHARACTER SET utf8mb4
	DEFAULT COLLATE utf8mb4_unicode_ci;

USE FlashSale;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
	`id` INT NOT NULL AUTO_INCREMENT,
	`username` VARCHAR(255) NOT NULL,
	`password` VARCHAR(255) NOT NULL,
	PRIMARY KEY (`id`),
	UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `product` (
	`id` INT NOT NULL AUTO_INCREMENT,
	`name` VARCHAR(255) NOT NULL,
	`description` VARCHAR(1000) NOT NULL,
	`price` DECIMAL(10,2) NOT NULL,
	`stock` INT NOT NULL,
	`image_url` VARCHAR(512) DEFAULT NULL,
	PRIMARY KEY (`id`),
	UNIQUE KEY `uk_product_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `inventory_outbox` (
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
	KEY `idx_inventory_outbox_status_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `product` (`name`, `description`, `price`, `stock`, `image_url`)
VALUES
	('电竞鼠标', '轻量化电竞鼠标，支持可编程按键和高精度传感器。', 199.00, 120, 'https://picsum.photos/seed/mouse/640/360'),
	('机械键盘', '87键机械键盘，热插拔轴体，支持 RGB 背光。', 369.00, 80, 'https://picsum.photos/seed/keyboard/640/360'),
	('降噪耳机', '无线降噪耳机，支持快充和多设备连接。', 599.00, 60, 'https://picsum.photos/seed/headset/640/360'),
	('4K 显示器', '27英寸 4K IPS 显示器，支持 144Hz 高刷和低蓝光。', 1899.00, 35, 'https://picsum.photos/seed/monitor/640/360'),
	('智能手表', '支持心率监测、GPS 和多种运动模式。', 899.00, 90, 'https://picsum.photos/seed/watch/640/360'),
	('便携音箱', '蓝牙防水音箱，续航 12 小时，户外便携。', 259.00, 110, 'https://picsum.photos/seed/speaker/640/360'),
	('游戏手柄', '双模连接游戏手柄，支持震动反馈与自定义按键。', 329.00, 70, 'https://picsum.photos/seed/gamepad/640/360'),
	('空气炸锅', '6L 大容量空气炸锅，支持多功能烹饪。', 459.00, 55, 'https://picsum.photos/seed/fryer/640/360'),
	('扫地机器人', '智能地图规划，自动回充，支持APP远程控制。', 1299.00, 40, 'https://picsum.photos/seed/robot/640/360'),
	('电动牙刷', '声波震动电动牙刷，支持多档清洁模式。', 199.00, 150, 'https://picsum.photos/seed/toothbrush/640/360')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
