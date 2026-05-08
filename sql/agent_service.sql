/*
 Navicat Premium Data Transfer

 Source Server         : Mysql-agent
 Source Server Type    : MySQL
 Source Server Version : 80046
 Source Host           : localhost:3307
 Source Schema         : agent_service

 Target Server Type    : MySQL
 Target Server Version : 80046
 File Encoding         : 65001

 Date: 08/05/2026 14:19:43
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for agent_chat_messages
-- ----------------------------
DROP TABLE IF EXISTS `agent_chat_messages`;
CREATE TABLE `agent_chat_messages`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `message_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息类型: USER_MESSAGE, AI_TEXT, AI_THOUGHT, MCP_ACTION, MCP_RESULT, ASK_USER, USER_INPUT, FILE_GENERATED, TASK_UPDATE, ERROR',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '角色: user, assistant, system',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '消息内容(文本/JSON)',
  `metadata` json NULL COMMENT '元数据(JSON，不同type有不同结构)',
  `parent_id` bigint(0) NULL DEFAULT NULL COMMENT '父消息ID(如MCP_RESULT关联到MCP_ACTION)',
  `round_index` int(0) NULL DEFAULT NULL COMMENT 'ReAct轮次(从0开始)',
  `sort_order` int(0) NOT NULL DEFAULT 0 COMMENT '排序序号(同session内递增)',
  `duration_ms` int(0) NULL DEFAULT NULL COMMENT '耗时(毫秒，AI_TEXT/MCP_ACTION适用)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_session_sort`(`session_id`, `sort_order`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_message_type`(`message_type`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4894 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会话对话记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_file_storage
-- ----------------------------
DROP TABLE IF EXISTS `agent_file_storage`;
CREATE TABLE `agent_file_storage`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `task_id` bigint(0) NULL DEFAULT NULL COMMENT '关联任务ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件名',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始文件名',
  `storage_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MinIO对象键',
  `file_size` bigint(0) NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `file_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件类型: docx, xlsx, pdf等',
  `mime_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MIME类型',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'upload' COMMENT '来源: upload(用户上传), generated(Agent生成)',
  `is_original` tinyint(1) NULL DEFAULT 1 COMMENT '是否为原始文件(1-原始,0-副本/修改版)',
  `original_file_id` bigint(0) NULL DEFAULT NULL COMMENT '原始文件ID(副本指向原文件)',
  `expire_time` datetime(0) NULL DEFAULT NULL COMMENT '过期时间(定期清理)',
  `download_count` int(0) NULL DEFAULT 0 COMMENT '下载次数',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_task_id`(`task_id`) USING BTREE,
  INDEX `idx_storage_key`(`storage_key`(255)) USING BTREE,
  INDEX `idx_source`(`source`) USING BTREE,
  INDEX `idx_expire_time`(`expire_time`) USING BTREE,
  INDEX `idx_original_file_id`(`original_file_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 548 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文件暂存记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_knowledge_bases
-- ----------------------------
DROP TABLE IF EXISTS `agent_knowledge_bases`;
CREATE TABLE `agent_knowledge_bases`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `kb_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识库唯一标识(UUID)',
  `user_id` bigint(0) NOT NULL COMMENT '所属用户ID(用户隔离核心字段)',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识库名称(默认取文件名)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '一句话描述知识库内容(AI查询依据)',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始文件名',
  `file_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件类型: docx/xlsx/pdf/txt等',
  `file_size` bigint(0) NULL DEFAULT 0 COMMENT '文件大小(字节)',
  `storage_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'MinIO对象键',
  `chunk_count` int(0) NULL DEFAULT 0 COMMENT '分块数量',
  `chunk_strategy` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PARAGRAPH' COMMENT '分块策略: PARAGRAPH/FIXED_SIZE/CHAPTER',
  `chunk_size` int(0) NULL DEFAULT 500 COMMENT '分块大小(字符数)',
  `chunk_overlap` int(0) NULL DEFAULT 50 COMMENT '分块重叠(字符数)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'UPLOADING' COMMENT '状态: UPLOADING/CHUNKING/EMBEDDING/ACTIVE/ERROR',
  `progress` int(0) NULL DEFAULT 0 COMMENT '索引进度(0-100)',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '错误信息',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_id`(`kb_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_knowledge_chunks
-- ----------------------------
DROP TABLE IF EXISTS `agent_knowledge_chunks`;
CREATE TABLE `agent_knowledge_chunks`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `chunk_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分块唯一标识(UUID)',
  `kb_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属知识库ID',
  `user_id` bigint(0) NOT NULL COMMENT '所属用户ID',
  `chunk_index` int(0) NOT NULL COMMENT '分块序号(从0开始)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '分块文本内容',
  `content_hash` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '内容MD5哈希(去重)',
  `char_count` int(0) NULL DEFAULT 0 COMMENT '字符数',
  `milvus_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Milvus中的向量ID',
  `metadata` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '元数据(JSON: 页码/章节/位置等)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chunk_id`(`chunk_id`) USING BTREE,
  INDEX `idx_kb_id`(`kb_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_content_hash`(`content_hash`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_operation_details
-- ----------------------------
DROP TABLE IF EXISTS `agent_operation_details`;
CREATE TABLE `agent_operation_details`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` bigint(0) NOT NULL COMMENT '关联agent_chat_messages表的ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `operation_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '操作类型: MCP/SYSTEM',
  `tool_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工具名称',
  `action_input` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '操作输入参数(JSON)',
  `action_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '操作返回结果(JSON)',
  `file_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联文件key(如有)',
  `round_index` int(0) NULL DEFAULT NULL COMMENT 'ReAct轮次',
  `duration_ms` int(0) NULL DEFAULT NULL COMMENT '耗时(毫秒)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_id`(`message_id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_tool_name`(`tool_name`) USING BTREE,
  INDEX `idx_file_key`(`file_key`(255)) USING BTREE,
  INDEX `idx_session_round`(`session_id`, `round_index`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1408 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作内容详情表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_scheduled_tasks
-- ----------------------------
DROP TABLE IF EXISTS `agent_scheduled_tasks`;
CREATE TABLE `agent_scheduled_tasks`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务唯一标识(UUID)',
  `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联Agent ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '关联会话ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `task_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `task_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '任务描述(AI可读，用于心跳触发时构建执行指令)',
  `repeat_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ONCE' COMMENT '重复类型: ONCE/WEEKLY/MONTHLY/YEARLY',
  `repeat_rule` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '重复规则(JSON)',
  `task_input` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '执行时的输入消息(AI生成，心跳触发时作为用户消息传入ReAct)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/PAUSED/COMPLETED/CANCELLED',
  `last_execute_time` datetime(0) NULL DEFAULT NULL COMMENT '上次执行时间',
  `next_execute_time` datetime(0) NULL DEFAULT NULL COMMENT '下次执行时间',
  `execute_count` int(0) NULL DEFAULT 0 COMMENT '已执行次数',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_id`(`task_id`) USING BTREE,
  INDEX `idx_agent_id`(`agent_id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_next_execute_time`(`next_execute_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_session_summaries
-- ----------------------------
DROP TABLE IF EXISTS `agent_session_summaries`;
CREATE TABLE `agent_session_summaries`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `summary_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'INCREMENTAL' COMMENT '摘要类型: INCREMENTAL(增量), FULL(全量压缩)',
  `summary_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '摘要内容(LLM生成)',
  `last_message_id` bigint(0) NOT NULL COMMENT '摘要截止的最晚对话记录ID(agent_chat_messages.id)',
  `token_count` int(0) NULL DEFAULT 0 COMMENT '摘要内容预估Token数',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_last_message_id`(`last_message_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '会话历史摘要表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_sessions
-- ----------------------------
DROP TABLE IF EXISTS `agent_sessions`;
CREATE TABLE `agent_sessions`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `session_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话唯一标识',
  `user_id` bigint(0) NOT NULL COMMENT '所属用户ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '会话标题',
  `context` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '会话上下文(JSON格式)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE, CLOSED, ARCHIVED',
  `message_count` int(0) NULL DEFAULT 0 COMMENT '消息数量',
  `last_message_time` datetime(0) NULL DEFAULT NULL COMMENT '最后消息时间',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_id`(`session_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 373 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Agent会话管理表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_task_lists
-- ----------------------------
DROP TABLE IF EXISTS `agent_task_lists`;
CREATE TABLE `agent_task_lists`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint(0) NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ACTIVE',
  `task_items` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `completed_count` int(0) NULL DEFAULT 0,
  `total_count` int(0) NULL DEFAULT 0,
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `deleted` tinyint(0) NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 370 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_workflow_edges
-- ----------------------------
DROP TABLE IF EXISTS `agent_workflow_edges`;
CREATE TABLE `agent_workflow_edges`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `edge_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '边唯一标识(UUID)',
  `workflow_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作流ID',
  `user_id` bigint(0) NOT NULL COMMENT '所属用户ID',
  `source_node_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '源节点ID',
  `target_node_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '目标节点ID',
  `condition` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '条件表达式(预留, 当前版本不使用)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_edge_id`(`edge_id`) USING BTREE,
  INDEX `idx_workflow_id`(`workflow_id`) USING BTREE,
  INDEX `idx_source_node`(`source_node_id`) USING BTREE,
  INDEX `idx_target_node`(`target_node_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 35 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_workflow_executions
-- ----------------------------
DROP TABLE IF EXISTS `agent_workflow_executions`;
CREATE TABLE `agent_workflow_executions`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `execution_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行唯一标识(UUID)',
  `workflow_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流ID',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联会话ID',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED/ABORTED',
  `current_node_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '当前执行节点ID',
  `completed_nodes` int(0) NULL DEFAULT 0 COMMENT '已完成节点数',
  `total_nodes` int(0) NULL DEFAULT 0 COMMENT '总任务节点数',
  `execution_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '执行结果(JSON)',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '错误信息',
  `start_time` datetime(0) NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime(0) NULL DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_execution_id`(`execution_id`) USING BTREE,
  INDEX `idx_workflow_id`(`workflow_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_session_id`(`session_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 83 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_workflow_nodes
-- ----------------------------
DROP TABLE IF EXISTS `agent_workflow_nodes`;
CREATE TABLE `agent_workflow_nodes`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `node_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点唯一标识(UUID)',
  `workflow_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '所属工作流ID',
  `user_id` bigint(0) NOT NULL COMMENT '所属用户ID',
  `node_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点类型: START/END/TASK',
  `node_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点名称',
  `position_x` int(0) NULL DEFAULT 0 COMMENT '画布X坐标',
  `position_y` int(0) NULL DEFAULT 0 COMMENT '画布Y坐标',
  `tool_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联MCP工具名(TASK节点)',
  `tool_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '工具参数模板(JSON, 支持变量引用)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '节点描述',
  `sort_order` int(0) NULL DEFAULT 0 COMMENT '执行顺序(拓扑排序)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_node_id`(`node_id`) USING BTREE,
  INDEX `idx_workflow_id`(`workflow_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 46 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_workflows
-- ----------------------------
DROP TABLE IF EXISTS `agent_workflows`;
CREATE TABLE `agent_workflows`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `workflow_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流唯一标识(UUID)',
  `user_id` bigint(0) NOT NULL COMMENT '所属用户ID(用户隔离)',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '工作流名称(LLM生成或用户修改)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作流描述',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
  `node_count` int(0) NULL DEFAULT 0 COMMENT '节点数量(不含开始/结束)',
  `version` int(0) NULL DEFAULT 1 COMMENT '版本号',
  `create_source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'MANUAL' COMMENT '创建来源: MANUAL/AI',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_workflow_id`(`workflow_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agents
-- ----------------------------
DROP TABLE IF EXISTS `agents`;
CREATE TABLE `agents`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Agent唯一标识(UUID)',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '绑定的会话ID(1:1)',
  `user_id` bigint(0) NOT NULL COMMENT '用户ID',
  `agent_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Agent名称',
  `agent_avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Agent头像URL',
  `agent_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'IDLE' COMMENT 'Agent运行状态: IDLE/RUNNING/WAITING_USER_INPUT/PAUSED/ERROR',
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT 'Agent自定义系统提示词',
  `capabilities` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Agent能力标签(逗号分隔)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'ACTIVE' COMMENT '记录状态: ACTIVE/DELETED',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_agent_id`(`agent_id`) USING BTREE,
  UNIQUE INDEX `uk_session_id`(`session_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_agent_status`(`agent_status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 166 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for skill_definitions
-- ----------------------------
DROP TABLE IF EXISTS `skill_definitions`;
CREATE TABLE `skill_definitions`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT 'Skill ID',
  `skill_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Skill唯一标识',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'Skill名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Skill描述',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '分类: document, data, report, tool',
  `version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1.0.0' COMMENT '版本号',
  `input_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '输入参数Schema(JSON格式)',
  `output_schema` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '输出结果Schema(JSON格式)',
  `config` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '配置信息(JSON格式)',
  `enabled` tinyint(0) NULL DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
  `is_builtin` tinyint(0) NULL DEFAULT 1 COMMENT '是否内置: 0-自定义, 1-内置',
  `author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '作者',
  `tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标签(逗号分隔)',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_skill_id`(`skill_id`) USING BTREE,
  INDEX `idx_category`(`category`) USING BTREE,
  INDEX `idx_enabled`(`enabled`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Skill元信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码(加密存储)',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '头像URL',
  `status` tinyint(0) NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'user' COMMENT '角色: admin, user',
  `last_login_time` datetime(0) NULL DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最后登录IP',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `deleted` tinyint(0) NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username`) USING BTREE,
  UNIQUE INDEX `uk_email`(`email`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_create_time`(`create_time`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户信息表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
