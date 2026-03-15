CREATE DATABASE IF NOT EXISTS FlashSale
	DEFAULT CHARACTER SET utf8mb4
	DEFAULT COLLATE utf8mb4_unicode_ci;

USE FlashSale;

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

INSERT INTO `product` (`name`, `description`, `price`, `stock`, `image_url`)
VALUES
	('电竞鼠标', '轻量化电竞鼠标，支持可编程按键和高精度传感器。', 199.00, 120, 'https://picsum.photos/seed/mouse/640/360'),
	('机械键盘', '87键机械键盘，热插拔轴体，支持 RGB 背光。', 369.00, 80, 'https://picsum.photos/seed/keyboard/640/360'),
	('降噪耳机', '无线降噪耳机，支持快充和多设备连接。', 599.00, 60, 'https://picsum.photos/seed/headset/640/360')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
