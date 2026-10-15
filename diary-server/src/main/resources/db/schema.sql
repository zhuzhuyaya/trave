-- =============================================================================
--  旅行日记 · 数据库结构
--  MySQL 8.0+ / utf8mb4
--
--  本脚本可重复执行:全部使用 CREATE TABLE IF NOT EXISTS,不会破坏已有数据。
--  需要彻底重置请改用 db/reset.sql。
--
--  设计要点:
--    1. 表名一律用复数,避开 MySQL 关键字。
--    2. 不使用 FOREIGN KEY 约束:引用完整性由应用层保证,索引负责连接性能。
--    3. 逻辑删除统一用 deleted 字段,MyBatis-Plus 全局配置自动追加 deleted = 0。
--    4. 不做计数字段冗余 —— 个人日记的数据量级下,实时聚合比维护一致性更划算。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `travel_diary`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `travel_diary`;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
--  users
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `username`   VARCHAR(32)  NOT NULL                COMMENT '登录名,唯一',
  `email`      VARCHAR(128) NOT NULL                COMMENT '邮箱,唯一',
  `password`   VARCHAR(100) NOT NULL                COMMENT 'BCrypt 哈希,固定 60 字符,留余量',
  `nickname`   VARCHAR(32)  NOT NULL                COMMENT '显示名',
  `avatar`     VARCHAR(255)     NULL                COMMENT '头像地址;为空时前端按昵称生成',
  `bio`        VARCHAR(255)     NULL                COMMENT '个性签名',
  `role`       VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
  `status`     TINYINT      NOT NULL DEFAULT 1      COMMENT '1 正常 / 0 停用',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- -----------------------------------------------------------------------------
--  trips · 行程(把若干篇日记归到一起,相当于一本相册)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trips` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL,
  `title`       VARCHAR(60)  NOT NULL                COMMENT '行程名,如「大理五日」',
  `description` VARCHAR(255)     NULL                COMMENT '一句话简介',
  `cover_photo_id` BIGINT        NULL                COMMENT '封面附件 id;为空时取最新一篇日记的首图',
  `visibility`  VARCHAR(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC / PRIVATE',
  `is_archived` TINYINT      NOT NULL DEFAULT 0      COMMENT '归档后不在列表主区展示',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_trips_user` (`user_id`, `is_archived`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程';

-- -----------------------------------------------------------------------------
--  entries · 日记
--
--  latitude / longitude 允许为 NULL —— 不是每篇日记都有坐标,
--  地图页只取两者都非空的记录。location_name 与坐标解耦,可以只写地名不选地图点。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `entries` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `trip_id`       BIGINT           NULL                COMMENT '所属行程;为空表示未归类',
  `user_id`       BIGINT       NOT NULL,
  `title`         VARCHAR(120) NOT NULL,
  `content`       MEDIUMTEXT   NOT NULL                COMMENT 'Markdown 原文,不存渲染后 HTML',
  `excerpt`       VARCHAR(255)     NULL                COMMENT '剥离标记后的纯文本摘要,列表页用',
  `entry_date`    DATE         NOT NULL                COMMENT '日记所属日期,排序与回忆都靠它',
  `location_name` VARCHAR(80)      NULL                COMMENT '地点名,如「大理 · 洱海」',
  `latitude`      DECIMAL(10,7)    NULL                COMMENT '纬度 -90 ~ 90',
  `longitude`     DECIMAL(10,7)    NULL                COMMENT '经度 -180 ~ 180',
  `weather`       VARCHAR(16)      NULL                COMMENT '晴 / 多云 / 雨 / 雪 / 阴',
  `mood`          VARCHAR(16)      NULL                COMMENT '心情标签',
  `view_count`    INT          NOT NULL DEFAULT 0,
  `deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_entries_user_date` (`user_id`, `deleted`, `entry_date` DESC, `id` DESC),
  KEY `idx_entries_trip`      (`trip_id`, `deleted`, `entry_date` DESC),
  KEY `idx_entries_geo`       (`user_id`, `deleted`, `entry_date`, `latitude`, `longitude`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记';

-- -----------------------------------------------------------------------------
--  entry_photos · 日记附件
--
--  url 存相对路径(/uploads/2026/10/xxx.jpg),不存绝对地址 ——
--  换域名、换端口都不用改数据。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `entry_photos` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `entry_id`   BIGINT           NULL                COMMENT '上传时先落盘,保存日记时才回填',
  `user_id`    BIGINT      NOT NULL,
  `url`        VARCHAR(255) NOT NULL,
  `thumb_url`  VARCHAR(255)     NULL                COMMENT '本期与 url 同值,留字段备将来做服务端缩放',
  `width`      INT              NULL,
  `height`     INT              NULL,
  `sort_order` INT          NOT NULL DEFAULT 0      COMMENT '越小越靠前',
  `deleted`    TINYINT      NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_entry_photos_entry` (`entry_id`, `deleted`, `sort_order`),
  KEY `idx_entry_photos_user`  (`user_id`, `deleted`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记附件';

-- -----------------------------------------------------------------------------
--  标签
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tags` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(24) NOT NULL,
  `use_count`  INT         NOT NULL DEFAULT 0,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tags_name` (`name`),
  KEY `idx_tags_use` (`use_count` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签';

CREATE TABLE IF NOT EXISTS `entry_tags` (
  `entry_id` BIGINT NOT NULL,
  `tag_id`   BIGINT NOT NULL,
  PRIMARY KEY (`entry_id`, `tag_id`),
  KEY `idx_entry_tags_tag` (`tag_id`, `entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记-标签关联';

-- -----------------------------------------------------------------------------
--  bookmarks · 收藏:唯一索引既防重复,又是幂等判定的依据
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bookmarks` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `entry_id`   BIGINT   NOT NULL,
  `user_id`    BIGINT   NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bookmarks` (`entry_id`, `user_id`),
  KEY `idx_bookmarks_user` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记收藏';
