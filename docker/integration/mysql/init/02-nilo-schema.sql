/*
 Navicat Premium Dump SQL

 Source Server         : rocky16-MySQL8.4.7
 Source Server Type    : MySQL
 Source Server Version : 80407 (8.4.7)
 Source Host           : 192.168.6.16:3306
 Source Schema         : nilo

 Target Server Type    : MySQL
 Target Server Version : 80407 (8.4.7)
 File Encoding         : 65001

 Date: 27/09/2026 22:01:38
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for category_info
-- ----------------------------
DROP TABLE IF EXISTS `category_info`;
CREATE TABLE `category_info`  (
  `category_id` int NOT NULL AUTO_INCREMENT COMMENT '自增分类ID',
  `category_number` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类编号',
  `category_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类名称',
  `p_category_id` int NOT NULL COMMENT '父级分类ID',
  `icon` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '图标',
  `background` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '背景图',
  `sort` tinyint NOT NULL COMMENT '排序号',
  `color` varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主题色',
  PRIMARY KEY (`category_id`) USING BTREE,
  UNIQUE INDEX `idx_qk_category_number`(`category_number` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 60 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分类信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for follow_info
-- ----------------------------
DROP TABLE IF EXISTS `follow_info`;
CREATE TABLE `follow_info`  (
  `follower_user_id` bigint NOT NULL COMMENT '关注者用户ID',
  `following_user_id` bigint NOT NULL COMMENT '被关注人用户ID',
  `follow_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`follower_user_id`, `following_user_id`) USING BTREE,
  INDEX `idx_time_following`(`follow_time` ASC, `following_user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for media_ownership
-- ----------------------------
DROP TABLE IF EXISTS `media_ownership`;
CREATE TABLE `media_ownership`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '排序序号，自增主键',
  `object_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'minio key（不保存前缀）',
  `bucket` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'minio中的bucket',
  `owner_id` bigint NOT NULL COMMENT '所有者用户ID',
  `used` tinyint NOT NULL DEFAULT 0 COMMENT '0表示已经用过，1表示尚未用过',
  `created_time` datetime NOT NULL COMMENT '创建时间',
  `used_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_key`(`object_key` ASC) USING BTREE COMMENT '索引必须有唯一性',
  INDEX `idx_owner_bucket_id`(`owner_id` ASC, `bucket` ASC, `id` ASC) USING BTREE COMMENT '方便根据用户id查询记录1'
) ENGINE = InnoDB AUTO_INCREMENT = 286 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for statistics_info
-- ----------------------------
DROP TABLE IF EXISTS `statistics_info`;
CREATE TABLE `statistics_info`  (
  `statistics_date` date NOT NULL COMMENT '统计日期',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `data_type` tinyint NOT NULL COMMENT '数据统计类型',
  `statistics_count` int NULL DEFAULT NULL COMMENT '统计数量',
  PRIMARY KEY (`statistics_date`, `user_id`, `data_type`) USING BTREE,
  INDEX `idx_user_date_type`(`user_id` ASC, `statistics_date` ASC, `data_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据统计' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for undo_log
-- ----------------------------
DROP TABLE IF EXISTS `undo_log`;
CREATE TABLE `undo_log`  (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE INDEX `ux_undo_log`(`xid` ASC, `branch_id` ASC) USING BTREE,
  INDEX `ix_log_created`(`log_created` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'AT transaction mode undo table' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for upload_quota_state
-- ----------------------------
DROP TABLE IF EXISTS `upload_quota_state`;
CREATE TABLE `upload_quota_state`  (
  `upload_id` bigint NOT NULL COMMENT '视频上传聚合ID，聚合同一视频下的多个完整文件',
  `user_id` bigint NOT NULL COMMENT '所属用户ID，用于越权校验',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CHARGED/BIZ_READY/QUOTA_EXCEEDED/DONE/VOID',
  `total_count` int NOT NULL COMMENT '该视频对应的完整文件总数',
  `issued_count` int NOT NULL DEFAULT 0 COMMENT '目前已经请求presigned url的资源数',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '已确认上传并计入配额的文件数',
  `video_bytes` bigint NOT NULL DEFAULT 0 COMMENT '视频已累加的真实字节数，来自MinIO notification',
  `cover_bytes` bigint NOT NULL DEFAULT 0 COMMENT '封面占用字节数，如果本次修改未更改封面，此项为0',
  `processed_keys` json NOT NULL COMMENT '已计入success_count的object key列表，用于notification去重',
  `fail_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '终结为VOID时的原因，如QUOTA_EXCEEDED导致的VOID，便于对账排查',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '默认为now',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '默认为now，自动更新',
  PRIMARY KEY (`upload_id`) USING BTREE,
  INDEX `idx_user_status`(`user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_status_updated`(`status` ASC, `updated_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '【！已弃用！】视频上传配额状态机：聚合单个视频下多个文件的配额扣减与业务处理状态' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_info
-- ----------------------------
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info`  (
  `user_id` bigint NOT NULL COMMENT '用户id',
  `nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '昵称',
  `email` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '邮箱',
  `password` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码',
  `gender` tinyint(1) NULL DEFAULT NULL COMMENT '0:女 1:男 2:未知',
  `birthday` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '出生日期',
  `school` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '学校',
  `personal_introduction` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '个人简介',
  `register_time` datetime NOT NULL COMMENT '加入时间',
  `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最后登录IP',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '0:禁用 1:正常',
  `notice_info` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '空间公告',
  `current_coin` int NOT NULL DEFAULT 10 COMMENT '当前硬币数（只能是正数）',
  `theme` tinyint NOT NULL DEFAULT 1 COMMENT '主题',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `idx_uk_email`(`email` ASC) USING BTREE,
  UNIQUE INDEX `idx_uk_nick_name`(`nick_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_message
-- ----------------------------
DROP TABLE IF EXISTS `user_message`;
CREATE TABLE `user_message`  (
  `message_id` bigint NOT NULL COMMENT '消息ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `video_id` bigint NULL DEFAULT NULL COMMENT '相关视频ID',
  `message_type` tinyint NULL DEFAULT NULL COMMENT '消息类型',
  `sender_user_id` bigint NULL DEFAULT NULL COMMENT '发送者用户ID',
  `read_type` tinyint NULL DEFAULT 0 COMMENT '0:未读 1:已读',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `extend_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '扩展信息（以JSON格式保存）',
  `repeatable_type` tinyint NULL DEFAULT NULL,
  PRIMARY KEY (`message_id`) USING BTREE,
  UNIQUE INDEX `uk_video_action`(`user_id` ASC, `message_type` ASC, `sender_user_id` ASC, `video_id` ASC, `repeatable_type` ASC) USING BTREE,
  INDEX `idx_user_read_type`(`user_id` ASC, `read_type` ASC, `message_type` ASC) USING BTREE,
  INDEX `idx_user_type_time`(`user_id` ASC, `message_type` ASC, `create_time` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户消息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_upload_video_lock
-- ----------------------------
DROP TABLE IF EXISTS `user_upload_video_lock`;
CREATE TABLE `user_upload_video_lock`  (
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用于为用户请求上传视频key的行为加锁' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_video_action
-- ----------------------------
DROP TABLE IF EXISTS `user_video_action`;
CREATE TABLE `user_video_action`  (
  `action_id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `video_user_id` bigint NOT NULL COMMENT '视频用户ID',
  `action_type` tinyint NOT NULL COMMENT '1:视频点赞 2:视频收藏 3:视频投币',
  `coin_amount` tinyint NOT NULL COMMENT '投币数量',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `action_time` datetime NOT NULL COMMENT '操作时间',
  PRIMARY KEY (`action_id`) USING BTREE,
  UNIQUE INDEX `uk_key_video_type_user`(`video_id` ASC, `action_type` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_type`(`action_type` ASC) USING BTREE,
  INDEX `idx_action_time`(`action_time` ASC, `video_user_id` ASC, `action_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 207 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户视频行为 点赞、收藏、投币' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for user_video_action_archive
-- ----------------------------
DROP TABLE IF EXISTS `user_video_action_archive`;
CREATE TABLE `user_video_action_archive`  (
  `action_id` bigint NOT NULL COMMENT '自增ID',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `video_user_id` bigint NOT NULL COMMENT '视频用户ID',
  `action_type` tinyint NOT NULL COMMENT '1:视频点赞 2:视频收藏 3:视频投币',
  `coin_amount` tinyint NOT NULL COMMENT '投币数量',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `action_time` datetime NOT NULL COMMENT '操作时间',
  PRIMARY KEY (`action_id`) USING BTREE,
  INDEX `idx_video`(`video_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户视频行为 点赞、收藏、投币' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_danmaku
-- ----------------------------
DROP TABLE IF EXISTS `video_danmaku`;
CREATE TABLE `video_danmaku`  (
  `danmaku_id` bigint NOT NULL COMMENT '弹幕ID【对外展示】',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `file_id` bigint NOT NULL COMMENT '视频文件ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_time` datetime NOT NULL COMMENT '发布时间',
  `content` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '内容',
  `position` tinyint NULL DEFAULT NULL COMMENT '展示位置',
  `color` varchar(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '颜色(HEX+不透明度)',
  `display_moment` int NOT NULL COMMENT '展示时刻（单位：毫秒）',
  PRIMARY KEY (`danmaku_id`) USING BTREE,
  INDEX `idx_file_moment_danmaku`(`file_id` ASC, `display_moment` ASC, `danmaku_id` ASC) USING BTREE,
  INDEX `idx_post_video`(`post_time` ASC, `video_id` ASC) USING BTREE,
  INDEX `idx_video_file_display`(`video_id` ASC, `file_id` ASC, `display_moment` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频弹幕' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_danmaku_archive
-- ----------------------------
DROP TABLE IF EXISTS `video_danmaku_archive`;
CREATE TABLE `video_danmaku_archive`  (
  `danmaku_id` bigint NOT NULL COMMENT '弹幕ID【对外展示】',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `file_id` bigint NOT NULL COMMENT '视频文件ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_time` datetime NOT NULL COMMENT '发布时间',
  `content` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '内容',
  `position` tinyint NULL DEFAULT NULL COMMENT '展示位置',
  `color` varchar(9) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '颜色(HEX+不透明度)',
  `display_moment` int NULL DEFAULT NULL COMMENT '展示时刻（单位：毫秒）',
  PRIMARY KEY (`danmaku_id`) USING BTREE,
  INDEX `idx_video`(`video_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频弹幕存档' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_info
-- ----------------------------
DROP TABLE IF EXISTS `video_info`;
CREATE TABLE `video_info`  (
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `video_cover` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频封面',
  `video_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频名称',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `last_update_time` datetime NOT NULL COMMENT '最后更新时间',
  `p_category_id` int NOT NULL COMMENT '父级分类ID',
  `category_id` int NULL DEFAULT NULL COMMENT '分类ID',
  `post_type` tinyint NOT NULL COMMENT '0:自制作 1:转载',
  `origin_info` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '原资源说明',
  `tags` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签，用\",\"分隔不同的标签',
  `introduction` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '简介',
  `interaction` varchar(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '互动设置（\r\n如果可以发弹幕和发评论，就是NULL；\r\n如果不能发弹幕，但是能发评论，就是0;\r\n如果能发弹幕，不能发评论，就是1;\r\n如果既不能发弹幕，也不能发评论，就是0,1\r\n）',
  `duration` int NULL DEFAULT 0 COMMENT '持续时间（秒）',
  `play_count` int NULL DEFAULT 0 COMMENT '播放数量',
  `like_count` int NULL DEFAULT 0 COMMENT '点赞数量',
  `danmaku_count` int NULL DEFAULT 0 COMMENT '弹幕数量',
  `comment_count` int NULL DEFAULT 0 COMMENT '评论数量',
  `coin_count` int NULL DEFAULT 0 COMMENT '投币数量',
  `collect_count` int NULL DEFAULT 0 COMMENT '收藏数量',
  `recommend_type` tinyint NULL DEFAULT 0 COMMENT '是否推荐0:未推荐 1:已推荐',
  `last_play_time` datetime NULL DEFAULT NULL COMMENT '最后播放时间',
  PRIMARY KEY (`video_id`) USING BTREE,
  INDEX `idx_category_id`(`category_id` ASC) USING BTREE,
  INDEX `idx_pcategory_id`(`p_category_id` ASC) USING BTREE,
  INDEX `idx_recommend_type`(`recommend_type` ASC) USING BTREE,
  INDEX `idx_user_last_update`(`user_id` ASC, `last_update_time` DESC) USING BTREE,
  INDEX `idx_create_time`(`create_time` DESC) USING BTREE,
  INDEX `idx_last_update_time`(`last_update_time` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_info_archive
-- ----------------------------
DROP TABLE IF EXISTS `video_info_archive`;
CREATE TABLE `video_info_archive`  (
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `video_cover` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频封面（路径要展示给前端，所以相对路径不带顶层目录）',
  `video_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频名称',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `last_update_time` datetime NOT NULL COMMENT '最后更新时间',
  `delete_time` datetime NOT NULL COMMENT '删除时间',
  `deleter_type` tinyint NULL DEFAULT NULL COMMENT '删除者用户类型，0:用户，1:管理员',
  `delete_detail` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除详情',
  `p_category_id` int NOT NULL COMMENT '父级分类ID',
  `category_id` int NULL DEFAULT NULL COMMENT '分类ID',
  `post_type` tinyint NOT NULL COMMENT '0:自制作 1:转载',
  `origin_info` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '原资源说明',
  `tags` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签，用\",\"分隔不同的标签',
  `introduction` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '简介',
  `interaction` varchar(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '互动设置（\r\n如果可以发弹幕和发评论，就是NULL；\r\n如果不能发弹幕，但是能发评论，就是0;\r\n如果能发弹幕，不能发评论，就是1;\r\n如果既不能发弹幕，也不能发评论，就是0,1\r\n）',
  `duration` int NULL DEFAULT 0 COMMENT '持续时间（秒）',
  `play_count` int NULL DEFAULT 0 COMMENT '播放数量',
  `like_count` int NULL DEFAULT 0 COMMENT '点赞数量',
  `danmaku_count` int NULL DEFAULT 0 COMMENT '弹幕数量',
  `comment_count` int NULL DEFAULT 0 COMMENT '评论数量',
  `coin_count` int NULL DEFAULT 0 COMMENT '投币数量',
  `collect_count` int NULL DEFAULT 0 COMMENT '收藏数量',
  PRIMARY KEY (`video_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_delete_time`(`delete_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '删除视频存档' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_info_file
-- ----------------------------
DROP TABLE IF EXISTS `video_info_file`;
CREATE TABLE `video_info_file`  (
  `file_id` bigint NOT NULL COMMENT '唯一ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `file_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '视频文件名',
  `file_index` int NOT NULL COMMENT '文件索引',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小',
  `file_path` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',
  `duration` int NULL DEFAULT NULL COMMENT '持续时间（秒）',
  PRIMARY KEY (`file_id`) USING BTREE,
  INDEX `idx_video_index`(`video_id` ASC, `file_index` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频文件信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_info_file_archive
-- ----------------------------
DROP TABLE IF EXISTS `video_info_file_archive`;
CREATE TABLE `video_info_file_archive`  (
  `file_id` bigint NOT NULL COMMENT '唯一ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `file_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '视频文件名',
  `file_index` int NOT NULL COMMENT '文件索引',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小',
  `file_path` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',
  `duration` int NULL DEFAULT NULL COMMENT '持续时间（秒）',
  PRIMARY KEY (`file_id`) USING BTREE,
  INDEX `idx_video_index`(`video_id` ASC, `file_index` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '删除视频文件存档' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_info_file_upload
-- ----------------------------
DROP TABLE IF EXISTS `video_info_file_upload`;
CREATE TABLE `video_info_file_upload`  (
  `file_id` bigint NOT NULL COMMENT '唯一ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `file_index` int NOT NULL COMMENT '文件索引',
  `file_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件名',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小（单位：字节）',
  `file_path` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',
  `update_type` tinyint NULL DEFAULT 1 COMMENT '0:无更新 1:有更新',
  `transfer_result` tinyint NULL DEFAULT NULL COMMENT '0:转码中 1:转码成功 2:转码失败',
  `duration` int NULL DEFAULT NULL COMMENT '持续时间（秒）',
  PRIMARY KEY (`file_id`) USING BTREE,
  UNIQUE INDEX `idx_file_id`(`file_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_video_id`(`video_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频文件信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_info_upload
-- ----------------------------
DROP TABLE IF EXISTS `video_info_upload`;
CREATE TABLE `video_info_upload`  (
  `video_id` bigint NOT NULL DEFAULT 0 COMMENT '视频ID',
  `video_cover` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频封面',
  `video_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频名称',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `last_update_time` datetime NOT NULL COMMENT '最后更新时间',
  `p_category_id` int NOT NULL COMMENT '父级分类ID',
  `category_id` int NULL DEFAULT NULL COMMENT '分类ID',
  `status` tinyint NOT NULL COMMENT '0:转码中 1:转码失败 2:待审核 3:审核成功 4:审核失败',
  `post_type` tinyint NOT NULL COMMENT '0:自制作 1:转载',
  `origin_info` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '原资源说明',
  `tags` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '标签，用\",\"分隔不同的标签',
  `introduction` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '简介',
  `interaction` varchar(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '互动设置',
  `duration` int NULL DEFAULT NULL COMMENT '持续时间（秒）',
  PRIMARY KEY (`video_id`) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_category_id`(`category_id` ASC) USING BTREE,
  INDEX `idx_pcategory_id`(`p_category_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_play_daily
-- ----------------------------
DROP TABLE IF EXISTS `video_play_daily`;
CREATE TABLE `video_play_daily`  (
  `statistics_date` date NOT NULL,
  `video_id` bigint NOT NULL,
  `video_user_id` bigint NOT NULL,
  `play_count` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`statistics_date`, `video_id`) USING BTREE,
  INDEX `idx_date_user`(`statistics_date` ASC, `video_user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_play_history
-- ----------------------------
DROP TABLE IF EXISTS `video_play_history`;
CREATE TABLE `video_play_history`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `file_index` int NOT NULL COMMENT '文件索引',
  `last_update_time` datetime NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (`user_id`, `video_id`) USING BTREE,
  INDEX `idx_user_time_rev`(`user_id` ASC, `last_update_time` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频播放历史' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_series_info
-- ----------------------------
DROP TABLE IF EXISTS `video_series_info`;
CREATE TABLE `video_series_info`  (
  `series_id` bigint NOT NULL COMMENT '合集ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `series_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '合集名称',
  `series_description` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '合集描述',
  `sort_index` tinyint NOT NULL COMMENT '排序序号',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`series_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户视频合集信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for video_series_video
-- ----------------------------
DROP TABLE IF EXISTS `video_series_video`;
CREATE TABLE `video_series_video`  (
  `series_id` bigint NOT NULL COMMENT '合集ID',
  `video_id` bigint NOT NULL COMMENT '视频ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `sort_index` tinyint NOT NULL COMMENT '排序序号',
  PRIMARY KEY (`series_id`, `video_id`) USING BTREE,
  INDEX `idx_series_sort_video`(`series_id` ASC, `sort_index` ASC, `video_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户视频合集视频信息' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
