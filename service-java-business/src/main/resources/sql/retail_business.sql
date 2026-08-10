/*
 Navicat Premium Dump SQL

 Source Server         : local
 Source Server Type    : MySQL
 Source Server Version : 80036 (8.0.36)
 Source Host           : localhost:3306
 Source Schema         : retail_business

 Target Server Type    : MySQL
 Target Server Version : 80036 (8.0.36)
 File Encoding         : 65001

 Date: 08/08/2026 13:02:21
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for agent_tool_definition
-- ----------------------------
DROP TABLE IF EXISTS `agent_tool_definition`;
CREATE TABLE `agent_tool_definition`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `tool_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工具名 (business:operation 格式, 如 stock:adjust)',
  `description` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工具描述 (喂 LLM)',
  `input_schema` json NULL COMMENT '输入 schema (JSON Schema, 供 Python 构建 Pydantic args_schema)',
  `output_schema` json NULL COMMENT '输出 schema',
  `required_permission` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '所需权限标识 (对齐 @SaCheckPermission)',
  `annotations` json NULL COMMENT '行为注解 (destructive/outputHint 等)',
  `tool_group` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'custom' COMMENT '工具分组 (业务域)',
  `enabled` tinyint NULL DEFAULT 1 COMMENT '是否启用 (0=禁用, 1=启用)',
  `version` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '1.0.0' COMMENT '工具版本',
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户 ID (NULL=全局工具)',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `create_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_at` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '删除人',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name_tenant`(`tool_name` ASC, `tenant_id` ASC) USING BTREE,
  INDEX `idx_enabled`(`enabled` ASC) USING BTREE,
  INDEX `idx_group`(`tool_group` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Agent 工具定义注册表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of agent_tool_definition
-- ----------------------------
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stats:sales', '查询销售记录。支持按日期范围过滤。返回每日销售额、订单数、客单价等指标。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回销售记录列表，包含日期、销售额、订单数、客单价。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'stats', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order:query', '查询订单列表。支持按订单号、会员姓名/手机号、商品名称、状态、类型、渠道、支付方式、金额区间、时间范围过滤。可分页。用于回答\'最近订单\'\'待发货订单\'\'王五的订单\'\'买过XX商品的订单\'\'金额超500的订单\'等问题。', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"channel\": {\"type\": \"integer\", \"description\": \"channel（枚举合法值: 1=线上|2=Agent|3=手工）\"}, \"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}, \"payType\": {\"type\": \"integer\", \"description\": \"payType（枚举合法值: 1=微信支付|2=支付宝|3=余额支付|4=现金）\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"maxAmount\": {\"type\": \"number\", \"description\": \"maxAmount\"}, \"minAmount\": {\"type\": \"number\", \"description\": \"minAmount\"}, \"orderType\": {\"type\": \"integer\", \"description\": \"orderType（枚举合法值: 1=正常订单|2=闪购订单|3=秒杀订单）\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}, \"memberPhone\": {\"type\": \"string\", \"description\": \"memberPhone\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, 'business:order:query', '{\"readOnly\": true, \"outputHint\": \"返回订单列表，包含订单号、会员、金额、状态、渠道、下单时间。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'order', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stats:overview', '查询经营概览。返回商品数、促销数、评价数、会员数等关键指标计数。支持按创建时间范围过滤。用于回答\'这个季度经营怎么样\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回经营概览，包含商品数、促销数、评价数、会员数。展示为结构化文本。\", \"destructive\": false}', 'stats', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:adjust', '手动调整商品库存余额。changeQty 正数增加库存，负数减少库存。支持按商品ID、商品名称或SKU编码定位商品。此操作会直接修改库存余额，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"skuId\": {\"type\": \"integer\", \"description\": \"skuId\"}, \"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"bizType\": {\"type\": \"integer\", \"description\": \"bizType（枚举合法值: 1=订单业务|2=采购入库|3=手动调整|4=退款回滚|5=手工操作）\"}, \"skuCode\": {\"type\": \"string\", \"description\": \"skuCode\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"changeQty\": {\"type\": \"integer\", \"description\": \"changeQty\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, 'business:stock:adjust', '{\"readOnly\": false, \"outputHint\": \"返回调整结果，包含商品ID、调整数量、调整后库存余额、流水ID。展示为文本。\", \"destructive\": true}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:check', '查询商品库存。支持按商品名称、品牌、分类、商品状态(上架/下架)、门店名称、低库存(低于安全库存)、在途(采购在途中)、积压(远超安全库存)筛选，也可按商品ID精确查询。可分页。典型触发词：\'白色T恤还有多少库存\'\'xx品牌库存\'\'化妆品类目的库存\'\'哪些商品缺货了\'\'有哪些采购在途的商品\'\'哪些商品压货了\'', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"brand\": {\"type\": \"string\", \"description\": \"brand\"}, \"skuId\": {\"type\": \"integer\", \"description\": \"skuId\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"category\": {\"type\": \"string\", \"description\": \"category\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"storeName\": {\"type\": \"string\", \"description\": \"storeName\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}, \"lowStockOnly\": {\"type\": \"boolean\", \"description\": \"lowStockOnly\"}, \"highStockOnly\": {\"type\": \"boolean\", \"description\": \"highStockOnly\"}, \"inTransitOnly\": {\"type\": \"boolean\", \"description\": \"inTransitOnly\"}}}', NULL, 'business:stock:query', '{\"readOnly\": true, \"outputHint\": \"返回库存列表，包含商品名称、SKU、可用库存、锁定库存、在途库存、安全库存、门店、是否低于安全库存。展示为 markdown 表格。\", \"destructive\": false}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'category:update', '更新商品分类信息。支持修改名称、排序、状态、描述。需要分类ID定位。此操作会修改分类数据，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"sortOrder\": {\"type\": \"integer\", \"description\": \"sortOrder\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}, \"description\": {\"type\": \"string\", \"description\": \"description\"}}}', NULL, '', '{\"readOnly\": false, \"outputHint\": \"返回更新结果，true表示成功。展示为文本，提示用户分类已更新成功。\", \"destructive\": true}', 'category', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:update', '更新商品通用信息。支持修改商品名称、描述、图片URL、分类、安全库存、SPU编码、品牌等。定位需要商品ID，或商品名称/编码（内部会自动转ID）。这是破坏性操作，必须等用户确认后才可执行。注意：如果是想改价/改成本请用 price_adjust 工具；如果是想上架/下架请用 on_shelf/off_shelf 工具，它们的语义更明确并会展示原价→新价/状态预览。典型触发词：\'修改商品XX的描述\'\'把XX的品牌改成XX\'', '{\"type\": \"object\", \"properties\": {\"cost\": {\"type\": \"number\", \"description\": \"cost\"}, \"name\": {\"type\": \"string\", \"description\": \"name\"}, \"brand\": {\"type\": \"string\", \"description\": \"brand\"}, \"price\": {\"type\": \"number\", \"description\": \"price\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"spuCode\"}, \"category\": {\"type\": \"string\", \"description\": \"category\"}, \"imageUrl\": {\"type\": \"string\", \"description\": \"imageUrl\"}, \"stockQty\": {\"type\": \"integer\", \"description\": \"stockQty\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}, \"description\": {\"type\": \"string\", \"description\": \"description\"}, \"safetyStock\": {\"type\": \"integer\", \"description\": \"safetyStock\"}}}', NULL, 'business:product:edit', '{\"readOnly\": false, \"outputHint\": \"返回更新结果（商品ID、更新条数），展示为文本提示用户哪些字段已更新。\", \"destructive\": true}', 'product', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:create', '创建优惠券模板。需要名称、类型(满减/折扣/代金券)、面额、使用门槛、有效期规则、发放总量、每人限领。满减券需指定满减金额和门槛，折扣券需指定折扣率(如0.8表示8折)。此操作会创建优惠券模板，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"type\": {\"type\": \"integer\", \"description\": \"type（枚举合法值: 1=优惠券|2=折扣|3=限时秒杀|1=满减券|2=折扣券|3=代金券|1=全额退款|2=部分退款|1=正常订单|2=闪购订单|3=秒杀订单|1=全部|2=商品|3=分类|1=领取后N天有效|2=固定时间段有效）\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"perLimit\": {\"type\": \"integer\", \"description\": \"perLimit\"}, \"validEnd\": {\"type\": \"string\", \"format\": \"date-time\", \"description\": \"validEnd\"}, \"faceValue\": {\"type\": \"number\", \"description\": \"faceValue\"}, \"threshold\": {\"type\": \"number\", \"description\": \"threshold\"}, \"validDays\": {\"type\": \"integer\", \"description\": \"validDays\"}, \"validType\": {\"type\": \"integer\", \"description\": \"validType\"}, \"totalCount\": {\"type\": \"integer\", \"description\": \"totalCount\"}, \"validStart\": {\"type\": \"string\", \"format\": \"date-time\", \"description\": \"validStart\"}, \"promotionId\": {\"type\": \"integer\", \"description\": \"promotionId\"}}}', NULL, 'business:coupon:add', '{\"readOnly\": false, \"outputHint\": \"返回创建结果，包含优惠券模板ID、名称、类型、面额、状态。展示为文本，提示用户优惠券模板已创建成功。\", \"destructive\": true}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'review:reply', '回复商品评价。需要评价ID和回复内容。回复后评价会显示商家回复。此操作会公开回复内容，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"content\": {\"type\": \"string\", \"description\": \"content\"}, \"reviewId\": {\"type\": \"integer\", \"description\": \"reviewId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, '', '{\"readOnly\": false, \"outputHint\": \"返回回复结果，包含评价ID、回复内容、回复时间。展示为文本，提示用户回复已发布。\", \"destructive\": true}', 'review', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'points:summary', '查询会员积分汇总。返回当前积分余额、累计获取积分、累计兑换积分、近30天积分变动。支持按会员ID、会员姓名或手机号定位会员。用于回答\'会员王五的积分情况\'。', '{\"type\": \"object\", \"properties\": {\"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}}}', NULL, 'business:points:query', '{\"readOnly\": true, \"outputHint\": \"返回积分汇总，包含当前余额、累计获取、累计兑换、近30天变动。展示为结构化文本，重点突出当前余额。\", \"destructive\": false}', 'points', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'review:approve', '审核通过商品评价。将待审核评价状态改为已通过，通过后评价对外可见。需要评价ID。此操作会改变评价可见性，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"content\": {\"type\": \"string\", \"description\": \"content\"}, \"reviewId\": {\"type\": \"integer\", \"description\": \"reviewId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, '', '{\"readOnly\": false, \"outputHint\": \"返回审核结果，包含评价ID、审核状态。展示为文本，提示用户评价已审核通过。\", \"destructive\": true}', 'review', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'points:adjust', '手动调整会员积分。changePoints正数增加积分，负数扣减积分（扣减时校验余额充足）。支持按会员ID、会员姓名或手机号定位会员，需提供变动积分数量、调整原因。此操作会直接修改会员积分余额，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}, \"changePoints\": {\"type\": \"integer\", \"description\": \"changePoints\"}}}', NULL, 'business:points:adjust', '{\"readOnly\": false, \"outputHint\": \"返回积分流水，包含变动类型、变动积分、变动前余额、变动后余额、调整原因。展示为文本，提示用户积分已调整。\", \"destructive\": true}', 'points', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'membertag:remove', '取消会员的指定标签。需要会员ID和待取消的标签ID列表。此操作会删除会员标签关系，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"tagIds\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"tagIds\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}}}', NULL, 'business:membertag:manage', '{\"readOnly\": false, \"outputHint\": \"返回删除标签关系数量。展示为文本，提示用户已取消会员标签。\", \"destructive\": true}', 'membertag', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:detail', '查询促销活动详情。支持按促销活动ID或活动名称定位，返回活动完整信息，包括类型、目标范围、时间、规则详情、关联优惠券等。用于回答\'促销活动XX的详细信息\'。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}, \"promotionId\": {\"type\": \"integer\", \"description\": \"promotionId\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回促销详情，包含名称、类型、目标类型、目标商品列表、开始/结束时间、规则。展示为结构化文本。\", \"destructive\": false}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'membertag:query', '查询会员标签列表。支持按名称关键词模糊搜索。返回所有标签定义。用于回答\'有哪些标签\'\'查找标签\'等问题。', '{\"type\": \"object\", \"properties\": {\"keyword\": {\"type\": \"string\", \"description\": \"keyword\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}}}', NULL, 'business:membertag:query', '{\"readOnly\": true, \"outputHint\": \"返回标签列表，包含标签ID、名称、描述、状态。展示为 markdown 表格。\", \"destructive\": false}', 'membertag', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order:create', '创建订单。需要订单明细列表（商品ID、数量、单价），支持会员订单和散客订单。内部系统创建即付款，状态直接为已支付。可使用优惠券。此操作会创建订单并扣减库存，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"items\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"items\"}, \"remark\": {\"type\": \"string\", \"description\": \"remark\"}, \"channel\": {\"type\": \"integer\", \"description\": \"channel（枚举合法值: 1=线上|2=Agent|3=手工）\"}, \"payType\": {\"type\": \"integer\", \"description\": \"payType（枚举合法值: 1=微信支付|2=支付宝|3=余额支付|4=现金）\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"tenantId\": {\"type\": \"integer\", \"description\": \"tenantId\"}, \"orderTime\": {\"type\": \"string\", \"format\": \"date-time\", \"description\": \"orderTime\"}, \"orderType\": {\"type\": \"integer\", \"description\": \"orderType（枚举合法值: 1=正常订单|2=闪购订单|3=秒杀订单）\"}, \"userCouponId\": {\"type\": \"integer\", \"description\": \"userCouponId\"}}}', NULL, 'business:order:add', '{\"readOnly\": false, \"outputHint\": \"返回创建结果，包含订单ID、订单号、支付时间、总金额。展示为文本，提示用户订单已创建成功。\", \"destructive\": true}', 'order', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'points:logs', '查询会员积分流水。支持按会员ID、会员姓名或手机号定位会员，并按变动类型(获取/兑换/退款/调整)和时间范围过滤。可分页。用于回答\'会员王五的积分明细\'\'最近积分变动\'等问题。', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"changeType\": {\"type\": \"integer\", \"description\": \"changeType\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}}}', NULL, 'business:points:query', '{\"readOnly\": true, \"outputHint\": \"返回积分流水列表，包含变动类型、变动积分、变动前后余额、业务类型、关联单号、时间。展示为 markdown 表格。\", \"destructive\": false}', 'points', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:create', '创建促销活动。需要活动名称、类型(优惠券/折扣/限时秒杀)、目标范围(全品/分类/指定商品)、起止时间、规则。系统根据当前时间自动推断活动状态。此操作会创建促销活动，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"type\": {\"type\": \"integer\", \"description\": \"type（枚举合法值: 1=优惠券|2=折扣|3=限时秒杀|1=满减券|2=折扣券|3=代金券|1=全额退款|2=部分退款|1=正常订单|2=闪购订单|3=秒杀订单|1=全部|2=商品|3=分类|1=领取后N天有效|2=固定时间段有效）\"}, \"rules\": {\"type\": \"object\", \"description\": \"rules\"}, \"endTime\": {\"type\": \"string\", \"format\": \"date-time\", \"description\": \"endTime\"}, \"startTime\": {\"type\": \"string\", \"format\": \"date-time\", \"description\": \"startTime\"}, \"targetIds\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"targetIds\"}, \"targetType\": {\"type\": \"integer\", \"description\": \"targetType（枚举合法值: 1=全部|2=商品|3=分类）\"}}}', NULL, '', '{\"readOnly\": false, \"outputHint\": \"返回创建结果，包含活动ID、名称、类型、状态。展示为文本，提示用户促销活动已创建成功。\", \"destructive\": true}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'refund:audit', '审核退款单。支持按退款单ID、退款单号或原订单号定位退款单，需审核结果。审核结果为整数code：2通过/3拒绝，必须传数字，如通过传result=2。审核通过会触发退款联动：退券、退积分、库存回滚、订单金额更新。全额退款则订单状态改为已退款。此操作会执行退款并影响多个模块，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"remark\": {\"type\": \"string\", \"description\": \"remark\"}, \"result\": {\"type\": \"integer\", \"description\": \"result\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}, \"refundId\": {\"type\": \"integer\", \"description\": \"refundId\"}, \"refundNo\": {\"type\": \"string\", \"description\": \"refundNo\"}}}', NULL, 'business:refund:audit', '{\"readOnly\": false, \"outputHint\": \"返回审核结果，包含退款单ID、审核结果、退款联动详情（退券数、退积分数、库存回滚数）。展示为结构化文本。\", \"destructive\": true}', 'refund', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'membertag:assign', '给会员批量分配标签。需要会员ID和标签ID列表。自动去重，已存在的标签关系不会重复分配。此操作会修改会员标签关系，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"tagIds\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"tagIds\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}}}', NULL, 'business:membertag:manage', '{\"readOnly\": false, \"outputHint\": \"返回新增标签关系数量。展示为文本，提示用户已为会员分配标签。\", \"destructive\": true}', 'membertag', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order:cancel', '取消订单。将待支付订单状态改为已关闭。支持按订单ID或订单号定位订单。仅待支付(PENDING)状态的订单可取消。此操作会改变订单状态，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"orderId\": {\"type\": \"integer\", \"description\": \"orderId\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}}}', NULL, 'business:order:edit', '{\"readOnly\": false, \"outputHint\": \"返回取消结果，true表示成功。展示为文本，提示用户订单已取消。\", \"destructive\": true}', 'order', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'category:create', '创建商品分类。需要分类名称，可指定父分类(创建子分类)、排序、状态、描述。状态为整数code：1启用/0停用，默认1启用，必须传数字。此操作会新增分类数据，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"parentId\": {\"type\": \"integer\", \"description\": \"parentId\"}, \"sortOrder\": {\"type\": \"integer\", \"description\": \"sortOrder\"}, \"description\": {\"type\": \"string\", \"description\": \"description\"}}}', NULL, '', '{\"readOnly\": false, \"outputHint\": \"返回创建结果，包含分类ID、名称、父分类ID。展示为文本，提示用户分类已创建成功。\", \"destructive\": true}', 'category', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order:detail', '查询订单详情。支持按订单ID或订单号定位订单，返回订单完整信息，包括订单号、会员、金额、状态、支付方式、明细列表（商品、数量、价格）。用于回答\'订单XX的详情\'。', '{\"type\": \"object\", \"properties\": {\"orderId\": {\"type\": \"integer\", \"description\": \"orderId\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}}}', NULL, 'business:order:query', '{\"readOnly\": true, \"outputHint\": \"返回订单详情，包含订单号、会员、金额、状态、支付方式、下单时间、明细列表。明细用 markdown 表格展示，金额保留 2 位小数。\", \"destructive\": false}', 'order', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'refund:detail', '查询退款单详情。支持按退款单ID、退款单号或原订单号定位，返回退款单完整信息，包括退款金额、退款类型、退款原因、审核状态、关联订单信息。用于回答\'退款单XX的详情\'。', '{\"type\": \"object\", \"properties\": {\"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}, \"refundId\": {\"type\": \"integer\", \"description\": \"refundId\"}, \"refundNo\": {\"type\": \"string\", \"description\": \"refundNo\"}}}', NULL, 'business:refund:query', '{\"readOnly\": true, \"outputHint\": \"返回退款单详情，包含退款单号、原订单号、退款金额、退款类型、退款原因、状态、审核备注。展示为结构化文本，金额保留 2 位小数。\", \"destructive\": false}', 'refund', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'review:stats', '查询商品评价统计。返回总评价数、平均评分、好评率、已通过数、待审核数。支持按商品ID或商品名称定位商品。用于回答\'白色T恤的评价情况\'\'评分怎么样\'等问题。', '{\"type\": \"object\", \"properties\": {\"content\": {\"type\": \"string\", \"description\": \"content\"}, \"reviewId\": {\"type\": \"integer\", \"description\": \"reviewId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回评价统计，包含总评价数、平均评分、好评率、已通过数、待审核数。展示为结构化文本，平均评分保留 1 位小数。\", \"destructive\": false}', 'review', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:detail', '查询商品详情。支持按商品ID、商品编码或商品名称定位（三选一）。返回商品完整信息：名称、价格、成本、品牌、描述、图片URL、SKU列表、分类信息。典型触发词：\'商品XX的详细信息\'\'看看XX有哪些规格/颜色/尺码\'', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"spuCode\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}}}', NULL, 'business:product:query', '{\"readOnly\": true, \"outputHint\": \"返回商品详情结构化文本，SKU 列表用 markdown 表格。\", \"destructive\": false}', 'product', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:product', '查询商品参与的促销活动。支持按商品ID或商品名称定位商品，返回该商品当前正在进行的所有促销活动。用于回答\'商品XX有什么优惠\'\'这个商品在打折吗\'等问题。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}, \"promotionId\": {\"type\": \"integer\", \"description\": \"promotionId\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回促销活动列表，包含活动名称、类型、规则、起止时间。展示为 markdown 表格。\", \"destructive\": false}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:query', '查询促销活动列表。支持按状态(进行中/未开始/已结束)、目标类型(全品/分类/指定商品)、活动类型(优惠券/折扣/秒杀)、名称关键词、活动起止时间范围过滤。可分页。用于回答\'当前促销活动\'\'这个月的满减活动\'等问题。', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"type\": {\"type\": \"integer\", \"description\": \"type（枚举合法值: 1=优惠券|2=折扣|3=限时秒杀|1=满减券|2=折扣券|3=代金券|1=全额退款|2=部分退款|1=正常订单|2=闪购订单|3=秒杀订单|1=全部|2=商品|3=分类|1=领取后N天有效|2=固定时间段有效）\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"keyword\": {\"type\": \"string\", \"description\": \"keyword\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"targetType\": {\"type\": \"integer\", \"description\": \"targetType（枚举合法值: 1=全部|2=商品|3=分类）\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回促销活动列表，包含活动名称、类型、目标类型、开始时间、结束时间、状态。展示为 markdown 表格。\", \"destructive\": false}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'review:query', '查询商品评价列表。支持按商品ID或商品名称、评分(1-5)、状态(待审核/已通过/已拒绝)、评价内容关键词、评价时间范围过滤。可分页。用于回答\'白色T恤的评价\'\'差评有哪些\'\'最近7天的差评\'等问题。', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"rating\": {\"type\": \"integer\", \"description\": \"rating\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"keyword\": {\"type\": \"string\", \"description\": \"keyword\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回评价列表，包含评价ID、商品名称、会员、评分、内容、状态、评价时间。展示为 markdown 表格，评分用星号标注。\", \"destructive\": false}', 'review', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:query', '查询优惠券模板列表。支持按状态(启用/停用)、类型(满减/折扣/代金券)、名称关键词、面额区间、使用门槛区间、有效期范围过滤。可分页。用于回答\'有哪些优惠券\'\'满100减20的券\'\'本月到期的券\'等问题。', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"type\": {\"type\": \"integer\", \"description\": \"type（枚举合法值: 1=优惠券|2=折扣|3=限时秒杀|1=满减券|2=折扣券|3=代金券|1=全额退款|2=部分退款|1=正常订单|2=闪购订单|3=秒杀订单|1=全部|2=商品|3=分类|1=领取后N天有效|2=固定时间段有效）\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"keyword\": {\"type\": \"string\", \"description\": \"keyword\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"validEnd\": {\"type\": \"string\", \"description\": \"validEnd\"}, \"validStart\": {\"type\": \"string\", \"description\": \"validStart\"}, \"maxFaceValue\": {\"type\": \"number\", \"description\": \"maxFaceValue\"}, \"maxThreshold\": {\"type\": \"number\", \"description\": \"maxThreshold\"}, \"minFaceValue\": {\"type\": \"number\", \"description\": \"minFaceValue\"}, \"minThreshold\": {\"type\": \"number\", \"description\": \"minThreshold\"}}}', NULL, 'business:coupon:query', '{\"readOnly\": true, \"outputHint\": \"返回优惠券模板列表，包含名称、类型、面额、门槛、有效期、发放总量、已发放量、状态。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:issue', '批量发放优惠券给指定会员。需要优惠券模板ID，发放对象支持会员ID列表，或按会员姓名/手机号/等级定位会员。发放后模板已发放量增加，会员获得可用优惠券。对单个会员的失败不影响其他会员。此操作会向会员发放优惠券，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"couponId\": {\"type\": \"integer\", \"description\": \"couponId\"}, \"memberIds\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"memberIds\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}, \"memberLevel\": {\"type\": \"integer\", \"description\": \"memberLevel（枚举合法值: 1=普通|2=银卡|3=金卡|4=钻石）\"}, \"memberPhone\": {\"type\": \"string\", \"description\": \"memberPhone\"}}}', NULL, 'business:coupon:issue', '{\"readOnly\": false, \"outputHint\": \"返回发放结果，包含成功数、失败数、失败会员列表。展示为文本，提示用户发放完成。\", \"destructive\": true}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'category:detail', '查询商品分类详情。支持按分类ID或分类名称定位，返回分类的完整信息，包括名称、父分类、排序、状态、描述。用于回答\'分类XX的详细信息\'。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回分类详情，包含分类ID、名称、父分类ID、排序、状态、描述。展示为结构化文本。\", \"destructive\": false}', 'category', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:query', '查询商品列表。支持多条件筛选：按商品名/编码关键词、分类（分类ID或分类名）、商品状态、品牌、价格区间（最低/最高）、低库存、有货(inStock)、清仓标记(clearance)、创建时间区间。可分页。商品状态为整数code：1上架/0下架，必须传数字，如查在售商品传status=1、查已下架传status=0。典型触发词：\'有哪些商品\'\'xx品牌的商品\'\'在售商品\'\'100元左右的商品\'\'哪些商品缺货了\'\'有什么有货的饮料\'\'有哪些清仓零食\'', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"brand\": {\"type\": \"string\", \"description\": \"brand\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"inStock\": {\"type\": \"boolean\", \"description\": \"inStock\"}, \"keyword\": {\"type\": \"string\", \"description\": \"keyword\"}, \"category\": {\"type\": \"string\", \"description\": \"category\"}, \"maxPrice\": {\"type\": \"number\", \"description\": \"maxPrice\"}, \"minPrice\": {\"type\": \"number\", \"description\": \"minPrice\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"clearance\": {\"type\": \"boolean\", \"description\": \"clearance\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}, \"lowStockOnly\": {\"type\": \"boolean\", \"description\": \"lowStockOnly\"}, \"createTimeEnd\": {\"type\": \"string\", \"description\": \"createTimeEnd\"}, \"createTimeStart\": {\"type\": \"string\", \"description\": \"createTimeStart\"}}}', NULL, 'business:product:query', '{\"readOnly\": true, \"outputHint\": \"返回商品分页列表，每条包含商品名称、分类、品牌、售价、状态（上架/下架）、库存、安全库存、是否低于安全库存。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'product', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order:ship', '订单发货。将已支付订单状态改为已发货。支持按订单ID或订单号定位订单。仅已支付(PAID)状态的订单可发货。此操作会改变订单状态，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"orderId\": {\"type\": \"integer\", \"description\": \"orderId\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}}}', NULL, 'business:order:edit', '{\"readOnly\": false, \"outputHint\": \"返回发货结果，true表示成功。展示为文本，提示用户订单已发货。\", \"destructive\": true}', 'order', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'membertag:member_tags', '查询指定会员的所有标签。返回该会员当前已分配的标签列表。用于回答\'会员XX有哪些标签\'\'会员标签情况\'等问题。', '{\"type\": \"object\", \"properties\": {\"keyword\": {\"type\": \"string\", \"description\": \"keyword\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}}}', NULL, 'business:membertag:query', '{\"readOnly\": true, \"outputHint\": \"返回会员标签列表，包含标签ID、名称、描述。展示为 markdown 表格或标签云。\", \"destructive\": false}', 'membertag', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'category:tree', '查询商品分类树。返回所有分类的树形结构，包含父子层级关系。用于回答\'有哪些商品分类\'\'分类结构\'等问题。', '{}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回分类树，包含分类ID、名称、父分类、排序、状态、子分类列表。展示为树形结构文本。\", \"destructive\": false}', 'category', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'refund:create', '创建退款申请。需要原订单ID、退款类型、退款金额、退款原因。退款类型为整数code：1全额退款/2部分退款，必须传数字，如全额退传refundType=1。仅已支付/已发货/已完成的订单可退款。创建后退款单进入待审核状态。此操作会创建退款单并标记订单为退款中，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"orderId\": {\"type\": \"integer\", \"description\": \"orderId\"}, \"refundQty\": {\"type\": \"integer\", \"description\": \"refundQty\"}, \"refundType\": {\"type\": \"integer\", \"description\": \"refundType（枚举合法值: 1=全额退款|2=部分退款）\"}, \"refundAmount\": {\"type\": \"number\", \"description\": \"refundAmount\"}}}', NULL, 'business:refund:audit', '{\"readOnly\": false, \"outputHint\": \"返回退款单信息，包含退款单ID、退款单号、退款金额、状态。展示为文本，提示用户退款申请已创建，等待审核。\", \"destructive\": true}', 'refund', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order:complete', '完成订单。将已发货订单状态改为已完成。完成时会触发会员积分获取。支持按订单ID或订单号定位订单。仅已发货(SHIPPED)状态的订单可完成。此操作会改变订单状态并发放积分，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"orderId\": {\"type\": \"integer\", \"description\": \"orderId\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}}}', NULL, 'business:order:edit', '{\"readOnly\": false, \"outputHint\": \"返回完成结果，true表示成功。展示为文本，提示用户订单已完成并已发放积分。\", \"destructive\": true}', 'order', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:create', '创建新商品。必填商品名称、分类（分类ID优先，分类名也可）、售价；可选成本、状态、描述、图片URL、初始库存、安全库存。状态为整数code：1上架/0下架，必须传数字，如创建在售商品传status=1；不传默认1上架。创建后商品状态默认在售。这是破坏性操作，必须等用户确认后才可执行。典型触发词：\'帮我上一款新商品：海天生抽500ml，15块，调味品分类\'\'新增商品XX，售价XX\'', '{\"type\": \"object\", \"properties\": {\"cost\": {\"type\": \"number\", \"description\": \"cost\"}, \"name\": {\"type\": \"string\", \"description\": \"name\"}, \"brand\": {\"type\": \"string\", \"description\": \"brand\"}, \"price\": {\"type\": \"number\", \"description\": \"price\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"spuCode\"}, \"category\": {\"type\": \"string\", \"description\": \"category\"}, \"imageUrl\": {\"type\": \"string\", \"description\": \"imageUrl\"}, \"stockQty\": {\"type\": \"integer\", \"description\": \"stockQty\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}, \"description\": {\"type\": \"string\", \"description\": \"description\"}, \"safetyStock\": {\"type\": \"integer\", \"description\": \"safetyStock\"}}}', NULL, 'business:product:add', '{\"readOnly\": false, \"outputHint\": \"返回创建结果（商品ID、名称、状态），展示为文本提示用户商品已创建成功。\", \"destructive\": true}', 'product', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'refund:query', '查询退款单列表。支持按退款状态、订单号、会员姓名/手机号、退款类型(全额/部分)、退款金额区间、时间范围过滤。可分页。用于回答\'王五的退款\'\'部分退款\'\'退款金额超100的退款\'等问题。', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"maxAmount\": {\"type\": \"number\", \"description\": \"maxAmount\"}, \"minAmount\": {\"type\": \"number\", \"description\": \"minAmount\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}, \"refundType\": {\"type\": \"integer\", \"description\": \"refundType（枚举合法值: 1=全额退款|2=部分退款）\"}, \"memberPhone\": {\"type\": \"string\", \"description\": \"memberPhone\"}}}', NULL, 'business:refund:query', '{\"readOnly\": true, \"outputHint\": \"返回退款单列表，包含退款单号、原订单号、会员姓名、退款金额、退款类型、状态、申请时间。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'refund', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:detail', '查询优惠券模板详情。支持按优惠券模板ID或券名定位，返回模板完整信息，包括类型、面额、使用门槛、有效期规则、发放总量、每人限领、关联促销等。用于回答\'优惠券XX的详细信息\'。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"couponId\": {\"type\": \"integer\", \"description\": \"couponId\"}}}', NULL, 'business:coupon:query', '{\"readOnly\": true, \"outputHint\": \"返回优惠券详情，包含名称、类型、面额、门槛、有效期类型、有效期、发放总量、已发放量、每人限领、状态。展示为结构化文本，金额保留 2 位小数。\", \"destructive\": false}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'member:detail', '查询会员详情。返回会员完整信息，包括姓名、手机号、等级、积分、累计消费、累计订单数、最后下单/活跃时间。可通过会员ID或手机号定位。用于回答\'会员王五的详细信息\'。', '{\"type\": \"object\", \"properties\": {\"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回会员详情，包含姓名、手机号、等级、积分、累计消费、累计订单数、最后下单时间。展示为结构化文本，金额保留 2 位小数。\", \"destructive\": false}', 'member', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stats:member_growth', '查询会员增长趋势。返回按日统计的新增会员数与活跃会员数。支持按日期范围过滤。用于回答\'最近一周新增会员\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回会员增长趋势列表，包含日期、新增会员数、活跃会员数。展示为 markdown 表格。\", \"destructive\": false}', 'stats', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stats:order_trend', '查询订单趋势。返回按日统计的订单数、销售额等指标。支持按日期范围过滤。用于回答\'最近7天订单趋势\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回订单趋势列表，包含日期、订单数、销售额。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'stats', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'member:query', '查询会员列表。支持按姓名、手机号、会员等级、积分区间、累计消费区间、累计订单数过滤。可分页。用于回答\'查会员王五\'\'金卡会员有哪些\'\'消费超1000的会员\'等问题。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"level\": {\"type\": \"integer\", \"description\": \"level（枚举合法值: 1=普通|2=银卡|3=金卡|4=钻石）\"}, \"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"isDesc\": {\"type\": \"boolean\", \"description\": \"isDesc\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"maxPoints\": {\"type\": \"integer\", \"description\": \"maxPoints\"}, \"minPoints\": {\"type\": \"integer\", \"description\": \"minPoints\"}, \"maxTotalSpent\": {\"type\": \"number\", \"description\": \"maxTotalSpent\"}, \"minTotalSpent\": {\"type\": \"number\", \"description\": \"minTotalSpent\"}, \"minTotalOrders\": {\"type\": \"integer\", \"description\": \"minTotalOrders\"}}}', NULL, '', '{\"readOnly\": true, \"outputHint\": \"返回会员列表，包含会员姓名、手机号、等级、积分、累计消费、累计订单数、最后下单时间。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'member', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '查询零售订单数据. 可按时间范围、门店 ID、订单状态过滤, 返回订单列表 (订单号、金额、状态、下单时间、门店). 用于回答查订单、最近订单类问题.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '查询商品库存数据. 可按商品名称、SKU、门店过滤, 返回库存列表 (商品、SKU、可用库存、门店). 用于回答查库存、库存量类问题.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '查询销售统计数据. 按日期范围返回每日销售记录 (日期、销售额、订单数、客单价). 用于回答销售趋势、本月销售额、销售对比类问题.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '通用数据库查询工具. 执行只读 SQL 查询 (仅 SELECT), 返回查询结果. 用于复杂数据查询场景.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '通用 Java 后端透传工具. 按 method+path+params 调用 Java 任意业务接口, 供业务工具封装层调用, 不直接暴露给 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '下架商品。支持两种方式：① 单个商品定位（商品名/编码/ID三选一）；② 批量圈选（按品牌名、分类名或分类ID、多个商品名列表、多个ID列表；多条件可组合AND）。单次批量上限 50 条，超过请分批。这是破坏性操作，必须等用户确认后才可执行。典型触发词：\'把可口可乐下架了\'\'xx品牌的商品全下架\'\'零食分类的商品别卖了\'\'这3款T恤都别卖了（A、B、C）\'', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"brand\": {\"type\": \"string\", \"description\": \"brand\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"names\"}, \"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"spuCode\"}, \"category\": {\"type\": \"string\", \"description\": \"category\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"productIds\"}}}', NULL, 'business:product:offShelf', '{\"readOnly\": false, \"outputHint\": \"返回批量下架结果：成功/跳过/失败计数，以及每条商品的名称、原价、库存、状态变更。展示为 markdown 表格 + 总结文本，失败的标红原因。\", \"destructive\": true}', 'product', 1, '1.0.0', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '上架商品。支持单个定位（商品名/编码/ID三选一），或多个商品名列表/ID列表批量。不支持按品牌/分类大范围上架（避免误上架已废弃商品），请显式列出要上架的商品。单次批量上限 50 条。这是破坏性操作，必须等用户确认后才可执行。典型触发词：\'那款T恤上架吧\'\'促销商品全部开始卖\'\'A、B、C这3款新品上架\'', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"names\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"spuCode\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"productIds\"}}}', NULL, 'business:product:onShelf', '{\"readOnly\": false, \"outputHint\": \"返回批量上架结果：成功/跳过/失败计数，以及每条商品的名称、原价、库存、状态变更。展示为 markdown 表格 + 总结文本，失败的标红原因。\", \"destructive\": true}', 'product', 1, '1.0.0', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '调整商品售价或成本。支持按商品名/编码/ID定位（三选一），至少提供新售价(newPrice)或新成本(newCost)之一，可附调价原因。会展示原价/成本→新价/成本以及差价，这是破坏性操作，必须等用户确认后才可执行。典型触发词：\'XX改成49块\'\'海天生抽改成12块，成本8.5，原因促销\'\'XX加价10块\'\'XX的成本补录成8.5\'', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"newCost\": {\"type\": \"number\", \"description\": \"newCost\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"spuCode\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"newPrice\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}}}', NULL, 'business:product:priceAdjust', '{\"readOnly\": false, \"outputHint\": \"返回改价结果：商品名、原价→新价及差价、原成本→新成本及差价。展示为结构化对比文本，金额保留2位小数。\", \"destructive\": true}', 'product', 1, '1.0.0', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '删除商品（软删除，记录仍在库里但不再出现在列表中）。按商品名/编码/ID三选一定位。这是破坏性操作，必须等用户确认后才可执行；如果只是下架，请用 off_shelf 工具（可恢复）。典型触发词：\'这款商品录错了删掉\'\'这个废弃的SKU删了\'', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"spuCode\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}}}', NULL, 'business:product:remove', '{\"readOnly\": false, \"outputHint\": \"返回删除结果（商品ID、删除条数），展示为文本提示用户删除成功。\", \"destructive\": true}', 'product', 1, '1.0.0', NULL, 'system', 'system', '2026-08-06 00:45:08', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '查询零售订单数据. 可按时间范围、门店 ID、订单状态过滤, 返回订单列表 (订单号、金额、状态、下单时间、门店). 用于回答查订单、最近订单类问题.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '查询商品库存数据. 可按商品名称、SKU、门店过滤, 返回库存列表 (商品、SKU、可用库存、门店). 用于回答查库存、库存量类问题.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '查询销售统计数据. 按日期范围返回每日销售记录 (日期、销售额、订单数、客单价). 用于回答销售趋势、本月销售额、销售对比类问题.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '通用数据库查询工具. 执行只读 SQL 查询 (仅 SELECT), 返回查询结果. 用于复杂数据查询场景.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '通用 Java 后端透传工具. 按 method+path+params 调用 Java 任意业务接口, 供业务工具封装层调用, 不直接暴露给 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '下架商品。支持两种方式：① 单个商品定位（商品名/编码/ID三选一）；② 批量圈选（按品牌名、分类名或分类ID、多个商品名列表、多个ID列表；多条件可组合AND）。单次批量上限 50 条，超过请分批。破坏性操作，必须等用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（用户说「把可口可乐下架了」）\"}, \"brand\": {\"type\": \"string\", \"description\": \"品牌名（xx品牌全部下架）\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"多个商品名列表\"}, \"reason\": {\"type\": \"string\", \"description\": \"下架原因\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"商品编码\"}, \"category\": {\"type\": \"string\", \"description\": \"分类名（零食分类下架）\"}, \"productId\": {\"type\": \"integer\", \"description\": \"商品ID（内部ID优先）\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"分类ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"多个商品ID列表\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '上架商品。支持单个定位（商品名/编码/ID三选一），或多个商品名列表/ID列表批量。不支持按品牌/分类大范围上架（避免误上架已废弃商品），请显式列出要上架的商品。单次批量上限 50 条。破坏性操作，必须等用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（那款T恤上架吧）\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '调整商品售价或成本。支持按商品名/编码/ID定位（三选一），至少提供新售价(newPrice)或新成本(newCost)之一，可附调价原因。会展示原价/成本→新价/成本以及差价，破坏性操作，必须等用户确认后才可执行。', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（XX改成49块）\"}, \"reason\": {\"type\": \"string\", \"description\": \"调价原因（促销/调货价上涨/补录）\"}, \"newCost\": {\"type\": \"number\", \"description\": \"新成本（两位小数）\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"新售价（两位小数）\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '删除商品（软删除，记录仍在库里但不再出现在列表中）。按商品名/编码/ID三选一定位。破坏性操作，必须等用户确认后才可执行；如果只是下架，请用 product:off_shelf 工具（可恢复）。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（这款录错了删掉）\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:47:45', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '鏌ヨ闆跺敭璁㈠崟鏁版嵁. 鍙寜鏃堕棿鑼冨洿銆侀棬搴� ID銆佽鍗曠姸鎬佽繃婊�, 杩斿洖璁㈠崟鍒楄〃 (璁㈠崟鍙枫�侀噾棰濄�佺姸鎬併�佷笅鍗曟椂闂淬�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ヨ鍗曘�佹渶杩戣鍗曠被闂.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '鏌ヨ鍟嗗搧搴撳瓨鏁版嵁. 鍙寜鍟嗗搧鍚嶇О銆丼KU銆侀棬搴楄繃婊�, 杩斿洖搴撳瓨鍒楄〃 (鍟嗗搧銆丼KU銆佸彲鐢ㄥ簱瀛樸�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ュ簱瀛樸�佸簱瀛橀噺绫婚棶棰�.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '鏌ヨ閿�鍞粺璁℃暟鎹�. 鎸夋棩鏈熻寖鍥磋繑鍥炴瘡鏃ラ攢鍞褰� (鏃ユ湡銆侀攢鍞銆佽鍗曟暟銆佸鍗曚环). 鐢ㄤ簬鍥炵瓟閿�鍞秼鍔裤�佹湰鏈堥攢鍞銆侀攢鍞姣旂被闂.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '閫氱敤鏁版嵁搴撴煡璇㈠伐鍏�. 鎵ц鍙 SQL 鏌ヨ (浠� SELECT), 杩斿洖鏌ヨ缁撴灉. 鐢ㄤ簬澶嶆潅鏁版嵁鏌ヨ鍦烘櫙.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '閫氱敤 Java 鍚庣閫忎紶宸ュ叿. 鎸� method+path+params 璋冪敤 Java 浠绘剰涓氬姟鎺ュ彛, 渚涗笟鍔″伐鍏峰皝瑁呭眰璋冪敤, 涓嶇洿鎺ユ毚闇茬粰 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '涓嬫灦鍟嗗搧銆傛敮鎸佷袱绉嶆柟寮忥細鈶� 鍗曚釜鍟嗗搧瀹氫綅锛堝晢鍝佸悕/缂栫爜/ID涓夐�変竴锛夛紱鈶� 鎵归噺鍦堥�夛紙鎸夊搧鐗屽悕銆佸垎绫诲悕鎴栧垎绫籌D銆佸涓晢鍝佸悕鍒楄〃銆佸涓狪D鍒楄〃锛涘鏉′欢鍙粍鍚圓ND锛夈�傚崟娆℃壒閲忎笂闄� 50 鏉★紝瓒呰繃璇峰垎鎵广�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙鐢ㄦ埛璇淬�屾妸鍙彛鍙箰涓嬫灦浜嗐�嶏級\"}, \"brand\": {\"type\": \"string\", \"description\": \"鍝佺墝鍚嶏紙xx鍝佺墝鍏ㄩ儴涓嬫灦锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"澶氫釜鍟嗗搧鍚嶅垪琛�\"}, \"reason\": {\"type\": \"string\", \"description\": \"涓嬫灦鍘熷洜\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"鍟嗗搧缂栫爜\"}, \"category\": {\"type\": \"string\", \"description\": \"鍒嗙被鍚嶏紙闆堕鍒嗙被涓嬫灦锛�\"}, \"productId\": {\"type\": \"integer\", \"description\": \"鍟嗗搧ID锛堝唴閮↖D浼樺厛锛�\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"鍒嗙被ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"澶氫釜鍟嗗搧ID鍒楄〃\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '涓婃灦鍟嗗搧銆傛敮鎸佸崟涓畾浣嶏紙鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴锛夛紝鎴栧涓晢鍝佸悕鍒楄〃/ID鍒楄〃鎵归噺銆備笉鏀寔鎸夊搧鐗�/鍒嗙被澶ц寖鍥翠笂鏋讹紙閬垮厤璇笂鏋跺凡搴熷純鍟嗗搧锛夛紝璇锋樉寮忓垪鍑鸿涓婃灦鐨勫晢鍝併�傚崟娆℃壒閲忎笂闄� 50 鏉°�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙閭ｆT鎭や笂鏋跺惂锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '璋冩暣鍟嗗搧鍞环鎴栨垚鏈�傛敮鎸佹寜鍟嗗搧鍚�/缂栫爜/ID瀹氫綅锛堜笁閫変竴锛夛紝鑷冲皯鎻愪緵鏂板敭浠�(newPrice)鎴栨柊鎴愭湰(newCost)涔嬩竴锛屽彲闄勮皟浠峰師鍥犮�備細灞曠ず鍘熶环/鎴愭湰鈫掓柊浠�/鎴愭湰浠ュ強宸环锛岀牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙XX鏀规垚49鍧楋級\"}, \"reason\": {\"type\": \"string\", \"description\": \"璋冧环鍘熷洜锛堜績閿�/璋冭揣浠蜂笂娑�/琛ュ綍锛�\"}, \"newCost\": {\"type\": \"number\", \"description\": \"鏂版垚鏈紙涓や綅灏忔暟锛�\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"鏂板敭浠凤紙涓や綅灏忔暟锛�\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '鍒犻櫎鍟嗗搧锛堣蒋鍒犻櫎锛岃褰曚粛鍦ㄥ簱閲屼絾涓嶅啀鍑虹幇鍦ㄥ垪琛ㄤ腑锛夈�傛寜鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴瀹氫綅銆傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц锛涘鏋滃彧鏄笅鏋讹紝璇风敤 product:off_shelf 宸ュ叿锛堝彲鎭㈠锛夈��', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙杩欐褰曢敊浜嗗垹鎺夛級\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:51:05', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '鏌ヨ闆跺敭璁㈠崟鏁版嵁. 鍙寜鏃堕棿鑼冨洿銆侀棬搴� ID銆佽鍗曠姸鎬佽繃婊�, 杩斿洖璁㈠崟鍒楄〃 (璁㈠崟鍙枫�侀噾棰濄�佺姸鎬併�佷笅鍗曟椂闂淬�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ヨ鍗曘�佹渶杩戣鍗曠被闂.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '鏌ヨ鍟嗗搧搴撳瓨鏁版嵁. 鍙寜鍟嗗搧鍚嶇О銆丼KU銆侀棬搴楄繃婊�, 杩斿洖搴撳瓨鍒楄〃 (鍟嗗搧銆丼KU銆佸彲鐢ㄥ簱瀛樸�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ュ簱瀛樸�佸簱瀛橀噺绫婚棶棰�.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '鏌ヨ閿�鍞粺璁℃暟鎹�. 鎸夋棩鏈熻寖鍥磋繑鍥炴瘡鏃ラ攢鍞褰� (鏃ユ湡銆侀攢鍞銆佽鍗曟暟銆佸鍗曚环). 鐢ㄤ簬鍥炵瓟閿�鍞秼鍔裤�佹湰鏈堥攢鍞銆侀攢鍞姣旂被闂.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '閫氱敤鏁版嵁搴撴煡璇㈠伐鍏�. 鎵ц鍙 SQL 鏌ヨ (浠� SELECT), 杩斿洖鏌ヨ缁撴灉. 鐢ㄤ簬澶嶆潅鏁版嵁鏌ヨ鍦烘櫙.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '閫氱敤 Java 鍚庣閫忎紶宸ュ叿. 鎸� method+path+params 璋冪敤 Java 浠绘剰涓氬姟鎺ュ彛, 渚涗笟鍔″伐鍏峰皝瑁呭眰璋冪敤, 涓嶇洿鎺ユ毚闇茬粰 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '涓嬫灦鍟嗗搧銆傛敮鎸佷袱绉嶆柟寮忥細鈶� 鍗曚釜鍟嗗搧瀹氫綅锛堝晢鍝佸悕/缂栫爜/ID涓夐�変竴锛夛紱鈶� 鎵归噺鍦堥�夛紙鎸夊搧鐗屽悕銆佸垎绫诲悕鎴栧垎绫籌D銆佸涓晢鍝佸悕鍒楄〃銆佸涓狪D鍒楄〃锛涘鏉′欢鍙粍鍚圓ND锛夈�傚崟娆℃壒閲忎笂闄� 50 鏉★紝瓒呰繃璇峰垎鎵广�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙鐢ㄦ埛璇淬�屾妸鍙彛鍙箰涓嬫灦浜嗐�嶏級\"}, \"brand\": {\"type\": \"string\", \"description\": \"鍝佺墝鍚嶏紙xx鍝佺墝鍏ㄩ儴涓嬫灦锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"澶氫釜鍟嗗搧鍚嶅垪琛�\"}, \"reason\": {\"type\": \"string\", \"description\": \"涓嬫灦鍘熷洜\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"鍟嗗搧缂栫爜\"}, \"category\": {\"type\": \"string\", \"description\": \"鍒嗙被鍚嶏紙闆堕鍒嗙被涓嬫灦锛�\"}, \"productId\": {\"type\": \"integer\", \"description\": \"鍟嗗搧ID锛堝唴閮↖D浼樺厛锛�\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"鍒嗙被ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"澶氫釜鍟嗗搧ID鍒楄〃\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '涓婃灦鍟嗗搧銆傛敮鎸佸崟涓畾浣嶏紙鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴锛夛紝鎴栧涓晢鍝佸悕鍒楄〃/ID鍒楄〃鎵归噺銆備笉鏀寔鎸夊搧鐗�/鍒嗙被澶ц寖鍥翠笂鏋讹紙閬垮厤璇笂鏋跺凡搴熷純鍟嗗搧锛夛紝璇锋樉寮忓垪鍑鸿涓婃灦鐨勫晢鍝併�傚崟娆℃壒閲忎笂闄� 50 鏉°�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙閭ｆT鎭や笂鏋跺惂锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '璋冩暣鍟嗗搧鍞环鎴栨垚鏈�傛敮鎸佹寜鍟嗗搧鍚�/缂栫爜/ID瀹氫綅锛堜笁閫変竴锛夛紝鑷冲皯鎻愪緵鏂板敭浠�(newPrice)鎴栨柊鎴愭湰(newCost)涔嬩竴锛屽彲闄勮皟浠峰師鍥犮�備細灞曠ず鍘熶环/鎴愭湰鈫掓柊浠�/鎴愭湰浠ュ強宸环锛岀牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙XX鏀规垚49鍧楋級\"}, \"reason\": {\"type\": \"string\", \"description\": \"璋冧环鍘熷洜锛堜績閿�/璋冭揣浠蜂笂娑�/琛ュ綍锛�\"}, \"newCost\": {\"type\": \"number\", \"description\": \"鏂版垚鏈紙涓や綅灏忔暟锛�\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"鏂板敭浠凤紙涓や綅灏忔暟锛�\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '鍒犻櫎鍟嗗搧锛堣蒋鍒犻櫎锛岃褰曚粛鍦ㄥ簱閲屼絾涓嶅啀鍑虹幇鍦ㄥ垪琛ㄤ腑锛夈�傛寜鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴瀹氫綅銆傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц锛涘鏋滃彧鏄笅鏋讹紝璇风敤 product:off_shelf 宸ュ叿锛堝彲鎭㈠锛夈��', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙杩欐褰曢敊浜嗗垹鎺夛級\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 00:53:20', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '鏌ヨ闆跺敭璁㈠崟鏁版嵁. 鍙寜鏃堕棿鑼冨洿銆侀棬搴� ID銆佽鍗曠姸鎬佽繃婊�, 杩斿洖璁㈠崟鍒楄〃 (璁㈠崟鍙枫�侀噾棰濄�佺姸鎬併�佷笅鍗曟椂闂淬�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ヨ鍗曘�佹渶杩戣鍗曠被闂.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '鏌ヨ鍟嗗搧搴撳瓨鏁版嵁. 鍙寜鍟嗗搧鍚嶇О銆丼KU銆侀棬搴楄繃婊�, 杩斿洖搴撳瓨鍒楄〃 (鍟嗗搧銆丼KU銆佸彲鐢ㄥ簱瀛樸�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ュ簱瀛樸�佸簱瀛橀噺绫婚棶棰�.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '鏌ヨ閿�鍞粺璁℃暟鎹�. 鎸夋棩鏈熻寖鍥磋繑鍥炴瘡鏃ラ攢鍞褰� (鏃ユ湡銆侀攢鍞銆佽鍗曟暟銆佸鍗曚环). 鐢ㄤ簬鍥炵瓟閿�鍞秼鍔裤�佹湰鏈堥攢鍞銆侀攢鍞姣旂被闂.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '閫氱敤鏁版嵁搴撴煡璇㈠伐鍏�. 鎵ц鍙 SQL 鏌ヨ (浠� SELECT), 杩斿洖鏌ヨ缁撴灉. 鐢ㄤ簬澶嶆潅鏁版嵁鏌ヨ鍦烘櫙.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '閫氱敤 Java 鍚庣閫忎紶宸ュ叿. 鎸� method+path+params 璋冪敤 Java 浠绘剰涓氬姟鎺ュ彛, 渚涗笟鍔″伐鍏峰皝瑁呭眰璋冪敤, 涓嶇洿鎺ユ毚闇茬粰 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '涓嬫灦鍟嗗搧銆傛敮鎸佷袱绉嶆柟寮忥細鈶� 鍗曚釜鍟嗗搧瀹氫綅锛堝晢鍝佸悕/缂栫爜/ID涓夐�変竴锛夛紱鈶� 鎵归噺鍦堥�夛紙鎸夊搧鐗屽悕銆佸垎绫诲悕鎴栧垎绫籌D銆佸涓晢鍝佸悕鍒楄〃銆佸涓狪D鍒楄〃锛涘鏉′欢鍙粍鍚圓ND锛夈�傚崟娆℃壒閲忎笂闄� 50 鏉★紝瓒呰繃璇峰垎鎵广�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙鐢ㄦ埛璇淬�屾妸鍙彛鍙箰涓嬫灦浜嗐�嶏級\"}, \"brand\": {\"type\": \"string\", \"description\": \"鍝佺墝鍚嶏紙xx鍝佺墝鍏ㄩ儴涓嬫灦锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"澶氫釜鍟嗗搧鍚嶅垪琛�\"}, \"reason\": {\"type\": \"string\", \"description\": \"涓嬫灦鍘熷洜\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"鍟嗗搧缂栫爜\"}, \"category\": {\"type\": \"string\", \"description\": \"鍒嗙被鍚嶏紙闆堕鍒嗙被涓嬫灦锛�\"}, \"productId\": {\"type\": \"integer\", \"description\": \"鍟嗗搧ID锛堝唴閮↖D浼樺厛锛�\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"鍒嗙被ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"澶氫釜鍟嗗搧ID鍒楄〃\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '涓婃灦鍟嗗搧銆傛敮鎸佸崟涓畾浣嶏紙鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴锛夛紝鎴栧涓晢鍝佸悕鍒楄〃/ID鍒楄〃鎵归噺銆備笉鏀寔鎸夊搧鐗�/鍒嗙被澶ц寖鍥翠笂鏋讹紙閬垮厤璇笂鏋跺凡搴熷純鍟嗗搧锛夛紝璇锋樉寮忓垪鍑鸿涓婃灦鐨勫晢鍝併�傚崟娆℃壒閲忎笂闄� 50 鏉°�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙閭ｆT鎭や笂鏋跺惂锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '璋冩暣鍟嗗搧鍞环鎴栨垚鏈�傛敮鎸佹寜鍟嗗搧鍚�/缂栫爜/ID瀹氫綅锛堜笁閫変竴锛夛紝鑷冲皯鎻愪緵鏂板敭浠�(newPrice)鎴栨柊鎴愭湰(newCost)涔嬩竴锛屽彲闄勮皟浠峰師鍥犮�備細灞曠ず鍘熶环/鎴愭湰鈫掓柊浠�/鎴愭湰浠ュ強宸环锛岀牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙XX鏀规垚49鍧楋級\"}, \"reason\": {\"type\": \"string\", \"description\": \"璋冧环鍘熷洜锛堜績閿�/璋冭揣浠蜂笂娑�/琛ュ綍锛�\"}, \"newCost\": {\"type\": \"number\", \"description\": \"鏂版垚鏈紙涓や綅灏忔暟锛�\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"鏂板敭浠凤紙涓や綅灏忔暟锛�\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '鍒犻櫎鍟嗗搧锛堣蒋鍒犻櫎锛岃褰曚粛鍦ㄥ簱閲屼絾涓嶅啀鍑虹幇鍦ㄥ垪琛ㄤ腑锛夈�傛寜鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴瀹氫綅銆傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц锛涘鏋滃彧鏄笅鏋讹紝璇风敤 product:off_shelf 宸ュ叿锛堝彲鎭㈠锛夈��', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙杩欐褰曢敊浜嗗垹鎺夛級\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:03:55', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '鏌ヨ闆跺敭璁㈠崟鏁版嵁. 鍙寜鏃堕棿鑼冨洿銆侀棬搴� ID銆佽鍗曠姸鎬佽繃婊�, 杩斿洖璁㈠崟鍒楄〃 (璁㈠崟鍙枫�侀噾棰濄�佺姸鎬併�佷笅鍗曟椂闂淬�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ヨ鍗曘�佹渶杩戣鍗曠被闂.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '鏌ヨ鍟嗗搧搴撳瓨鏁版嵁. 鍙寜鍟嗗搧鍚嶇О銆丼KU銆侀棬搴楄繃婊�, 杩斿洖搴撳瓨鍒楄〃 (鍟嗗搧銆丼KU銆佸彲鐢ㄥ簱瀛樸�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ュ簱瀛樸�佸簱瀛橀噺绫婚棶棰�.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '鏌ヨ閿�鍞粺璁℃暟鎹�. 鎸夋棩鏈熻寖鍥磋繑鍥炴瘡鏃ラ攢鍞褰� (鏃ユ湡銆侀攢鍞銆佽鍗曟暟銆佸鍗曚环). 鐢ㄤ簬鍥炵瓟閿�鍞秼鍔裤�佹湰鏈堥攢鍞銆侀攢鍞姣旂被闂.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '閫氱敤鏁版嵁搴撴煡璇㈠伐鍏�. 鎵ц鍙 SQL 鏌ヨ (浠� SELECT), 杩斿洖鏌ヨ缁撴灉. 鐢ㄤ簬澶嶆潅鏁版嵁鏌ヨ鍦烘櫙.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '閫氱敤 Java 鍚庣閫忎紶宸ュ叿. 鎸� method+path+params 璋冪敤 Java 浠绘剰涓氬姟鎺ュ彛, 渚涗笟鍔″伐鍏峰皝瑁呭眰璋冪敤, 涓嶇洿鎺ユ毚闇茬粰 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '涓嬫灦鍟嗗搧銆傛敮鎸佷袱绉嶆柟寮忥細鈶� 鍗曚釜鍟嗗搧瀹氫綅锛堝晢鍝佸悕/缂栫爜/ID涓夐�変竴锛夛紱鈶� 鎵归噺鍦堥�夛紙鎸夊搧鐗屽悕銆佸垎绫诲悕鎴栧垎绫籌D銆佸涓晢鍝佸悕鍒楄〃銆佸涓狪D鍒楄〃锛涘鏉′欢鍙粍鍚圓ND锛夈�傚崟娆℃壒閲忎笂闄� 50 鏉★紝瓒呰繃璇峰垎鎵广�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙鐢ㄦ埛璇淬�屾妸鍙彛鍙箰涓嬫灦浜嗐�嶏級\"}, \"brand\": {\"type\": \"string\", \"description\": \"鍝佺墝鍚嶏紙xx鍝佺墝鍏ㄩ儴涓嬫灦锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"澶氫釜鍟嗗搧鍚嶅垪琛�\"}, \"reason\": {\"type\": \"string\", \"description\": \"涓嬫灦鍘熷洜\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"鍟嗗搧缂栫爜\"}, \"category\": {\"type\": \"string\", \"description\": \"鍒嗙被鍚嶏紙闆堕鍒嗙被涓嬫灦锛�\"}, \"productId\": {\"type\": \"integer\", \"description\": \"鍟嗗搧ID锛堝唴閮↖D浼樺厛锛�\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"鍒嗙被ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"澶氫釜鍟嗗搧ID鍒楄〃\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '涓婃灦鍟嗗搧銆傛敮鎸佸崟涓畾浣嶏紙鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴锛夛紝鎴栧涓晢鍝佸悕鍒楄〃/ID鍒楄〃鎵归噺銆備笉鏀寔鎸夊搧鐗�/鍒嗙被澶ц寖鍥翠笂鏋讹紙閬垮厤璇笂鏋跺凡搴熷純鍟嗗搧锛夛紝璇锋樉寮忓垪鍑鸿涓婃灦鐨勫晢鍝併�傚崟娆℃壒閲忎笂闄� 50 鏉°�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙閭ｆT鎭や笂鏋跺惂锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '璋冩暣鍟嗗搧鍞环鎴栨垚鏈�傛敮鎸佹寜鍟嗗搧鍚�/缂栫爜/ID瀹氫綅锛堜笁閫変竴锛夛紝鑷冲皯鎻愪緵鏂板敭浠�(newPrice)鎴栨柊鎴愭湰(newCost)涔嬩竴锛屽彲闄勮皟浠峰師鍥犮�備細灞曠ず鍘熶环/鎴愭湰鈫掓柊浠�/鎴愭湰浠ュ強宸环锛岀牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙XX鏀规垚49鍧楋級\"}, \"reason\": {\"type\": \"string\", \"description\": \"璋冧环鍘熷洜锛堜績閿�/璋冭揣浠蜂笂娑�/琛ュ綍锛�\"}, \"newCost\": {\"type\": \"number\", \"description\": \"鏂版垚鏈紙涓や綅灏忔暟锛�\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"鏂板敭浠凤紙涓や綅灏忔暟锛�\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '鍒犻櫎鍟嗗搧锛堣蒋鍒犻櫎锛岃褰曚粛鍦ㄥ簱閲屼絾涓嶅啀鍑虹幇鍦ㄥ垪琛ㄤ腑锛夈�傛寜鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴瀹氫綅銆傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц锛涘鏋滃彧鏄笅鏋讹紝璇风敤 product:off_shelf 宸ュ叿锛堝彲鎭㈠锛夈��', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙杩欐褰曢敊浜嗗垹鎺夛級\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:05:47', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '鏌ヨ闆跺敭璁㈠崟鏁版嵁. 鍙寜鏃堕棿鑼冨洿銆侀棬搴� ID銆佽鍗曠姸鎬佽繃婊�, 杩斿洖璁㈠崟鍒楄〃 (璁㈠崟鍙枫�侀噾棰濄�佺姸鎬併�佷笅鍗曟椂闂淬�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ヨ鍗曘�佹渶杩戣鍗曠被闂.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '鏌ヨ鍟嗗搧搴撳瓨鏁版嵁. 鍙寜鍟嗗搧鍚嶇О銆丼KU銆侀棬搴楄繃婊�, 杩斿洖搴撳瓨鍒楄〃 (鍟嗗搧銆丼KU銆佸彲鐢ㄥ簱瀛樸�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ュ簱瀛樸�佸簱瀛橀噺绫婚棶棰�.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '鏌ヨ閿�鍞粺璁℃暟鎹�. 鎸夋棩鏈熻寖鍥磋繑鍥炴瘡鏃ラ攢鍞褰� (鏃ユ湡銆侀攢鍞銆佽鍗曟暟銆佸鍗曚环). 鐢ㄤ簬鍥炵瓟閿�鍞秼鍔裤�佹湰鏈堥攢鍞銆侀攢鍞姣旂被闂.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '閫氱敤鏁版嵁搴撴煡璇㈠伐鍏�. 鎵ц鍙 SQL 鏌ヨ (浠� SELECT), 杩斿洖鏌ヨ缁撴灉. 鐢ㄤ簬澶嶆潅鏁版嵁鏌ヨ鍦烘櫙.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '閫氱敤 Java 鍚庣閫忎紶宸ュ叿. 鎸� method+path+params 璋冪敤 Java 浠绘剰涓氬姟鎺ュ彛, 渚涗笟鍔″伐鍏峰皝瑁呭眰璋冪敤, 涓嶇洿鎺ユ毚闇茬粰 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '涓嬫灦鍟嗗搧銆傛敮鎸佷袱绉嶆柟寮忥細鈶� 鍗曚釜鍟嗗搧瀹氫綅锛堝晢鍝佸悕/缂栫爜/ID涓夐�変竴锛夛紱鈶� 鎵归噺鍦堥�夛紙鎸夊搧鐗屽悕銆佸垎绫诲悕鎴栧垎绫籌D銆佸涓晢鍝佸悕鍒楄〃銆佸涓狪D鍒楄〃锛涘鏉′欢鍙粍鍚圓ND锛夈�傚崟娆℃壒閲忎笂闄� 50 鏉★紝瓒呰繃璇峰垎鎵广�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙鐢ㄦ埛璇淬�屾妸鍙彛鍙箰涓嬫灦浜嗐�嶏級\"}, \"brand\": {\"type\": \"string\", \"description\": \"鍝佺墝鍚嶏紙xx鍝佺墝鍏ㄩ儴涓嬫灦锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"澶氫釜鍟嗗搧鍚嶅垪琛�\"}, \"reason\": {\"type\": \"string\", \"description\": \"涓嬫灦鍘熷洜\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"鍟嗗搧缂栫爜\"}, \"category\": {\"type\": \"string\", \"description\": \"鍒嗙被鍚嶏紙闆堕鍒嗙被涓嬫灦锛�\"}, \"productId\": {\"type\": \"integer\", \"description\": \"鍟嗗搧ID锛堝唴閮↖D浼樺厛锛�\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"鍒嗙被ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"澶氫釜鍟嗗搧ID鍒楄〃\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '涓婃灦鍟嗗搧銆傛敮鎸佸崟涓畾浣嶏紙鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴锛夛紝鎴栧涓晢鍝佸悕鍒楄〃/ID鍒楄〃鎵归噺銆備笉鏀寔鎸夊搧鐗�/鍒嗙被澶ц寖鍥翠笂鏋讹紙閬垮厤璇笂鏋跺凡搴熷純鍟嗗搧锛夛紝璇锋樉寮忓垪鍑鸿涓婃灦鐨勫晢鍝併�傚崟娆℃壒閲忎笂闄� 50 鏉°�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙閭ｆT鎭や笂鏋跺惂锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '璋冩暣鍟嗗搧鍞环鎴栨垚鏈�傛敮鎸佹寜鍟嗗搧鍚�/缂栫爜/ID瀹氫綅锛堜笁閫変竴锛夛紝鑷冲皯鎻愪緵鏂板敭浠�(newPrice)鎴栨柊鎴愭湰(newCost)涔嬩竴锛屽彲闄勮皟浠峰師鍥犮�備細灞曠ず鍘熶环/鎴愭湰鈫掓柊浠�/鎴愭湰浠ュ強宸环锛岀牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙XX鏀规垚49鍧楋級\"}, \"reason\": {\"type\": \"string\", \"description\": \"璋冧环鍘熷洜锛堜績閿�/璋冭揣浠蜂笂娑�/琛ュ綍锛�\"}, \"newCost\": {\"type\": \"number\", \"description\": \"鏂版垚鏈紙涓や綅灏忔暟锛�\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"鏂板敭浠凤紙涓や綅灏忔暟锛�\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '鍒犻櫎鍟嗗搧锛堣蒋鍒犻櫎锛岃褰曚粛鍦ㄥ簱閲屼絾涓嶅啀鍑虹幇鍦ㄥ垪琛ㄤ腑锛夈�傛寜鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴瀹氫綅銆傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц锛涘鏋滃彧鏄笅鏋讹紝璇风敤 product:off_shelf 宸ュ叿锛堝彲鎭㈠锛夈��', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙杩欐褰曢敊浜嗗垹鎺夛級\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:07:52', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '鏌ヨ闆跺敭璁㈠崟鏁版嵁. 鍙寜鏃堕棿鑼冨洿銆侀棬搴� ID銆佽鍗曠姸鎬佽繃婊�, 杩斿洖璁㈠崟鍒楄〃 (璁㈠崟鍙枫�侀噾棰濄�佺姸鎬併�佷笅鍗曟椂闂淬�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ヨ鍗曘�佹渶杩戣鍗曠被闂.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '鏌ヨ鍟嗗搧搴撳瓨鏁版嵁. 鍙寜鍟嗗搧鍚嶇О銆丼KU銆侀棬搴楄繃婊�, 杩斿洖搴撳瓨鍒楄〃 (鍟嗗搧銆丼KU銆佸彲鐢ㄥ簱瀛樸�侀棬搴�). 鐢ㄤ簬鍥炵瓟鏌ュ簱瀛樸�佸簱瀛橀噺绫婚棶棰�.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '鏌ヨ閿�鍞粺璁℃暟鎹�. 鎸夋棩鏈熻寖鍥磋繑鍥炴瘡鏃ラ攢鍞褰� (鏃ユ湡銆侀攢鍞銆佽鍗曟暟銆佸鍗曚环). 鐢ㄤ簬鍥炵瓟閿�鍞秼鍔裤�佹湰鏈堥攢鍞銆侀攢鍞姣旂被闂.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '閫氱敤鏁版嵁搴撴煡璇㈠伐鍏�. 鎵ц鍙 SQL 鏌ヨ (浠� SELECT), 杩斿洖鏌ヨ缁撴灉. 鐢ㄤ簬澶嶆潅鏁版嵁鏌ヨ鍦烘櫙.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '閫氱敤 Java 鍚庣閫忎紶宸ュ叿. 鎸� method+path+params 璋冪敤 Java 浠绘剰涓氬姟鎺ュ彛, 渚涗笟鍔″伐鍏峰皝瑁呭眰璋冪敤, 涓嶇洿鎺ユ毚闇茬粰 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '涓嬫灦鍟嗗搧銆傛敮鎸佷袱绉嶆柟寮忥細鈶� 鍗曚釜鍟嗗搧瀹氫綅锛堝晢鍝佸悕/缂栫爜/ID涓夐�変竴锛夛紱鈶� 鎵归噺鍦堥�夛紙鎸夊搧鐗屽悕銆佸垎绫诲悕鎴栧垎绫籌D銆佸涓晢鍝佸悕鍒楄〃銆佸涓狪D鍒楄〃锛涘鏉′欢鍙粍鍚圓ND锛夈�傚崟娆℃壒閲忎笂闄� 50 鏉★紝瓒呰繃璇峰垎鎵广�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙鐢ㄦ埛璇淬�屾妸鍙彛鍙箰涓嬫灦浜嗐�嶏級\"}, \"brand\": {\"type\": \"string\", \"description\": \"鍝佺墝鍚嶏紙xx鍝佺墝鍏ㄩ儴涓嬫灦锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"澶氫釜鍟嗗搧鍚嶅垪琛�\"}, \"reason\": {\"type\": \"string\", \"description\": \"涓嬫灦鍘熷洜\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"鍟嗗搧缂栫爜\"}, \"category\": {\"type\": \"string\", \"description\": \"鍒嗙被鍚嶏紙闆堕鍒嗙被涓嬫灦锛�\"}, \"productId\": {\"type\": \"integer\", \"description\": \"鍟嗗搧ID锛堝唴閮↖D浼樺厛锛�\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"鍒嗙被ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"澶氫釜鍟嗗搧ID鍒楄〃\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '涓婃灦鍟嗗搧銆傛敮鎸佸崟涓畾浣嶏紙鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴锛夛紝鎴栧涓晢鍝佸悕鍒楄〃/ID鍒楄〃鎵归噺銆備笉鏀寔鎸夊搧鐗�/鍒嗙被澶ц寖鍥翠笂鏋讹紙閬垮厤璇笂鏋跺凡搴熷純鍟嗗搧锛夛紝璇锋樉寮忓垪鍑鸿涓婃灦鐨勫晢鍝併�傚崟娆℃壒閲忎笂闄� 50 鏉°�傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙閭ｆT鎭や笂鏋跺惂锛�\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '璋冩暣鍟嗗搧鍞环鎴栨垚鏈�傛敮鎸佹寜鍟嗗搧鍚�/缂栫爜/ID瀹氫綅锛堜笁閫変竴锛夛紝鑷冲皯鎻愪緵鏂板敭浠�(newPrice)鎴栨柊鎴愭湰(newCost)涔嬩竴锛屽彲闄勮皟浠峰師鍥犮�備細灞曠ず鍘熶环/鎴愭湰鈫掓柊浠�/鎴愭湰浠ュ強宸环锛岀牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц銆�', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙XX鏀规垚49鍧楋級\"}, \"reason\": {\"type\": \"string\", \"description\": \"璋冧环鍘熷洜锛堜績閿�/璋冭揣浠蜂笂娑�/琛ュ綍锛�\"}, \"newCost\": {\"type\": \"number\", \"description\": \"鏂版垚鏈紙涓や綅灏忔暟锛�\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"鏂板敭浠凤紙涓や綅灏忔暟锛�\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '鍒犻櫎鍟嗗搧锛堣蒋鍒犻櫎锛岃褰曚粛鍦ㄥ簱閲屼絾涓嶅啀鍑虹幇鍦ㄥ垪琛ㄤ腑锛夈�傛寜鍟嗗搧鍚�/缂栫爜/ID涓夐�変竴瀹氫綅銆傜牬鍧忔�ф搷浣滐紝蹇呴』绛夌敤鎴风‘璁ゅ悗鎵嶅彲鎵ц锛涘鏋滃彧鏄笅鏋讹紝璇风敤 product:off_shelf 宸ュ叿锛堝彲鎭㈠锛夈��', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"鍟嗗搧鍚嶏紙杩欐褰曢敊浜嗗垹鎺夛級\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 01:08:38', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order_query', '查询零售订单数据. 可按时间范围、门店 ID、订单状态过滤, 返回订单列表 (订单号、金额、状态、下单时间、门店). 用于回答查订单、最近订单类问题.', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"default\": 1, \"minimum\": 1}, \"status\": {\"enum\": [\"PENDING\", \"PAID\", \"SHIPPED\", \"COMPLETED\", \"CLOSED\"], \"type\": \"string\"}, \"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"storeId\": {\"type\": \"string\"}, \"pageSize\": {\"type\": \"integer\", \"default\": 20, \"maximum\": 100, \"minimum\": 1}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, 'business:order:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'inventory_check', '查询商品库存数据. 可按商品名称、SKU、门店过滤, 返回库存列表 (商品、SKU、可用库存、门店). 用于回答查库存、库存量类问题.', '{\"type\": \"object\", \"properties\": {\"skuCode\": {\"type\": \"string\"}, \"storeId\": {\"type\": \"string\"}, \"productName\": {\"type\": \"string\"}}}', NULL, 'business:stock:query', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'sales_analysis', '查询销售统计数据. 按日期范围返回每日销售记录 (日期、销售额、订单数、客单价). 用于回答销售趋势、本月销售额、销售对比类问题.', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}, \"startDate\": {\"type\": \"string\", \"description\": \"YYYY-MM-DD\"}}}', NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'business', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'db_query', '通用数据库查询工具. 执行只读 SQL 查询 (仅 SELECT), 返回查询结果. 用于复杂数据查询场景.', NULL, NULL, '', '{\"readOnlyHint\": true, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": false}', 'db', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'java_backend', '通用 Java 后端透传工具. 按 method+path+params 调用 Java 任意业务接口, 供业务工具封装层调用, 不直接暴露给 LLM.', NULL, NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": true, \"idempotentHint\": false, \"destructiveHint\": false}', 'java', 0, 'inactive', NULL, 'system', 'system', '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:off_shelf', '下架商品。支持两种方式：① 单个商品定位（商品名/编码/ID三选一）；② 批量圈选（按品牌名、分类名或分类ID、多个商品名列表、多个ID列表；多条件可组合AND）。单次批量上限 50 条，超过请分批。破坏性操作，必须等用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（用户说「把可口可乐下架了」）\"}, \"brand\": {\"type\": \"string\", \"description\": \"品牌名（xx品牌全部下架）\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"多个商品名列表\"}, \"reason\": {\"type\": \"string\", \"description\": \"下架原因\"}, \"spuCode\": {\"type\": \"string\", \"description\": \"商品编码\"}, \"category\": {\"type\": \"string\", \"description\": \"分类名（零食分类下架）\"}, \"productId\": {\"type\": \"integer\", \"description\": \"商品ID（内部ID优先）\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"分类ID\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}, \"description\": \"多个商品ID列表\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:on_shelf', '上架商品。支持单个定位（商品名/编码/ID三选一），或多个商品名列表/ID列表批量。不支持按品牌/分类大范围上架（避免误上架已废弃商品），请显式列出要上架的商品。单次批量上限 50 条。破坏性操作，必须等用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（那款T恤上架吧）\"}, \"names\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}, \"productIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:price_adjust', '调整商品售价或成本。支持按商品名/编码/ID定位（三选一），至少提供新售价(newPrice)或新成本(newCost)之一，可附调价原因。会展示原价/成本→新价/成本以及差价，破坏性操作，必须等用户确认后才可执行。', '{\"type\": \"object\", \"required\": [], \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（XX改成49块）\"}, \"reason\": {\"type\": \"string\", \"description\": \"调价原因（促销/调货价上涨/补录）\"}, \"newCost\": {\"type\": \"number\", \"description\": \"新成本（两位小数）\"}, \"spuCode\": {\"type\": \"string\"}, \"newPrice\": {\"type\": \"number\", \"description\": \"新售价（两位小数）\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'product:delete', '删除商品（软删除，记录仍在库里但不再出现在列表中）。按商品名/编码/ID三选一定位。破坏性操作，必须等用户确认后才可执行；如果只是下架，请用 product:off_shelf 工具（可恢复）。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"商品名（这款录错了删掉）\"}, \"spuCode\": {\"type\": \"string\"}, \"productId\": {\"type\": \"integer\"}}}', NULL, '', '{\"readOnlyHint\": false, \"openWorldHint\": false, \"idempotentHint\": true, \"destructiveHint\": true}', 'business', 1, '1.0.0', NULL, 'system', NULL, '2026-08-06 02:13:37', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:count', '商品盘点，录入实盘数量，系统自动对比账面库存计算盘盈盘亏并调整库存。按商品名称/SKU编码定位，填实盘数量。典型触发词：\'盘点海天生抽，账面10实盘8，盘亏2\'\'XX盘亏了\'\'XX实际比账面多了3件，盘盈\'破坏性操作，会按盘差调整库存余量，需用户确认后才执行。', '{\"type\": \"object\", \"properties\": {\"skuId\": {\"type\": \"integer\", \"description\": \"skuId\"}, \"remark\": {\"type\": \"string\", \"description\": \"remark\"}, \"bookQty\": {\"type\": \"integer\", \"description\": \"bookQty\"}, \"skuCode\": {\"type\": \"string\", \"description\": \"skuCode\"}, \"actualQty\": {\"type\": \"integer\", \"description\": \"actualQty\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, 'business:stock:adjust', '{\"readOnly\": false, \"outputHint\": \"返回盘点结果，包含商品名称、账面数量、实盘数量、盘差、结果类型(盘盈/盘亏/平账)、调整后库存。展示为文本。\", \"destructive\": true}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:safety_set', '设置商品安全库存阈值，用于缺货预警判断。按商品名称/SKU编码定位，填新的安全库存数值(非负整数)。典型触发词：\'把海天生抽的安全库存设为50\'\'XX安全库存调成100\'破坏性操作，会改变缺货预警标准，需用户确认后才执行。', '{\"type\": \"object\", \"properties\": {\"skuId\": {\"type\": \"integer\", \"description\": \"skuId\"}, \"skuCode\": {\"type\": \"string\", \"description\": \"skuCode\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"storeName\": {\"type\": \"string\", \"description\": \"storeName\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}, \"safetyStock\": {\"type\": \"integer\", \"description\": \"safetyStock\"}}}', NULL, 'business:stock:adjust', '{\"readOnly\": false, \"outputHint\": \"返回更新后的库存账户，包含商品名称、新的安全库存、当前可用库存、是否低于安全库存。展示为文本。\", \"destructive\": true}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:outbound', '商品出库，减少库存。按商品名称/SKU编码定位，出库数量必须为正整数且不超过现有可用库存，可填业务来源。业务来源为整数code：1订单/2采购/3调整/4退款/5手工，默认5手工，必须传数字，如手工出库传bizType=5。典型触发词：\'领用10件矿泉水做活动\'\'XX报废出库\'破坏性操作，会直接扣减库存余量，需用户确认后才执行。', '{\"type\": \"object\", \"properties\": {\"qty\": {\"type\": \"integer\", \"description\": \"qty\"}, \"bizNo\": {\"type\": \"string\", \"description\": \"bizNo\"}, \"skuId\": {\"type\": \"integer\", \"description\": \"skuId\"}, \"remark\": {\"type\": \"string\", \"description\": \"remark\"}, \"bizType\": {\"type\": \"integer\", \"description\": \"bizType（枚举合法值: 1=订单业务|2=采购入库|3=手动调整|4=退款回滚|5=手工操作）\"}, \"skuCode\": {\"type\": \"string\", \"description\": \"skuCode\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, 'business:stock:adjust', '{\"readOnly\": false, \"outputHint\": \"返回出库确认信息，包含商品名称、出库数量、业务来源。展示为文本。\", \"destructive\": true}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:transfer', '门店间调拨商品库存。按商品名称/SKU编码定位，指定调拨数量、源门店名称和目标门店名称，系统从源门店出库并给目标门店入库（同一单据号关联两笔流水）。典型触发词：\'从城西店调10件海天生抽到滨江店\'\'把XX从城北店调5件到萧山店\'破坏性操作，会同时改变两个门店库存，需用户确认后才执行。', '{\"type\": \"object\", \"properties\": {\"qty\": {\"type\": \"integer\", \"description\": \"qty\"}, \"skuId\": {\"type\": \"integer\", \"description\": \"skuId\"}, \"remark\": {\"type\": \"string\", \"description\": \"remark\"}, \"skuCode\": {\"type\": \"string\", \"description\": \"skuCode\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}, \"toStoreName\": {\"type\": \"string\", \"description\": \"toStoreName\"}, \"fromStoreName\": {\"type\": \"string\", \"description\": \"fromStoreName\"}}}', NULL, 'business:stock:adjust', '{\"readOnly\": false, \"outputHint\": \"返回调拨结果，包含商品名称、源门店、目标门店、调拨数量、源门店调拨后持仓、目标门店调拨后持仓、调拨单号。展示为文本。\", \"destructive\": true}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:inbound', '商品入库，增加库存。按商品名称/SKU编码定位，入库数量必须为正整数，可填采购单号与业务来源。业务来源为整数code：1订单/2采购/3调整/4退款/5手工，默认2采购，必须传数字，如采购入库传bizType=2。典型触发词：\'这批采购到了，海天生抽入库50件\'\'XX补货入库\'\'退货的商品入库\'破坏性操作，会直接增加库存余量，需用户确认后才执行。', '{\"type\": \"object\", \"properties\": {\"qty\": {\"type\": \"integer\", \"description\": \"qty\"}, \"bizNo\": {\"type\": \"string\", \"description\": \"bizNo\"}, \"skuId\": {\"type\": \"integer\", \"description\": \"skuId\"}, \"remark\": {\"type\": \"string\", \"description\": \"remark\"}, \"bizType\": {\"type\": \"integer\", \"description\": \"bizType（枚举合法值: 1=订单业务|2=采购入库|3=手动调整|4=退款回滚|5=手工操作）\"}, \"skuCode\": {\"type\": \"string\", \"description\": \"skuCode\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}}}', NULL, 'business:stock:adjust', '{\"readOnly\": false, \"outputHint\": \"返回入库确认信息，包含商品名称、入库数量、业务来源。展示为文本。\", \"destructive\": true}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'stock:movement', '查询库存出入库流水明细。支持按商品名称、SKU编码、门店名称、变动类型、业务来源、单据号、时间范围过滤。可分页。变动类型为整数code：1入库/2出库/3调整/4锁定/5释放/6盘盈/7盘亏，必须传数字，如查入库流水传movementType=1；业务来源为整数code：1订单/2采购/3调整/4退款/5手工，必须传数字，如查采购流水传bizType=2。典型触发词：\'海天生抽的库存为什么变了\'\'xx商品最近的出入库记录\'\'采购单xxx的入库流水\'', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"bizNo\": {\"type\": \"string\", \"description\": \"bizNo\"}, \"bizType\": {\"type\": \"integer\", \"description\": \"bizType（枚举合法值: 1=订单业务|2=采购入库|3=手动调整|4=退款回滚|5=手工操作）\"}, \"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"skuCode\": {\"type\": \"string\", \"description\": \"skuCode\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"storeName\": {\"type\": \"string\", \"description\": \"storeName\"}, \"productName\": {\"type\": \"string\", \"description\": \"productName\"}, \"movementType\": {\"type\": \"integer\", \"description\": \"movementType（枚举合法值: 1=入库|2=出库|3=手动调整|4=锁定|5=释放|6=盘盈|7=盘亏）\"}}}', NULL, 'business:stock:movement', '{\"readOnly\": true, \"outputHint\": \"返回流水列表，包含商品名称、变动类型(入库/出库/调整/盘盈/盘亏)、变动数量、变动前后库存、业务来源、单据号、时间。展示为 markdown 表格，按时间倒序。\", \"destructive\": false}', 'stock', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'order:update', '修改订单信息。支持修改订单备注、收货人姓名、收货电话、收货地址。支持按订单ID或订单号定位订单。未提供的字段保持不变。用于回答\'把订单XX的备注改成YY\'\'修改订单XX的收货地址为ZZ\'等问题。此操作会修改订单信息，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"remark\": {\"type\": \"string\", \"description\": \"remark\"}, \"orderId\": {\"type\": \"integer\", \"description\": \"orderId\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}, \"payType\": {\"type\": \"integer\", \"description\": \"payType（枚举合法值: 1=微信支付|2=支付宝|3=余额支付|4=现金）\"}, \"receiverName\": {\"type\": \"string\", \"description\": \"receiverName\"}, \"receiverPhone\": {\"type\": \"string\", \"description\": \"receiverPhone\"}, \"receiverAddress\": {\"type\": \"string\", \"description\": \"receiverAddress\"}}}', NULL, 'business:order:edit', '{\"readOnly\": false, \"outputHint\": \"返回修改结果，包含 success、message、updated 行数。展示为文本，提示用户订单信息已更新。\", \"destructive\": true}', 'order', 1, '1.0.0', NULL, 'system', 'system', '2026-08-06 06:59:10', NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'member:sleeping', '识别沉睡会员。返回超过指定天数（如90天）未活跃/未消费的会员列表。可分页。用于回答\'有哪些超90天没消费的会员\'\'沉睡会员名单\'\'多少天没来的会员\'等问题。', '{\"type\": \"object\", \"properties\": {\"days\": {\"type\": \"integer\", \"description\": \"days\"}, \"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}}}', NULL, 'business:member:query', '{\"readOnly\": true, \"outputHint\": \"返回沉睡会员列表，包含会员姓名、手机号、等级、最后活跃时间。展示为 markdown 表格，突出最后活跃时间以便判断沉睡程度。\", \"destructive\": false}', 'member', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'member:update', '更新会员资料。支持修改会员姓名、手机号。支持按会员ID、会员姓名或手机号定位会员。未提供的字段保持不变。用于回答\'把会员王五的手机号改成13900000000\'\'把张三的名字改成李四\'等问题。此操作会修改会员资料，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"newName\": {\"type\": \"string\", \"description\": \"newName\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"newPhone\": {\"type\": \"string\", \"description\": \"newPhone\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}}}', NULL, 'business:member:edit', '{\"readOnly\": false, \"outputHint\": \"返回更新后的会员信息，包含会员ID、姓名、手机号、等级。展示为结构化文本，提示用户会员资料已更新。\", \"destructive\": true}', 'member', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'member:orders', '查询会员的历史订单。支持按会员ID、会员姓名或手机号定位会员，返回该会员的历史订单列表（订单号、金额、状态、下单时间）。可分页。用于回答\'会员王五最近买过什么\'\'查一下张三的订单记录\'等问题。', '{\"type\": \"object\", \"properties\": {\"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}}}', NULL, 'business:member:query', '{\"readOnly\": true, \"outputHint\": \"返回会员历史订单列表，包含订单号、金额、状态、下单时间。展示为 markdown 表格，金额保留 2 位小数。\", \"destructive\": false}', 'member', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'member:create', '新增会员（办卡建档）。需要会员姓名，可选填手机号、会员等级（会员等级为整数code：1普通/2银卡/3金卡/4钻石，必须传数字，如金卡传3）、初始积分、标签ID列表。手机号租户内唯一，重复会被拒绝。用于回答\'给张三办一张会员卡\'\'新增会员张三 13812345678\'\'新来顾客录一下，办张金卡\'等问题。此操作会新增会员记录，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"level\": {\"type\": \"integer\", \"description\": \"level（枚举合法值: 1=普通|2=银卡|3=金卡|4=钻石）\"}, \"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"points\": {\"type\": \"integer\", \"description\": \"points\"}, \"tagIds\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}, \"description\": \"tagIds\"}}}', NULL, 'business:member:add', '{\"readOnly\": false, \"outputHint\": \"返回新增的会员信息，包含会员ID、姓名、手机号、等级、积分。展示为结构化文本，提示用户会员已建档成功。\", \"destructive\": true}', 'member', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'member:level_adjust', '调整会员等级。支持将会员升级或降级为普通(1)/银卡(2)/金卡(3)/钻石(4)。目标等级必须传整数code，如升金卡传newLevel=3。支持按会员ID、会员姓名或手机号定位会员。用于回答\'把李四升到金卡\'\'给王五降为普通会员\'\'修改会员张三的等级为钻石\'等问题。此操作会修改会员等级，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"phone\": {\"type\": \"string\", \"description\": \"phone\"}, \"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"newLevel\": {\"type\": \"integer\", \"description\": \"newLevel\"}, \"memberName\": {\"type\": \"string\", \"description\": \"memberName\"}}}', NULL, 'business:member:edit', '{\"readOnly\": false, \"outputHint\": \"返回调整后的会员信息，包含会员ID、姓名、等级。展示为结构化文本，提示用户会员等级已调整。\", \"destructive\": true}', 'member', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'review:reject', '拒审/隐藏商品评价。将评价状态置为已拒绝，前台不再展示，用于屏蔽恶意或违规差评。需要评价ID，可附拒审原因。此操作会改变评价可见性且不可直接恢复，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"reviewId\": {\"type\": \"integer\", \"description\": \"reviewId\"}}}', NULL, 'business:review:audit', '{\"readOnly\": false, \"outputHint\": \"返回拒审结果，包含评价ID、拒审状态。展示为文本，提示用户该评价已拒审/隐藏。\", \"destructive\": true}', 'review', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'review:delete', '删除商品评价（软删除，记录仍在库里但不再出现在评价列表）。按评价ID定位。此操作不可撤销，需要用户确认后才可执行；如果只想屏蔽可以先用 reject 工具。', '{\"type\": \"object\", \"properties\": {\"reviewId\": {\"type\": \"integer\", \"description\": \"reviewId\"}}}', NULL, 'business:review:delete', '{\"readOnly\": false, \"outputHint\": \"返回删除结果，包含评价ID、删除状态。展示为文本，提示用户该评价已删除。\", \"destructive\": true}', 'review', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'points:rule', '查看或修改当前租户的积分规则（1元=N积分）。action=get 查看当前规则；action=set 时需提供 rate（1元换取多少积分），修改积分规则需用户确认后才可执行。用于回答\'1元能攒多少积分帮我看看规则\'\'现在一元能得多少分\'\'把积分规则改成1元10分\'。', '{\"type\": \"object\", \"properties\": {\"rate\": {\"type\": \"integer\", \"description\": \"rate\"}, \"action\": {\"type\": \"string\", \"description\": \"action\"}}}', NULL, 'business:points:edit', '{\"readOnly\": false, \"outputHint\": \"返回当前积分规则（1元=N积分）。action=set 时返回修改后的规则。展示为简洁文本。\", \"destructive\": true}', 'points', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:refund_analysis', '查询退款分析。返回退款总金额、笔数、全额与部分退款占比。支持时间范围、门店过滤。用于回答\'最近退款多不多\'\'退款率怎么样\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:order', '{\"readOnly\": true, \"outputHint\": \"返回退款分析，包含退款总额、退款笔数、全额/部分退款占比。展示为结构化文本。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:member_rfm', '查询会员RFM分群。基于最近购买时间、频次、金额将会员分为8类。支持时间范围过滤。用于回答\'我的会员是什么样的\'\'有哪些高价值会员\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:member', '{\"readOnly\": true, \"outputHint\": \"返回RFM分群列表，包含分群名称、会员数、占比。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:order_funnel', '查询订单转化漏斗。按订单状态阶段统计订单数与转化率。支持时间范围、门店过滤。用于回答\'订单转化率怎么样\'\'各阶段订单数\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:order', '{\"readOnly\": true, \"outputHint\": \"返回订单漏斗列表，包含阶段、订单数、转化率。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:store_compare', '查询门店销售对比。按门店维度聚合销售额、订单数、客单价。支持时间范围过滤。用于回答\'各门店业绩对比\'\'哪个店卖得好\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:sales', '{\"readOnly\": true, \"outputHint\": \"返回门店销售对比列表，包含门店名、销售额、订单数、客单价。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:end', '提前结束促销活动。支持按活动ID或活动名称定位，将活动状态置为已结束并立即失效，若活动原定时间未到也会提前终止。用于回答\'活动结束了帮我结束它\'\'提前终止XX活动\'等场景。此操作会结束促销活动，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"promotionId\": {\"type\": \"integer\", \"description\": \"promotionId\"}}}', NULL, 'business:promotion:edit', '{\"readOnly\": false, \"outputHint\": \"返回结束结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已提前结束。\", \"destructive\": true}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:member_level_dist', '查询会员等级分布。按普通/银卡/金卡/钻石统计人数与占比。用于回答\'金卡会员有多少\'\'会员等级分布\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:member', '{\"readOnly\": true, \"outputHint\": \"返回会员等级分布列表，包含等级、人数、占比。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'refund:analysis', '查询退款分析。返回退款总金额、退款笔数、全额退款笔数、部分退款笔数、平均退款金额。支持按时间范围过滤。用于回答\'最近退款率怎么这么高\'\'这个月退款情况怎么样\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"orderNo\": {\"type\": \"string\", \"description\": \"orderNo\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}}}', NULL, 'business:report:order', '{\"readOnly\": true, \"outputHint\": \"返回退款分析，包含退款总金额、退款笔数、全额/部分退款笔数、平均退款金额。展示为结构化文本，金额保留 2 位小数。\", \"destructive\": false}', 'refund', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:pay_type_dist', '查询支付方式分布。按微信/支付宝/余额/现金聚合金额与占比。支持时间范围、门店过滤。用于回答\'用户都用什么支付\'\'支付方式分布\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:finance', '{\"readOnly\": true, \"outputHint\": \"返回支付方式分布列表，包含支付方式、金额、占比。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:disable', '停用优惠券模板。支持按券名或券ID定位，停用后该券不可再发放/领取，但对已发出的券无影响。此操作会修改优惠券模板状态，需要用户确认后才可执行。用于回答\'这张券先停发\'\'别再发这张券了\'等问题。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"couponId\": {\"type\": \"integer\", \"description\": \"couponId\"}}}', NULL, 'business:coupon:edit', '{\"readOnly\": false, \"outputHint\": \"返回停用的优惠券模板详情，包含名称、类型、面额、状态。展示为文本，提示用户优惠券模板已停用。\", \"destructive\": true}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'refund:cancel', '撤销待审核退款单。支持按退款单ID或退款单号定位退款单。仅待审核(PENDING)状态的退款单可撤销，撤销后退款单进入已撤销状态，关联订单从退款中恢复原状态。用于回答\'撤销王五那个填错的退款单\'\'把刚才建错的退款单撤了\'等问题。此操作会改变退款单和订单状态，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"refundId\": {\"type\": \"integer\", \"description\": \"refundId\"}, \"refundNo\": {\"type\": \"string\", \"description\": \"refundNo\"}}}', NULL, 'business:refund:edit', '{\"readOnly\": false, \"outputHint\": \"返回撤销后的退款单信息，包含退款单号、原订单号、退款金额、状态(已撤销)。展示为结构化文本，金额保留 2 位小数。\", \"destructive\": true}', 'refund', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:slow_moving', '查询滞销商品。返回指定时间范围内无出库动销的商品。支持时间范围、门店、分类过滤。用于回答\'哪些商品滞销\'\'压货的有哪些\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:inventory', '{\"readOnly\": true, \"outputHint\": \"返回滞销商品列表，包含商品、滞销天数、当前库存。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:redeem_records', '查询优惠券核销记录明细。支持按券名或券ID、会员、状态(未使用/已核销/已过期/已退)过滤，分页返回已发放券的领用与核销明细。用于回答\'这张券发给了哪些人\'\'看看上周那张券核销了多少\'\'这个会员名下的券\'等问题。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"page\": {\"type\": \"integer\", \"description\": \"page\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"couponId\": {\"type\": \"integer\", \"description\": \"couponId\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"pageSize\": {\"type\": \"integer\", \"description\": \"pageSize\"}}}', NULL, 'business:coupon:query', '{\"readOnly\": true, \"outputHint\": \"返回优惠券核销记录分页列表，包含券名、会员、状态、面额、领取时间、核销时间、核销订单号。展示为 markdown 表格，并提示总条数。\", \"destructive\": false}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:stock_alerts', '查询缺货预警。返回可用库存低于安全库存阈值的商品。支持门店、分类过滤。用于回答\'哪些商品缺货\'\'库存不足预警\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:inventory', '{\"readOnly\": true, \"outputHint\": \"返回缺货预警列表，包含商品、可用库存、安全库存。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:sales_summary', '查询销售汇总。返回总销售额(GMV)、订单数、客单价、退款率、优惠金额。支持时间范围、门店、分类、商品维度过滤。用于回答\'今天卖了多少\'\'这个月业绩怎么样\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:sales', '{\"readOnly\": true, \"outputHint\": \"返回销售汇总，包含总销售额、订单数、客单价、退款率、优惠金额。展示为结构化文本，金额保留 2 位小数。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:disable', '停用促销活动。支持按活动ID或活动名称定位，将活动状态置为未开始，使活动暂停不再进行。用于回答\'这个活动先停用\'\'把XX活动暂停\'等场景。此操作会停用促销活动，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"promotionId\": {\"type\": \"integer\", \"description\": \"promotionId\"}}}', NULL, 'business:promotion:edit', '{\"readOnly\": false, \"outputHint\": \"返回停用结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已停用。\", \"destructive\": true}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:aov', '查询客单价分析。返回 GMV、订单数、客单价、退款率、平均订单商品数。支持时间范围、门店过滤。用于回答\'客单价多少\'\'平均每单多少钱\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:order', '{\"readOnly\": true, \"outputHint\": \"返回客单价分析，包含GMV、订单数、客单价、退款率、平均商品数。展示为结构化文本。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:redeem_stats', '查询优惠券核销统计。按券模板统计发放数、已核销数及核销率，支持券名、券类型、时间范围过滤。用于回答\'看看上周那张券核销了多少\'\'这个月的券核销率怎么样\'\'哪些券用得多\'等问题。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"type\": {\"type\": \"integer\", \"description\": \"type（枚举合法值: 1=优惠券|2=折扣|3=限时秒杀|1=满减券|2=折扣券|3=代金券|1=全额退款|2=部分退款|1=正常订单|2=闪购订单|3=秒杀订单|1=全部|2=商品|3=分类|1=领取后N天有效|2=固定时间段有效）\"}, \"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}}}', NULL, 'business:report:coupon', '{\"readOnly\": true, \"outputHint\": \"返回优惠券核销统计列表，包含券名、发放数、已核销数、核销率(百分比)。展示为 markdown 表格，核销率保留 2 位小数。\", \"destructive\": false}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'coupon:enable', '启用优惠券模板。支持按券名或券ID定位，启用后该券恢复可发放/领取。此操作会修改优惠券模板状态，需要用户确认后才可执行。用于回答\'恢复这张券的发放\'\'把满减20元券重新启用\'等问题。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"couponId\": {\"type\": \"integer\", \"description\": \"couponId\"}}}', NULL, 'business:coupon:edit', '{\"readOnly\": false, \"outputHint\": \"返回启用的优惠券模板详情，包含名称、类型、面额、状态。展示为文本，提示用户优惠券模板已启用。\", \"destructive\": true}', 'coupon', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:enable', '启用促销活动。支持按活动ID或活动名称定位，将活动状态置为进行中即开始生效。用于回答\'把XX活动启用\'\'这个活动开始上线\'等场景。此操作会启用促销活动，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"promotionId\": {\"type\": \"integer\", \"description\": \"promotionId\"}}}', NULL, 'business:promotion:edit', '{\"readOnly\": false, \"outputHint\": \"返回启用结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已启用。\", \"destructive\": true}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'promotion:update', '更新促销活动基础信息或时间。支持按活动ID或活动名称定位，可修改活动名称、状态、开始时间、结束时间、折扣规则等字段（未提供的字段保持不变）。用于回答\'把XX活动的结束时间改到月底\'\'修改XX活动名称\'等场景。此操作会更新促销活动，需要用户确认后才可执行。', '{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\", \"description\": \"name\"}, \"rules\": {\"type\": \"object\", \"description\": \"rules\"}, \"status\": {\"type\": \"integer\", \"description\": \"status（枚举合法值: 1=上架|0=下架|1=上架|0=下架|1=启用|0=停用|1=草稿|2=已发布|3=失效|4=归档）\"}, \"endTime\": {\"type\": \"string\", \"format\": \"date-time\", \"description\": \"endTime\"}, \"newName\": {\"type\": \"string\", \"description\": \"newName\"}, \"startTime\": {\"type\": \"string\", \"format\": \"date-time\", \"description\": \"startTime\"}, \"promotionId\": {\"type\": \"integer\", \"description\": \"promotionId\"}}}', NULL, 'business:promotion:edit', '{\"readOnly\": false, \"outputHint\": \"返回更新结果，包含成功标志、提示消息、更新行数。展示为文本，提示用户促销活动已更新。\", \"destructive\": true}', 'promotion', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:category_sales', '查询分类销售占比。按商品分类维度聚合销售额与占比。支持时间范围、门店过滤。用于回答\'哪个分类卖得多\'\'分类销售占比\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:sales', '{\"readOnly\": true, \"outputHint\": \"返回分类销售列表，包含分类名、销售额、占比。展示为 markdown 表格，按占比降序。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:product_rank', '查询商品销售排行。按商品维度聚合销量与销售额并排序。支持时间范围、门店、分类过滤。用于回答\'哪些商品卖得最好\'\'销量Top10\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:sales', '{\"readOnly\": true, \"outputHint\": \"返回商品销售排行列表，包含商品名、销量、销售额、占比。展示为 markdown 表格，按销售额降序。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'points:redeem', '积分兑换。按会员手机号或会员ID定位会员，扣减其积分余额并写入负数积分流水，需提供兑换积分数量、兑换原因。此操作会直接扣减会员积分，需要用户确认后才可执行。用于回答\'用积分换券\'\'把他的积分兑换掉100分\'\'为他兑换50积分\'。', '{\"type\": \"object\", \"properties\": {\"points\": {\"type\": \"integer\", \"description\": \"points\"}, \"reason\": {\"type\": \"string\", \"description\": \"reason\"}, \"memberId\": {\"type\": \"integer\", \"description\": \"memberId\"}, \"memberPhone\": {\"type\": \"string\", \"description\": \"memberPhone\"}}}', NULL, 'business:points:edit', '{\"readOnly\": false, \"outputHint\": \"返回积分流水，包含变动类型、变动积分、变动前余额、变动后余额、兑换原因。展示为文本，提示用户积分已兑换。\", \"destructive\": true}', 'points', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:sales_trend', '查询销售趋势。按日聚合销售金额与订单数，返回趋势序列。支持时间范围、门店、分类过滤。用于回答\'本月销售走势\'\'最近30天销售\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:sales', '{\"readOnly\": true, \"outputHint\": \"返回销售趋势列表，包含日期、销售额、订单数。展示为文本序列或表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:coupon_roi', '查询营销ROI。按券模板统计折扣金额与带来销售额，计算投入产出比。支持时间范围过滤。用于回答\'发的券值不值\'\'营销ROI\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:coupon', '{\"readOnly\": true, \"outputHint\": \"返回营销ROI列表，包含券名、折扣金额、带来销售额、ROI。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:finance_summary', '查询财务汇总。返回总收入、退款金额、净收入、优惠金额、订单数。支持时间范围、门店过滤。用于回答\'赚了多少\'\'财务汇总\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:finance', '{\"readOnly\": true, \"outputHint\": \"返回财务汇总，包含总收入、退款金额、净收入、优惠金额、订单数。展示为结构化文本，金额保留 2 位小数。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:coupon_redeem', '查询优惠券核销率。按券模板统计发放数、已使用数及核销率。支持时间范围过滤。用于回答\'发的券核销了多少\'\'券核销率\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:coupon', '{\"readOnly\": true, \"outputHint\": \"返回优惠券核销率列表，包含券名、发放数、已用数、核销率。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:member_growth', '查询会员增长趋势。按日统计新增会员数与活跃会员数。支持时间范围过滤。用于回答\'会员增长怎么样\'\'最近新增多少会员\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:member', '{\"readOnly\": true, \"outputHint\": \"返回会员增长趋势列表，包含日期、新增会员数、活跃会员数。展示为文本序列或表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:inventory_turnover', '查询库存周转率。按商品维度计算出库成本与平均库存价值的比值。支持时间范围、门店、分类过滤。用于回答\'哪些商品库存周转慢\'\'库存周转情况\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:inventory', '{\"readOnly\": true, \"outputHint\": \"返回库存周转率列表，包含商品、出库成本、平均库存价值、周转率。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);
INSERT INTO `agent_tool_definition` VAlUES (NULL, 'report:stock_fund', '查询库存资金占用。按商品维度统计库存价值及占比。支持门店、分类过滤。用于回答\'仓库压了多少资金\'\'库存资金占用\'等问题。', '{\"type\": \"object\", \"properties\": {\"endDate\": {\"type\": \"string\", \"description\": \"endDate\"}, \"storeId\": {\"type\": \"integer\", \"description\": \"storeId\"}, \"productId\": {\"type\": \"integer\", \"description\": \"productId\"}, \"startDate\": {\"type\": \"string\", \"description\": \"startDate\"}, \"categoryId\": {\"type\": \"integer\", \"description\": \"categoryId\"}}}', NULL, 'business:report:inventory', '{\"readOnly\": true, \"outputHint\": \"返回库存资金占用列表，包含商品、库存价值、占比。展示为 markdown 表格。\", \"destructive\": false}', 'report', 1, '1.0', NULL, 'system', 'system', NULL, NULL, 0, NULL, NULL);

-- ----------------------------
-- Table structure for chat_message
-- ----------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭 id',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '鎵�灞炰細璇� id锛堝叧鑱� chat_session.session_id锛�',
  `role` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '娑堟伅瑙掕壊锛歶ser / assistant',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '娑堟伅姝ｆ枃锛坅ssistant 涓� markdown锛�',
  `intent` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '鎰忓浘鏍囩锛坅ssistant锛孭ython meta.intent锛�',
  `tools_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '宸ュ叿璋冪敤璇︽儏 JSON锛坅ssistant锛孭ython meta.used_tools锛屼粎瀹¤瀛樺偍涓嶅睍绀哄墠绔級',
  `tokens_used` int NULL DEFAULT NULL COMMENT 'token 娑堣�楋紙assistant锛�',
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '绉熸埛 id',
  `store_id` bigint NULL DEFAULT NULL COMMENT '闂ㄥ簵 id',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime NULL DEFAULT NULL,
  `updated_at` datetime NULL DEFAULT NULL,
  `deleted` int NOT NULL DEFAULT 0,
  `delete_at` datetime NULL DEFAULT NULL,
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session`(`session_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '鏅鸿兘瀵硅瘽娑堟伅琛�' ROW_FORMAT = Dynamic;


-- ----------------------------
-- Table structure for chat_session
-- ----------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭 id',
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '浼氳瘽鍞竴鏍囪瘑锛堜笟鍔′晶鐢熸垚锛屽墠缂� sess_锛屼笌 Python memory key 瀵归綈锛�',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '鏂板璇�' COMMENT '浼氳瘽鏍囬锛堝彲閲嶅懡鍚嶏級',
  `user_id` bigint NULL DEFAULT NULL COMMENT '鍒涘缓鑰呯敤鎴� id锛堢敤浜庢寜鐢ㄦ埛闅旂浼氳瘽鍒楄〃锛�',
  `message_count` int NOT NULL DEFAULT 0 COMMENT '娑堟伅鏉℃暟锛堝啑浣欒鏁帮紝閬垮厤 count(*) 鏌ヨ锛�',
  `last_message_preview` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '鏈�鍚庝竴鏉℃秷鎭瑙堬紙鎴柇 200 瀛楃锛�',
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '绉熸埛 id锛堟嫤鎴櫒鑷姩娉ㄥ叆锛�',
  `store_id` bigint NULL DEFAULT NULL COMMENT '闂ㄥ簵 id锛圫toreLineHandler 鑷姩娉ㄥ叆锛�',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '鍒涘缓浜�',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '鏇存柊浜�',
  `created_at` datetime NULL DEFAULT NULL COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NULL DEFAULT NULL COMMENT '鏇存柊鏃堕棿',
  `deleted` int NOT NULL DEFAULT 0 COMMENT '閫昏緫鍒犻櫎鏍囪锛�0=鏈垹闄わ紝1=宸插垹闄わ級',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '鍒犻櫎鏃堕棿锛堥�昏緫鍒犻櫎鏃跺～鍏咃級',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '鍒犻櫎浜猴紙閫昏緫鍒犻櫎鏃跺～鍏咃級',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_session_id`(`session_id` ASC) USING BTREE,
  INDEX `idx_tenant_user`(`tenant_id` ASC, `user_id` ASC, `updated_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '鏅鸿兘瀵硅瘽浼氳瘽琛�' ROW_FORMAT = Dynamic;


-- ----------------------------
-- Table structure for coupon_template
-- ----------------------------
DROP TABLE IF EXISTS `coupon_template`;
CREATE TABLE `coupon_template`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '券名',
  `type` tinyint NOT NULL COMMENT 'fullcut满减/discount折扣/cash代金券',
  `face_value` decimal(15, 2) NOT NULL COMMENT '面额（满减/代金券为金额，折扣为折扣率0.8表示8折）',
  `threshold` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛（满X元可用）',
  `valid_type` tinyint NOT NULL COMMENT 'relative相对（领取后N天）/fixed绝对（起止时间）',
  `valid_days` int NULL DEFAULT NULL COMMENT 'relative 时领取后有效天数',
  `valid_start` datetime NULL DEFAULT NULL COMMENT 'fixed 时开始时间',
  `valid_end` datetime NULL DEFAULT NULL COMMENT 'fixed 时结束时间',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '发放总量，0=不限',
  `issued_count` int NOT NULL DEFAULT 0 COMMENT '已发放数量',
  `per_limit` int NOT NULL DEFAULT 1 COMMENT '每人限领',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT 'active启用/inactive停用',
  `promotion_id` bigint NULL DEFAULT NULL COMMENT '关联促销活动ID，可空',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_tenant_promotion`(`tenant_id` ASC, `promotion_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '优惠券模板表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of coupon_template
-- ----------------------------
INSERT INTO `coupon_template` VAlUES (NULL, 1001, '满30减20券', 1, 20.00, 30.00, 1, 30, NULL, NULL, 2000, 1080, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);
INSERT INTO `coupon_template` VAlUES (NULL, 1001, '满100减15券', 1, 15.00, 100.00, 1, 30, NULL, NULL, 500, 238, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);
INSERT INTO `coupon_template` VAlUES (NULL, 1001, '全场8.8折券', 2, 8.80, 0.00, 1, 30, NULL, NULL, 500, 54, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);
INSERT INTO `coupon_template` VAlUES (NULL, 1001, '10元代金券券', 3, 10.00, 0.00, 1, 30, NULL, NULL, 2000, 1809, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);
INSERT INTO `coupon_template` VAlUES (NULL, 1002, '满30减20券', 1, 20.00, 30.00, 1, 30, NULL, NULL, 1000, 219, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);
INSERT INTO `coupon_template` VAlUES (NULL, 1002, '满100减15券', 1, 15.00, 100.00, 1, 30, NULL, NULL, 500, 382, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);
INSERT INTO `coupon_template` VAlUES (NULL, 1002, '全场8.8折券', 2, 8.80, 0.00, 1, 30, NULL, NULL, 500, 170, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);
INSERT INTO `coupon_template` VAlUES (NULL, 1002, '10元代金券券', 3, 10.00, 0.00, 1, 30, NULL, NULL, 500, 188, 1, 1, NULL, 0, '2026-07-30 00:00:00', '2026-08-03 14:30:36', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for inventory_record
-- ----------------------------
DROP TABLE IF EXISTS `inventory_record`;
CREATE TABLE `inventory_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店ID（门店拦截器自动注入，NULL=租户级汇总）',
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `warehouse` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'default',
  `stock_qty` int NOT NULL DEFAULT 0,
  `safety_stock` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充，批量任务回退system）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充，批量任务回退system）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_warehouse`(`tenant_id` ASC, `warehouse` ASC) USING BTREE,
  INDEX `idx_tenant_store`(`tenant_id` ASC, `store_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of inventory_record
-- ----------------------------

-- ----------------------------
-- Table structure for kb_doc_chunk
-- ----------------------------
DROP TABLE IF EXISTS `kb_doc_chunk`;
CREATE TABLE `kb_doc_chunk`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '代理主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID (拦截器自动注入)',
  `doc_id` bigint NOT NULL COMMENT '所属文档ID (关联 knowledge_doc.id)',
  `chunk_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分片唯一标识 ({doc_id}_{chunk_index}, 与向量库对齐)',
  `chunk_index` int NOT NULL COMMENT '分片序号 (文档内从0递增)',
  `content_head` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分片头部文本 (前 2*overlap 字符, 管理员预览用)',
  `content_tail` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分片尾部文本 (后 2*overlap 字符, 小分片为空)',
  `char_count` int NOT NULL DEFAULT 0 COMMENT '分片全量字符数 (head+tail 截断前的原始长度)',
  `chunk_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'text' COMMENT '分片类型: text/table (表格感知分块标记)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chunk_id`(`chunk_id` ASC) USING BTREE,
  INDEX `idx_doc_id`(`doc_id` ASC) USING BTREE,
  INDEX `idx_tenant_doc`(`tenant_id` ASC, `doc_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识文档分片表 (chunk 持久化供管理员查看)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of kb_doc_chunk
-- ----------------------------
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 47, '47_0', 0, 'GMV (Gross Merchandise Volume, 成交总额) 计算口径:\n1. 统计范围: 已支付订单的实付金额 (含运费), 单位元;\n2. 计入时点: 用户支付成功时点 (非发货/妥投', '败订单/测试订单不计入;\n6. 例外: B2B 大客户订单单列, 不并入零售 GMV; 内部调拨单不计入.\n常见误用: GMV 不等于实收金额 (实收需扣退款 + 抵扣券核销差), 也不等于发货金额.', 326, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 48, '48_1', 1, '销量 (Sales Volume, 销售件数) 计算口径:\n1. 统计维度: 按 SKU 件数, 不按金额; 单位件;\n2. 计入时点: 出库时点 (非支付/妥投), 与库存账实同步;\n3. 退货扣减', '赠品处理: 满赠/买赠的赠品不计入销量 (无实付), 但计入出库单;\n6. 剔除项: 取消订单/内部调拨/损坏报损不计入销量.\n动销率 = 有销量 SKU 数 / 在售 SKU 数, 反映品类活跃度.', 276, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 49, '49_2', 2, '库存周转 (Inventory Turnover) 计算口径:\n1. 周转天数 = 平均库存金额 / 日均销售成本 (COGS) × 天数;\n2. 平均库存 = (期初库存 + 期末库存) / 2, ', '避免虚高周转);\n6. 滞销品: 90 天无动销的 SKU 建议单列, 周转天数剔除后看健康周转.\n健康指标: 食品饮料 30 天周转 < 25 天为健康; 日化 < 45 天; 服饰 < 60 天.', 313, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 50, '50_3', 3, '客单价 (Average Order Value, AOV) 计算口径:\n1. 公式: 客单价 = GMV (实付, 剔运费) / 支付订单数;\n2. 剔除运费: 运费不计入客单价 (避免虚高), 但', '处理: 拆单后仍按一笔订单计 (用户视角一次购买行为);\n6. 例外: 单笔超 1 万元的大客订单建议单列, 避免拉高均值失真.\n提升手段: 关联推荐/满减门槛/组合套餐, 均以提升客单价为优化目标.', 284, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 51, '51_4', 4, '促销 (Promotion) 计算口径:\n1. 促销订单: 含任意优惠 (满减/券/折扣/积分抵扣) 的订单;\n2. 优惠金额 = 原价 - 实付; 含平台券 + 门店券 + 满减 + 折扣;\n3. ', '成功为准 (非妥投), 取消订单的券回退;\n6. 剔除项: 内部员工折扣/会员日积分倍增不计入促销 (单列会员运营).\n叠加规则: 满减与优惠券可叠加 (上限实付), 限时折扣与满减互斥 (二选一).', 296, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 52, '52_5', 5, '满减活动规则 (2026 年度):\n1. 阶梯满减: 满 99 减 15 / 满 199 减 40 / 满 399 减 100, 按实付前金额判定;\n2. 跨品类: 全品类可参与 (含食品/日化/服饰', '清零; 大促期间可设单笔门槛;\n6. 例外: 称重商品/定制商品不参与满减 (价格浮动无法判定门槛).\n店长注意: 门店专属满减需在活动配置中绑定 store_id, 全场满减留空 store_id.', 330, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 53, '53_6', 6, '优惠券使用规则:\n1. 券类型: 满减券 (满 X 减 Y) / 折扣券 (Z 折) / 无门槛券 (直减);\n2. 叠加: 每笔订单仅可用 1 张券 (不叠加多张券); 券可与满减叠加 (先满减后券', '单品券, 按券面标注限制适用;\n6. 退款: 部分退款券不回退 (已核销); 全额退款券回退 (有效期未过).\n会员专属券: 仅会员可见/可用, 非会员领取后核销会失败 (权限校验在 Java 侧).', 301, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 54, '54_7', 7, '限时折扣活动规则:\n1. 形式: 指定时段 (如每日 10-12 点) 部分 SKU 折扣销售 (Z 折);\n2. 互斥: 限时折扣与满减互斥 (订单内含折扣 SKU 则整单不走满减);\n3. 叠加券', '. 退款: 折扣订单退款按实付金额退, 不按原价;\n6. 数据上报: 折扣订单的优惠金额单列「限时折扣」标签, 不并入满减统计.\n店长注意: 限时折扣 SKU 的库存周转单独看, 不混入常规周转计算.', 264, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 55, '55_8', 8, '退换货处理 SOP:\n1. 7 天无理由: 用户收货 7 天内可无理由退货 (商品完好), 运费用户承担;\n2. 质量问题: 30 天内质量问题可退可换, 运费商家承担;\n3. 凭证要求: 退货需上传', '新货 (3-5 个工作日);\n6. 例外: 贴身衣物/定制商品/生鲜不支持 7 天无理由 (商品页需明示).\n数据影响: 退货订单从当日 GMV/销量扣减; 换货不计入退货 (只换 SKU 不退钱).', 307, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 56, '56_9', 9, '门店盘点流程 SOP (仅店长可见):\n1. 盘点周期: 周盘 (高动销 SKU, 每周一次) + 月盘 (全量 SKU, 每月一次);\n2. 盘点准备: 盘点前 2 小时停止出入库, 冻结库存账; ', '亏出库, 系统自动生成调账单, 店长审核生效;\n6. 报告: 月盘后 3 个工作日内提交盘点报告 (差异率/主要原因/改进措施).\n注意: 盘亏超 1 万元需上报区域, 启动调查流程; 恶意盘亏追责.', 308, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 57, '57_10', 10, '销量下滑诊断方法论 (历史案例归纳):\n1. 定位: 先看下滑幅度 (>20% 异常) + 持续天数 (>3 天需诊断) + 范围 (单 SKU/品类/全店);\n2. 外因排查: 节假日/天气/竞品活', '减下线), 恢复活动后回升;\n7. 典型案例 C: 全店销量降, 查流量降, 系平台算法调整, 优化主图/标题后恢复.\n诊断输出: 必须给出「现象 → 数据 → 根因 → 建议」四段式结论, 不臆测.', 405, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 58, '58_11', 11, '零售品类树 (聚合描述, 单 SKU 详情走商品查询工具):\n- 一级: 饮料\n  - 二级: 碳酸饮料 (含可口可乐/百事/雪碧等, 主流规格 330ml-2L);\n  - 二级: 果汁饮料 (浓度', '克力 / 饼干糕点 / 肉脯海味.\n- 一级: 日化 / 服饰 / 生鲜 (水/部分低价日化高动销).\n动销特征: 碳酸与水类高动销 (周转 < 15 天); 坚果季节性强 (年货季销量翻 3 倍).', 313, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 59, '59_12', 12, '门店清单区域聚合 (聚合描述, 单店详情走门店查询工具):\n- 华北区: 北京/天津/河北门店; 旗舰店坪效最高 (日均 GMV 5 万+), 社区店日均 1.5 万;\n- 华东区: 上海/江苏/浙江门店; 上海旗舰客单价最高 (180 元), 江浙社区店 120 元.\n配送: 华北由北京总仓发货 (次日达); 华东由上海总仓发货 (江浙沪次日达).', '', 176, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 60, '60_13', 13, '生鲜果蔬损耗管理 SOP (超市业态):\n1. 损耗率目标: 果蔬生鲜综合损耗率 ≤ 5%, 叶菜 ≤ 8%, 精品水果 ≤ 3%;\n2. 到货验收: 按批次抽检 10% 称重/验伤, 到货损耗超 3', ' 降低损耗;\n6. 盘点: 生鲜每日盘点 (日清日结), 损耗差异当日分析归属 (验收/陈列/存储/报损).\n数据影响: 报损在当日库存流水记为「报损」负向, 不计入销量; 损耗率异常需查冷链/收货.', 356, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 61, '61_14', 14, '冷链与温控管理 SOP (超市业态):\n1. 温度标准: 冷藏 0-4℃, 冷冻 -18℃ 以下, 常温 15-25℃; 各冷库/冷柜每日记录 3 次温度;\n2. 温度异常: 超温 30 分钟需查找原', '冻按需解冻, 不解冻回冻 (二次冷冻会降质并加速微生物滋生);\n6. 记录: 温度记录保存 90 天, 供质量追溯与损耗分析.\n提示: 冷链断链是生鲜损耗与投诉的首要原因, 温度记录是责任判定的依据.', 322, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 62, '62_15', 15, '临期商品与报损流程 (超市业态):\n1. 临期定义: 保质期 ≤ 1/3 剩余为临期, 需单独区域陈列并打折 (预售期设定);\n2. 临期预警: 保质期剩余 30 天自动预警, 进入临期区; 剩余 1', '≤ 1.5% (按成本), 超标的店需提交损耗分析;\n6. 责任: 过期未下架导致客诉由当班责任人承担; 恶意虚报损耗追责.\n提示: 临期商品促销是「止损」而非「盈利」, 重点关注损耗成本与客诉平衡.', 319, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 63, '63_16', 16, '天气与节假日备货策略 (超市业态):\n1. 节假日: 春节/中秋/国庆前 7 天启动备货, 生鲜加量 50%, 饮料/礼盒加量 30%;\n2. 天气: 降雨/降温日果蔬采购量下调 20-30% (生鲜', '量 + 未来 7 天天气预报 + 库存周转, 避免滞销与缺货;\n6. 复盘: 每次大促/极端天气后复盘备货准确率, 优化备货系数.\n提示: 生鲜备货宁可略紧不可过量 (损耗成本高), 标品可适度多备.', 325, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 64, '64_17', 17, '统配与门店配送 SOP (超市业态):\n1. 统配范围: 高流转标品 (饮料/日化/粮油) 由总仓统配, 生鲜按门店销售预测铺货;\n2. 配送频次: 中心店每日 1 配, 社区店 2 日 1 配; 生', '店滞销/临期可申请退仓, 总仓评估拦截高库存;\n6. 缺货补货: 门店缺货补货 T+1 到店, 紧急缺货走加急配送 (当日).\n提示: 配送准时率直接影响生鲜新鲜度与货架缺货率, 是门店运营关键指标.', 307, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1001, 65, '65_18', 18, '生鲜定价与促销策略 (超市业态):\n1. 定价原则: 生鲜按「进价 + 加价率」定价, 常规加价率 20-35%, 引流品 5-10%;\n2. 引流品: 每日 1-2 款爆品 (如鸡蛋/大米) 微利引', '联品组合 (如火锅食材套组), 提升客单价;\n6. 价格调整: 生鲜价格按日调整, 遇天气/到货量波动及时调价 (避免积压).\n提示: 生鲜促销核心是「去损耗」而非「拉利润」, 关注毛利与损耗的平衡.', 301, 'text', '2026-08-05 02:35:13');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 66, '66_0', 0, 'GMV (Gross Merchandise Volume, 成交总额) 计算口径:\n1. 统计范围: 已支付订单的实付金额 (含运费), 单位元;\n2. 计入时点: 用户支付成功时点 (非发货/妥投', '败订单/测试订单不计入;\n6. 例外: B2B 大客户订单单列, 不并入零售 GMV; 内部调拨单不计入.\n常见误用: GMV 不等于实收金额 (实收需扣退款 + 抵扣券核销差), 也不等于发货金额.', 326, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 67, '67_1', 1, '销量 (Sales Volume, 销售件数) 计算口径:\n1. 统计维度: 按 SKU 件数, 不按金额; 单位件;\n2. 计入时点: 出库时点 (非支付/妥投), 与库存账实同步;\n3. 退货扣减', '赠品处理: 满赠/买赠的赠品不计入销量 (无实付), 但计入出库单;\n6. 剔除项: 取消订单/内部调拨/损坏报损不计入销量.\n动销率 = 有销量 SKU 数 / 在售 SKU 数, 反映品类活跃度.', 276, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 68, '68_2', 2, '库存周转 (Inventory Turnover) 计算口径:\n1. 周转天数 = 平均库存金额 / 日均销售成本 (COGS) × 天数;\n2. 平均库存 = (期初库存 + 期末库存) / 2, ', '避免虚高周转);\n6. 滞销品: 90 天无动销的 SKU 建议单列, 周转天数剔除后看健康周转.\n健康指标: 食品饮料 30 天周转 < 25 天为健康; 日化 < 45 天; 服饰 < 60 天.', 313, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 69, '69_3', 3, '客单价 (Average Order Value, AOV) 计算口径:\n1. 公式: 客单价 = GMV (实付, 剔运费) / 支付订单数;\n2. 剔除运费: 运费不计入客单价 (避免虚高), 但', '处理: 拆单后仍按一笔订单计 (用户视角一次购买行为);\n6. 例外: 单笔超 1 万元的大客订单建议单列, 避免拉高均值失真.\n提升手段: 关联推荐/满减门槛/组合套餐, 均以提升客单价为优化目标.', 284, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 70, '70_4', 4, '促销 (Promotion) 计算口径:\n1. 促销订单: 含任意优惠 (满减/券/折扣/积分抵扣) 的订单;\n2. 优惠金额 = 原价 - 实付; 含平台券 + 门店券 + 满减 + 折扣;\n3. ', '成功为准 (非妥投), 取消订单的券回退;\n6. 剔除项: 内部员工折扣/会员日积分倍增不计入促销 (单列会员运营).\n叠加规则: 满减与优惠券可叠加 (上限实付), 限时折扣与满减互斥 (二选一).', 296, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 71, '71_5', 5, '满减活动规则 (2026 年度):\n1. 阶梯满减: 满 99 减 15 / 满 199 减 40 / 满 399 减 100, 按实付前金额判定;\n2. 跨品类: 全品类可参与 (含食品/日化/服饰', '清零; 大促期间可设单笔门槛;\n6. 例外: 称重商品/定制商品不参与满减 (价格浮动无法判定门槛).\n店长注意: 门店专属满减需在活动配置中绑定 store_id, 全场满减留空 store_id.', 330, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 72, '72_6', 6, '优惠券使用规则:\n1. 券类型: 满减券 (满 X 减 Y) / 折扣券 (Z 折) / 无门槛券 (直减);\n2. 叠加: 每笔订单仅可用 1 张券 (不叠加多张券); 券可与满减叠加 (先满减后券', '单品券, 按券面标注限制适用;\n6. 退款: 部分退款券不回退 (已核销); 全额退款券回退 (有效期未过).\n会员专属券: 仅会员可见/可用, 非会员领取后核销会失败 (权限校验在 Java 侧).', 301, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 73, '73_7', 7, '限时折扣活动规则:\n1. 形式: 指定时段 (如每日 10-12 点) 部分 SKU 折扣销售 (Z 折);\n2. 互斥: 限时折扣与满减互斥 (订单内含折扣 SKU 则整单不走满减);\n3. 叠加券', '. 退款: 折扣订单退款按实付金额退, 不按原价;\n6. 数据上报: 折扣订单的优惠金额单列「限时折扣」标签, 不并入满减统计.\n店长注意: 限时折扣 SKU 的库存周转单独看, 不混入常规周转计算.', 264, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 74, '74_8', 8, '退换货处理 SOP:\n1. 7 天无理由: 用户收货 7 天内可无理由退货 (商品完好), 运费用户承担;\n2. 质量问题: 30 天内质量问题可退可换, 运费商家承担;\n3. 凭证要求: 退货需上传', '新货 (3-5 个工作日);\n6. 例外: 贴身衣物/定制商品/生鲜不支持 7 天无理由 (商品页需明示).\n数据影响: 退货订单从当日 GMV/销量扣减; 换货不计入退货 (只换 SKU 不退钱).', 307, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 75, '75_9', 9, '门店盘点流程 SOP (仅店长可见):\n1. 盘点周期: 周盘 (高动销 SKU, 每周一次) + 月盘 (全量 SKU, 每月一次);\n2. 盘点准备: 盘点前 2 小时停止出入库, 冻结库存账; ', '亏出库, 系统自动生成调账单, 店长审核生效;\n6. 报告: 月盘后 3 个工作日内提交盘点报告 (差异率/主要原因/改进措施).\n注意: 盘亏超 1 万元需上报区域, 启动调查流程; 恶意盘亏追责.', 308, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 76, '76_10', 10, '销量下滑诊断方法论 (历史案例归纳):\n1. 定位: 先看下滑幅度 (>20% 异常) + 持续天数 (>3 天需诊断) + 范围 (单 SKU/品类/全店);\n2. 外因排查: 节假日/天气/竞品活', '减下线), 恢复活动后回升;\n7. 典型案例 C: 全店销量降, 查流量降, 系平台算法调整, 优化主图/标题后恢复.\n诊断输出: 必须给出「现象 → 数据 → 根因 → 建议」四段式结论, 不臆测.', 405, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 77, '77_11', 11, '零售品类树 (聚合描述, 单 SKU 详情走商品查询工具):\n- 一级: 饮料\n  - 二级: 碳酸饮料 (含可口可乐/百事/雪碧等, 主流规格 330ml-2L);\n  - 二级: 果汁饮料 (浓度', '克力 / 饼干糕点 / 肉脯海味.\n- 一级: 日化 / 服饰 / 生鲜 (水/部分低价日化高动销).\n动销特征: 碳酸与水类高动销 (周转 < 15 天); 坚果季节性强 (年货季销量翻 3 倍).', 313, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 78, '78_12', 12, '门店清单区域聚合 (聚合描述, 单店详情走门店查询工具):\n- 华北区: 北京/天津/河北门店; 旗舰店坪效最高 (日均 GMV 5 万+), 社区店日均 1.5 万;\n- 华东区: 上海/江苏/浙江门店; 上海旗舰客单价最高 (180 元), 江浙社区店 120 元.\n配送: 华北由北京总仓发货 (次日达); 华东由上海总仓发货 (江浙沪次日达).', '', 176, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 79, '79_13', 13, '休闲食品选品与品类规划 (零食店业态):\n1. 品类结构: 膨化 25% / 坚果炒货 20% / 糖果巧克力 15% / 饼干糕点 15% / 肉脯海味 10% / 饮料 15%;\n2. 选品原则:', '试销 + 数据验证」引入, 不盲目铺货;\n6. 淘汰: 90 天动销率 < 30% 或连续 2 个月负毛利 SKU 纳入淘汰清单.\n提示: 零食店选品是「以销定采」, 关注动销率与周转而非单纯铺货量.', 350, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 80, '80_14', 14, '保质期与临期预警管理 (零食店业态):\n1. 临期定义: 剩余保质期 < 1/3 为临期, 需单独陈列 + 打折 (如 5-7 折);\n2. 预警: 系统按商品保质期自动预警 (剩余 45 天/30 ', '下架报损, 拍照留证, 店长审核, 计入损耗成本;\n6. 回收: 临期未售出可联系供应商退换 (部分品牌支持), 降低损耗.\n提示: 零食保质期 6-18 个月, 临期管理是损耗控制与客诉防范的核心.', 303, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 81, '81_15', 15, '货架陈列与动销优化 (零食店业态):\n1. 黄金货架: 1.2-1.6m 黄金视线区放高毛利/高动销网红品, 低毛利放底层/顶层;\n2. 动线: 入口放引流爆品, 收银台放冲动型小商品 (糖果/口香糖', '-3 款新品并放「新品区」, 数据跟踪 2 周动销决定去留;\n6. 数据驱动: 每周按动销率/毛利/坪效分析货架, 淘汰低效 SKU.\n提示: 陈列优化的目标是「提升坪效」, 用动销数据而非经验判断.', 305, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 82, '82_16', 16, '新品引进与续订评估 (零食店业态):\n1. 引进流程: 供应商推荐 → 试销 (2 周, 2-3 门店) → 数据评估 → 全面铺货;\n2. 试销指标: 2 周动销率 ≥ 40% 且 复购率 ≥ 15', '销量, 避免压货; 断货风险品备安全库存;\n6. 复盘: 每季度新品存活率 (≥ 60% 为健康), 优化选品预判能力.\n提示: 零食店 SKU 多、周转快, 新品管理要「快进快出」, 避免滞销库存.', 307, 'text', '2026-08-05 02:35:14');
INSERT INTO `kb_doc_chunk` VAlUES (NULL, 1002, 83, '83_17', 17, '零食促销与组合销售策略 (零食店业态):\n1. 促销形式: 满减 (满 50 减 5 / 满 99 减 15) / 第二件半价 / 组合套餐 / 会员价;\n2. 组合套餐: 爆品 + 关联品捆绑 (如', '一送一」或「换购」快速去化, 避免过期损耗;\n6. 促销复盘: 记录促销 ROI 与库存去化率, 优化下次活动力度.\n提示: 零食店促销核心是「组合提客单 + 清库存降损耗」, 关注毛利率与库存健康.', 314, 'text', '2026-08-05 02:35:14');

-- ----------------------------
-- Table structure for knowledge_doc
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_doc`;
CREATE TABLE `knowledge_doc`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '代理主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID (拦截器自动注入)',
  `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档标题 (同步到 Python metadata.title)',
  `domain` tinyint NOT NULL COMMENT '业务域: order/inventory/sales/promo/member/sop/category_tree/product_catalog/store_list',
  `role_id` bigint NULL DEFAULT NULL COMMENT '可见角色ID: NULL=全员可见; 非空=仅该 sys_role 可见',
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店范围: NULL=全局; 店长级文档填具体门店',
  `status` tinyint NOT NULL COMMENT 'draft/published/expired/archived (D3=B 无审批态)',
  `valid_from` date NULL DEFAULT NULL COMMENT '生效时间: 促销政策可定时生效, NULL=立即',
  `valid_until` date NULL DEFAULT NULL COMMENT '失效时间: 检索时过滤过期 (C5), NULL=长期有效',
  `current_version` int NOT NULL DEFAULT 1 COMMENT '当前版本号 (D4 文档级版本)',
  `content_preview` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文档预览 (前200字, 列表/详情展示用, 不存全量原文)',
  `file_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '原文文件落盘路径 (data/kb_files/{tenant}/{docId}.{ext}), 原文 SSOT',
  `source_type` tinyint NOT NULL COMMENT '来源: manual(手动录入)/upload(文件上传)/generated(系统生成)',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人 (自动填充)',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人 (自动填充)',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间 (逻辑删除填充)',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人 (逻辑删除填充)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_tenant_domain`(`tenant_id` ASC, `domain` ASC) USING BTREE,
  INDEX `idx_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识文档主表 (Java SSOT, Python 索引消费者)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of knowledge_doc
-- ----------------------------
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, 'GMV 计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, 'GMV (Gross Merchandise Volume, 成交总额) 计算口径:\n1. 统计范围', 'data/kb_files/1001/47.txt', 3, 0, '2026-08-05 00:49:15', '2026-08-05 00:49:15', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '销量计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '销量 (Sales Volume, 销售件数) 计算口径:\n1. 统计维度: 按 SKU 件数, 不', 'data/kb_files/1001/48.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '库存周转计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '库存周转 (Inventory Turnover) 计算口径:\n1. 周转天数 = 平均库存金额 /', 'data/kb_files/1001/49.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '客单价计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '客单价 (Average Order Value, AOV) 计算口径:\n1. 公式: 客单价 = ', 'data/kb_files/1001/50.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '促销口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '促销 (Promotion) 计算口径:\n1. 促销订单: 含任意优惠 (满减/券/折扣/积分抵扣)', 'data/kb_files/1001/51.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '满减活动规则', 4, NULL, NULL, 2, NULL, NULL, 1, '满减活动规则 (2026 年度):\n1. 阶梯满减: 满 99 减 15 / 满 199 减 40 ', 'data/kb_files/1001/52.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '优惠券使用规则', 4, NULL, NULL, 2, NULL, NULL, 1, '优惠券使用规则:\n1. 券类型: 满减券 (满 X 减 Y) / 折扣券 (Z 折) / 无门槛券 ', 'data/kb_files/1001/53.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '限时折扣活动规则', 4, NULL, NULL, 2, NULL, NULL, 1, '限时折扣活动规则:\n1. 形式: 指定时段 (如每日 10-12 点) 部分 SKU 折扣销售 (Z', 'data/kb_files/1001/54.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '退换货处理 SOP', 6, NULL, NULL, 2, NULL, NULL, 1, '退换货处理 SOP:\n1. 7 天无理由: 用户收货 7 天内可无理由退货 (商品完好), 运费用户', 'data/kb_files/1001/55.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '盘点流程 SOP', 6, NULL, NULL, 2, NULL, NULL, 1, '门店盘点流程 SOP (仅店长可见):\n1. 盘点周期: 周盘 (高动销 SKU, 每周一次) + ', 'data/kb_files/1001/56.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '销量下滑诊断方法论', 3, NULL, NULL, 2, NULL, NULL, 1, '销量下滑诊断方法论 (历史案例归纳):\n1. 定位: 先看下滑幅度 (>20% 异常) + 持续天数', 'data/kb_files/1001/57.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '品类树聚合描述', 7, NULL, NULL, 2, NULL, NULL, 1, '零售品类树 (聚合描述, 单 SKU 详情走商品查询工具):\n- 一级: 饮料\n  - 二级: 碳酸', 'data/kb_files/1001/58.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '门店清单区域聚合', 9, NULL, NULL, 2, NULL, NULL, 1, '门店清单区域聚合 (聚合描述, 单店详情走门店查询工具):\n- 华北区: 北京/天津/河北门店; 旗', 'data/kb_files/1001/59.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '生鲜果蔬损耗管理 SOP', 6, NULL, NULL, 2, NULL, NULL, 1, '生鲜果蔬损耗管理 SOP (超市业态):\n1. 损耗率目标: 果蔬生鲜综合损耗率 ≤ 5%, 叶菜 ', 'data/kb_files/1001/60.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '冷链与温控管理 SOP', 6, NULL, NULL, 2, NULL, NULL, 1, '冷链与温控管理 SOP (超市业态):\n1. 温度标准: 冷藏 0-4℃, 冷冻 -18℃ 以下, ', 'data/kb_files/1001/61.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '临期商品与报损流程', 6, NULL, NULL, 2, NULL, NULL, 1, '临期商品与报损流程 (超市业态):\n1. 临期定义: 保质期 ≤ 1/3 剩余为临期, 需单独区域陈', 'data/kb_files/1001/62.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '天气与节假日备货策略', 3, NULL, NULL, 2, NULL, NULL, 1, '天气与节假日备货策略 (超市业态):\n1. 节假日: 春节/中秋/国庆前 7 天启动备货, 生鲜加量', 'data/kb_files/1001/63.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '统配与门店配送 SOP', 6, NULL, NULL, 2, NULL, NULL, 1, '统配与门店配送 SOP (超市业态):\n1. 统配范围: 高流转标品 (饮料/日化/粮油) 由总仓统', 'data/kb_files/1001/64.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1001, '生鲜定价与促销策略', 4, NULL, NULL, 2, NULL, NULL, 1, '生鲜定价与促销策略 (超市业态):\n1. 定价原则: 生鲜按「进价 + 加价率」定价, 常规加价率 ', 'data/kb_files/1001/65.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, 'GMV 计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, 'GMV (Gross Merchandise Volume, 成交总额) 计算口径:\n1. 统计范围', 'data/kb_files/1002/66.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '销量计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '销量 (Sales Volume, 销售件数) 计算口径:\n1. 统计维度: 按 SKU 件数, 不', 'data/kb_files/1002/67.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '库存周转计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '库存周转 (Inventory Turnover) 计算口径:\n1. 周转天数 = 平均库存金额 /', 'data/kb_files/1002/68.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '客单价计算口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '客单价 (Average Order Value, AOV) 计算口径:\n1. 公式: 客单价 = ', 'data/kb_files/1002/69.txt', 3, 0, '2026-08-05 00:49:16', '2026-08-05 00:49:16', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '促销口径定义', 6, NULL, NULL, 2, NULL, NULL, 1, '促销 (Promotion) 计算口径:\n1. 促销订单: 含任意优惠 (满减/券/折扣/积分抵扣)', 'data/kb_files/1002/70.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '满减活动规则', 4, NULL, NULL, 2, NULL, NULL, 1, '满减活动规则 (2026 年度):\n1. 阶梯满减: 满 99 减 15 / 满 199 减 40 ', 'data/kb_files/1002/71.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '优惠券使用规则', 4, NULL, NULL, 2, NULL, NULL, 1, '优惠券使用规则:\n1. 券类型: 满减券 (满 X 减 Y) / 折扣券 (Z 折) / 无门槛券 ', 'data/kb_files/1002/72.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '限时折扣活动规则', 4, NULL, NULL, 2, NULL, NULL, 1, '限时折扣活动规则:\n1. 形式: 指定时段 (如每日 10-12 点) 部分 SKU 折扣销售 (Z', 'data/kb_files/1002/73.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '退换货处理 SOP', 6, NULL, NULL, 2, NULL, NULL, 1, '退换货处理 SOP:\n1. 7 天无理由: 用户收货 7 天内可无理由退货 (商品完好), 运费用户', 'data/kb_files/1002/74.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '盘点流程 SOP', 6, NULL, NULL, 2, NULL, NULL, 1, '门店盘点流程 SOP (仅店长可见):\n1. 盘点周期: 周盘 (高动销 SKU, 每周一次) + ', 'data/kb_files/1002/75.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '销量下滑诊断方法论', 3, NULL, NULL, 2, NULL, NULL, 1, '销量下滑诊断方法论 (历史案例归纳):\n1. 定位: 先看下滑幅度 (>20% 异常) + 持续天数', 'data/kb_files/1002/76.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '品类树聚合描述', 7, NULL, NULL, 2, NULL, NULL, 1, '零售品类树 (聚合描述, 单 SKU 详情走商品查询工具):\n- 一级: 饮料\n  - 二级: 碳酸', 'data/kb_files/1002/77.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '门店清单区域聚合', 9, NULL, NULL, 2, NULL, NULL, 1, '门店清单区域聚合 (聚合描述, 单店详情走门店查询工具):\n- 华北区: 北京/天津/河北门店; 旗', 'data/kb_files/1002/78.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '休闲食品选品与品类规划', 3, NULL, NULL, 2, NULL, NULL, 1, '休闲食品选品与品类规划 (零食店业态):\n1. 品类结构: 膨化 25% / 坚果炒货 20% / ', 'data/kb_files/1002/79.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '保质期与临期预警管理', 6, NULL, NULL, 2, NULL, NULL, 1, '保质期与临期预警管理 (零食店业态):\n1. 临期定义: 剩余保质期 < 1/3 为临期, 需单独陈', 'data/kb_files/1002/80.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '货架陈列与动销优化', 3, NULL, NULL, 2, NULL, NULL, 1, '货架陈列与动销优化 (零食店业态):\n1. 黄金货架: 1.2-1.6m 黄金视线区放高毛利/高动销', 'data/kb_files/1002/81.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '新品引进与续订评估', 3, NULL, NULL, 2, NULL, NULL, 1, '新品引进与续订评估 (零食店业态):\n1. 引进流程: 供应商推荐 → 试销 (2 周, 2-3 门', 'data/kb_files/1002/82.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);
INSERT INTO `knowledge_doc` VAlUES (NULL, 1002, '零食促销与组合销售策略', 4, NULL, NULL, 2, NULL, NULL, 1, '零食促销与组合销售策略 (零食店业态):\n1. 促销形式: 满减 (满 50 减 5 / 满 99 ', 'data/kb_files/1002/83.txt', 3, 0, '2026-08-05 00:49:17', '2026-08-05 00:49:17', 'system', 'system', NULL, NULL);

-- ----------------------------
-- Table structure for long_memory
-- ----------------------------
DROP TABLE IF EXISTS `long_memory`;
CREATE TABLE `long_memory`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `tenant_id` bigint NOT NULL COMMENT '租户ID (拦截器自动注入, 长期记忆按租户隔离)',
  `user_id` bigint NOT NULL COMMENT '用户ID (Mem0 user 作用域, Service 手动过滤)',
  `memory_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'preference' COMMENT '记忆类型: preference/constraint/habit/goal (v1 默认 preference)',
  `category` int NOT NULL COMMENT '分类 (整型枚举: 0=REPORT_FORMAT 1=SCOPE_FILTER 2=PERMISSION_CONFIRM 3=DIAGNOSIS_DEPTH 4=DISPLAY_STYLE 5=PROMO_PREFERENCE 6=COMMUNICATION_STYLE 100=OTHER)',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记忆文本 (用户主观偏好/约束描述)',
  `confidence` decimal(3, 2) NOT NULL DEFAULT 0.00 COMMENT '置信度 0.00-1.00 (合并/覆盖前新记忆必须 ≥ MEMORY_CONFIDENCE_THRESHOLD)',
  `importance` int NOT NULL DEFAULT 3 COMMENT '重要性 1-5 (1=随口一提 3=明确偏好 5=硬性不可违背)',
  `access_count` int NOT NULL DEFAULT 0 COMMENT '访问次数 (重排加权)',
  `last_accessed_at` datetime NULL DEFAULT NULL COMMENT '最近访问时间 (recency 加权)',
  `source_msg_id` bigint NULL DEFAULT NULL COMMENT '最近一次来源消息 id (增量游标回溯)',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人 (自动填充)',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人 (自动填充)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间 (自动填充)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间 (自动填充)',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 (0=未删除, 1=已删除)',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间 (逻辑删除时填充)',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '删除人 (逻辑删除时填充)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_user`(`tenant_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_tenant_user_cat`(`tenant_id` ASC, `user_id` ASC, `category` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '长期记忆表 (跨会话用户主观偏好, Java SSOT, Python 只读/抽取)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of long_memory
-- ----------------------------
INSERT INTO `long_memory` VAlUES (NULL, 1001, 1001, 'preference', 0, '报表使用周报格式，只看本店数据，结论先给', 0.90, 4, 0, NULL, 100, 'system', 'system', '2026-08-05 15:16:35', '2026-08-05 15:17:21', 0, NULL, NULL);

-- ----------------------------
-- Table structure for member
-- ----------------------------
DROP TABLE IF EXISTS `member`;
CREATE TABLE `member`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `level` tinyint NOT NULL COMMENT 'normal/silver/gold/diamond',
  `points` int NOT NULL DEFAULT 0,
  `total_spent` decimal(15, 2) NOT NULL DEFAULT 0.00,
  `total_orders` int NOT NULL DEFAULT 0 COMMENT '累计订单数',
  `last_order_at` datetime NULL DEFAULT NULL COMMENT '最后下单时间',
  `last_active_at` datetime NULL DEFAULT NULL COMMENT '最后活跃时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_member`(`tenant_id` ASC, `id` ASC) USING BTREE,
  INDEX `idx_tenant_level`(`tenant_id` ASC, `level` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of member
-- ----------------------------
INSERT INTO `member` VAlUES (NULL, 1001, '张三', '13800001001', 3, 289, 289.60, 2, '2026-07-23 11:00:00', '2026-07-23 11:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:58', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '李四', '13800001002', 2, 135, 135.00, 1, '2026-07-21 14:00:00', '2026-07-21 14:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:44', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '王五', '13800001003', 1, 0, 0.00, 0, '2026-07-24 09:00:00', '2026-07-24 09:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '赵六', '13800002001', 4, 784, 784.00, 1, '2026-07-22 10:00:00', '2026-07-22 10:00:00', '2026-07-30 01:34:54', '2026-08-03 13:08:20', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '钱七', '13800002002', 1, 0, 0.00, 0, '2026-07-25 14:00:00', '2026-07-25 14:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '许超', '13961938315', 1, 327, 328.30, 2, '2026-07-09 10:17:59', '2026-07-18 04:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '许伟', '13972458289', 1, 39, 39.80, 1, '2026-06-04 00:34:35', '2026-07-25 07:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '马倩', '13925682695', 1, 429, 429.70, 2, '2026-07-04 15:05:03', '2026-07-01 22:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '刘哲', '13989361657', 3, 0, 0.00, 0, '2026-06-14 12:22:33', '2026-07-17 00:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:58', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '周梅', '13991022314', 1, 0, 0.00, 0, NULL, '2026-07-12 03:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '马梅', '13970829099', 1, 7, 7.00, 1, '2026-07-18 08:17:11', '2026-07-23 06:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '许桂', '13947341930', 2, 0, 0.00, 0, '2026-06-11 13:10:20', '2026-06-15 14:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:44', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '马哲', '13950411697', 1, 0, 0.00, 0, NULL, '2026-07-03 10:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '赵强', '13978994440', 1, 3, 3.50, 1, '2026-07-12 16:28:25', '2026-06-05 15:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '孙平', '13957072238', 1, 0, 0.00, 0, NULL, '2026-06-10 03:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '孙宇', '13954703806', 1, 636, 656.00, 2, '2026-07-15 10:20:00', '2026-08-06 12:19:43', '2026-07-30 01:34:54', '2026-08-06 12:19:43', NULL, 'internal-agent');
INSERT INTO `member` VAlUES (NULL, 1001, '赵艳', '13998723857', 1, 35, 35.20, 1, '2026-07-07 03:12:29', '2026-07-18 06:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '梁敏', '13979574822', 2, 137, 138.00, 3, '2026-06-29 21:40:55', '2026-07-05 21:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:44', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '冯亚', '13925324876', 2, 0, 0.00, 0, NULL, '2026-06-11 23:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:44', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '徐波', '13944705957', 1, 0, 0.00, 0, NULL, '2026-07-26 02:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '谢勇', '13949573497', 2, 0, 0.00, 0, '2026-06-29 08:33:09', '2026-06-08 07:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:44', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '李哲', '13983573045', 1, 117, 118.20, 2, '2026-07-19 13:06:28', '2026-07-24 01:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '王英', '13962659506', 1, 95, 95.80, 1, '2026-06-18 11:38:06', '2026-06-21 18:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '谢桂', '13941742720', 1, 0, 0.00, 0, '2026-07-25 15:41:32', '2026-06-26 09:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '杨辉', '13996909334', 3, 176, 176.00, 1, '2026-06-20 00:26:29', '2026-06-06 20:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:58', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '梁刚', '13976072485', 3, 0, 0.00, 0, NULL, '2026-06-14 17:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:58', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '刘桂', '13933650563', 3, 0, 0.00, 0, NULL, '2026-07-24 09:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:58', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '罗鹏', '13992007502', 1, 2322, 2322.00, 2, '2026-07-25 15:13:36', '2026-06-27 11:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '孙勇', '13924718445', 1, 0, 0.00, 0, NULL, '2026-06-09 04:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '冯晨', '13938820364', 2, 0, 0.00, 0, NULL, '2026-07-18 06:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:44', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '杨洋', '13987371838', 1, 0, 0.00, 0, '2026-06-16 09:48:14', '2026-06-06 11:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '林雪', '13963594789', 1, 0, 0.00, 0, '2026-06-01 20:05:34', '2026-06-28 22:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '赵伟', '13977860818', 1, 0, 0.00, 0, NULL, '2026-07-29 11:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '陈婷', '13927638563', 1, 0, 0.00, 0, NULL, '2026-07-16 19:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '郑辉', '13966543009', 1, 536, 536.00, 2, '2026-07-08 15:11:34', '2026-06-22 23:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '孙波', '13911782942', 1, 2421, 2421.00, 3, '2026-06-15 16:29:00', '2026-07-25 19:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '吴伟', '13919350655', 1, 0, 0.00, 0, NULL, '2026-06-08 14:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '王伟', '13938371748', 1, 0, 0.00, 0, NULL, '2026-07-01 03:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '曹洋', '13915423550', 1, 0, 0.00, 0, NULL, '2026-07-27 10:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '罗辉', '13939358008', 1, 0, 0.00, 0, NULL, '2026-07-06 22:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:30', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1002, '张宇', '13982526330', 2, 554, 554.00, 1, '2026-07-08 00:29:43', '2026-06-23 06:00:00', '2026-07-30 01:34:54', '2026-08-03 13:07:44', NULL, NULL);
INSERT INTO `member` VAlUES (NULL, 1001, '张三丰', '13851445144', 3, 100, 0.00, 0, NULL, '2026-08-06 07:55:24', '2026-08-06 07:55:24', '2026-08-06 07:55:24', 'internal-agent', 'internal-agent');
INSERT INTO `member` VAlUES (NULL, 1001, '张三丰', '13858445844', 3, 100, 0.00, 0, NULL, '2026-08-06 07:57:07', '2026-08-06 07:57:07', '2026-08-06 07:57:07', 'internal-agent', 'internal-agent');

-- ----------------------------
-- Table structure for member_tag
-- ----------------------------
DROP TABLE IF EXISTS `member_tag`;
CREATE TABLE `member_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `tag_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `tag_color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '展示色，如 #FF6B6B',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_tag`(`tenant_id` ASC, `tag_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会员标签定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of member_tag
-- ----------------------------
INSERT INTO `member_tag` VAlUES (NULL, 1001, '高消费会员', 'danger', '高消费会员自动分组', 0, '2026-07-30 00:00:00', '2026-07-30 00:00:00', NULL, NULL, NULL, NULL);
INSERT INTO `member_tag` VAlUES (NULL, 1001, '活跃会员', 'success', '活跃会员自动分组', 0, '2026-07-30 00:00:00', '2026-07-30 00:00:00', NULL, NULL, NULL, NULL);
INSERT INTO `member_tag` VAlUES (NULL, 1001, '沉睡会员', 'info', '沉睡会员自动分组', 0, '2026-07-30 00:00:00', '2026-07-30 00:00:00', NULL, NULL, NULL, NULL);
INSERT INTO `member_tag` VAlUES (NULL, 1002, '高消费会员', 'danger', '高消费会员自动分组', 0, '2026-07-30 00:00:00', '2026-07-30 00:00:00', NULL, NULL, NULL, NULL);
INSERT INTO `member_tag` VAlUES (NULL, 1002, '活跃会员', 'success', '活跃会员自动分组', 0, '2026-07-30 00:00:00', '2026-07-30 00:00:00', NULL, NULL, NULL, NULL);
INSERT INTO `member_tag` VAlUES (NULL, 1002, '沉睡会员', 'info', '沉睡会员自动分组', 0, '2026-07-30 00:00:00', '2026-07-30 00:00:00', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for member_tag_rel
-- ----------------------------
DROP TABLE IF EXISTS `member_tag_rel`;
CREATE TABLE `member_tag_rel`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_member_tag`(`member_id` ASC, `tag_id` ASC) USING BTREE,
  INDEX `idx_tag`(`tag_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会员-标签关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of member_tag_rel
-- ----------------------------
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3003, 3001);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3002, 3001);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3013, 3001);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3017, 3002);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3004, 3002);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3006, 3002);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3003, 3002);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3014, 3002);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3016, 3002);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3001, 3003);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3015, 3003);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3014, 3003);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1001, 3018, 3003);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3030, 3004);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3019, 3004);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3028, 3004);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3020, 3005);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3021, 3005);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3028, 3005);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3023, 3006);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3029, 3006);
INSERT INTO `member_tag_rel` VAlUES (NULL, 1002, 3025, 3006);

-- ----------------------------
-- Table structure for order_info
-- ----------------------------
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID（拦截器自动注入）',
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店ID（门店拦截器自动注入，NULL=租户级）',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务订单号，UNIQUE',
  `member_id` bigint NULL DEFAULT NULL COMMENT '会员ID，FK引用member.id，NULL=散客',
  `member_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '会员姓名冗余',
  `order_type` tinyint(1) NOT NULL COMMENT '1正常/2闪购/3秒杀',
  `status` tinyint NOT NULL COMMENT '1待付/2已付/3已发/4完成/5关闭/r6退款中/7已退款',
  `total_amount` decimal(15, 2) NOT NULL COMMENT '商品总金额',
  `discount_amount` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  `pay_amount` decimal(15, 2) NOT NULL COMMENT '实付金额=total_amount-discount_amount',
  `refund_amount` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '已退款金额',
  `pay_type` tinyint(1) NULL DEFAULT NULL COMMENT 'wechat/alipay/balance/cash',
  `pay_time` datetime NULL DEFAULT NULL,
  `channel` tinyint NOT NULL COMMENT 'online线上/agent Agent下单/manual手工',
  `order_time` datetime NOT NULL COMMENT '下单时间',
  `finish_time` datetime NULL DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `receiver_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收货人姓名（Agent 改收货用）',
  `receiver_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收货人电话（Agent 改收货用）',
  `receiver_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收货地址（Agent 改收货用）',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_order_no`(`tenant_id` ASC, `order_no` ASC) USING BTREE,
  INDEX `idx_tenant_status_time`(`tenant_id` ASC, `status` ASC, `order_time` ASC) USING BTREE,
  INDEX `idx_tenant_store_time`(`tenant_id` ASC, `store_id` ASC, `order_time` ASC) USING BTREE,
  INDEX `idx_tenant_member`(`tenant_id` ASC, `member_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_info
-- ----------------------------
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0001', 1001, '张三', 1, 4, 93.60, 0.00, 93.60, 0.00, 1, '2026-07-20 10:00:00', 3, '2026-07-20 10:00:00', '2026-07-20 10:30:00', '苹果+车厘子', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0002', 1002, '李四', 1, 4, 135.00, 0.00, 135.00, 0.00, 2, '2026-07-21 14:00:00', 3, '2026-07-21 14:00:00', '2026-07-21 15:00:00', '夏威夷果', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0003', 1003, '王五', 1, 3, 79.00, 0.00, 79.00, 0.00, 4, '2026-07-22 16:00:00', 3, '2026-07-22 16:00:00', NULL, '贵妃芒待发货', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0004', 1001, '张三', 1, 4, 196.00, 0.00, 196.00, 0.00, 1, '2026-07-23 11:00:00', 3, '2026-07-23 11:00:00', '2026-07-23 11:20:00', '车厘子JJ级', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0005', 1003, '王五', 1, 2, 45.60, 0.00, 45.60, 0.00, 3, '2026-07-24 09:00:00', 3, '2026-07-24 09:00:00', NULL, '苹果1kg待发货', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-06 12:20:24', NULL, 'system', NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0001', 2001, '赵六', 1, 4, 784.00, 0.00, 784.00, 0.00, 1, '2026-07-22 10:00:00', 3, '2026-07-22 10:00:00', '2026-07-22 10:30:00', '奶粉+纸尿裤', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0002', 2002, '钱七', 1, 2, 132.00, 0.00, 132.00, 0.00, 4, '2026-07-25 14:00:00', 3, '2026-07-25 14:00:00', NULL, '奶瓶待发货', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1004, '1001SO0006', 3002, '许伟', 1, 1, 141.00, 0.00, 141.00, 0.00, 1, NULL, 3, '2026-06-01 13:10:43', NULL, '顾客自提', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0003', 3027, '林雪', 1, 3, 128.00, 0.00, 128.00, 0.00, 4, '2026-06-01 20:05:34', 2, '2026-06-01 20:05:34', NULL, '补货订单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0004', 3036, '张宇', 1, 2, 648.00, 0.00, 648.00, 0.00, 1, '2026-06-03 12:18:37', 2, '2026-06-03 12:18:37', NULL, '顾客自提', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0007', 3002, '许伟', 1, 4, 39.80, 0.00, 39.80, 0.00, 3, '2026-06-04 00:34:35', 1, '2026-06-04 00:34:35', '2026-06-04 01:17:35', '线上下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0008', 3013, '梁敏', 1, 4, 36.30, 0.00, 36.30, 0.00, 1, '2026-06-04 07:45:27', 1, '2026-06-04 07:45:27', '2026-06-04 08:07:27', '顾客自提', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1004, '1001SO0009', 3003, '马倩', 1, 4, 26.40, 0.00, 26.40, 0.00, 4, '2026-06-06 07:31:17', 3, '2026-06-06 07:31:17', '2026-06-06 08:21:17', '店员开单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0005', 3020, '杨辉', 1, 3, 1032.00, 0.00, 1032.00, 0.00, 4, '2026-06-07 06:30:20', 1, '2026-06-07 06:30:20', NULL, '线上下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0006', 3030, '郑辉', 1, 4, 128.00, 0.00, 128.00, 0.00, 4, '2026-06-07 17:31:02', 2, '2026-06-07 17:31:02', '2026-06-07 17:49:02', '顾客自提', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0010', 3009, '赵强', 1, 4, 3.50, 0.00, 3.50, 0.00, 2, '2026-06-08 15:29:54', 1, '2026-06-08 15:29:54', '2026-06-08 16:17:54', '店员开单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0007', 3031, '孙波', 1, 4, 511.00, 0.00, 511.00, 0.00, 3, '2026-06-09 09:09:11', 3, '2026-06-09 09:09:11', '2026-06-09 10:07:11', '常规采购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0008', 3031, '孙波', 1, 4, 1634.00, 0.00, 1634.00, 0.00, 2, '2026-06-09 12:52:40', 3, '2026-06-09 12:52:40', '2026-06-09 13:45:40', '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0011', 3007, '许桂', 1, 2, 423.60, 0.00, 423.60, 0.00, 4, '2026-06-11 13:10:20', 2, '2026-06-11 13:10:20', NULL, '会员回购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0012', 3011, '孙宇', 1, 4, 558.00, 0.00, 558.00, 0.00, 4, '2026-06-12 11:14:46', 2, '2026-06-12 11:14:46', '2026-06-12 12:06:46', '线上下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0009', 3026, '杨洋', 1, 3, 99.00, 0.00, 99.00, 0.00, 1, '2026-06-12 14:13:17', 2, '2026-06-12 14:13:17', NULL, '补货订单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0013', 3006, '马梅', 1, 4, 7.00, 0.00, 7.00, 0.00, 4, '2026-06-12 14:26:44', 1, '2026-06-12 14:26:44', '2026-06-12 14:51:44', '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0014', 3001, '许超', 1, 4, 47.80, 0.00, 47.80, 0.00, 1, '2026-06-12 20:58:57', 2, '2026-06-12 20:58:57', '2026-06-12 21:43:57', 'Agent辅助下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0010', 3026, '杨洋', 1, 7, 668.00, 0.00, 668.00, 0.00, 4, '2026-06-13 23:02:05', 1, '2026-06-13 23:02:05', '2026-06-13 23:40:05', '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0015', 3004, '刘哲', 1, 7, 329.30, 0.00, 329.30, 0.00, 1, '2026-06-14 12:22:33', 1, '2026-06-14 12:22:33', '2026-06-14 13:09:33', '会员回购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0016', 3017, '李哲', 1, 4, 92.60, 0.00, 92.60, 0.00, 1, '2026-06-15 04:43:05', 2, '2026-06-15 04:43:05', '2026-06-15 04:59:05', '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-06 12:18:29', NULL, 'system', NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0011', 3031, '孙波', 1, 4, 276.00, 0.00, 276.00, 0.00, 4, '2026-06-15 16:29:00', 3, '2026-06-15 16:29:00', '2026-06-15 17:03:00', '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0012', 3026, '杨洋', 1, 5, 839.00, 0.00, 839.00, 0.00, 1, '2026-06-16 09:48:14', 1, '2026-06-16 09:48:14', NULL, '店员开单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0017', 3018, '王英', 1, 4, 95.80, 0.00, 95.80, 0.00, 4, '2026-06-18 11:38:06', 1, '2026-06-18 11:38:06', '2026-06-18 12:33:06', '补货订单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0018', 3013, '梁敏', 1, 4, 12.00, 0.00, 12.00, 0.00, 3, '2026-06-18 18:30:39', 2, '2026-06-18 18:30:39', '2026-06-18 19:24:39', '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0013', 3029, '陈婷', 1, 1, 276.00, 0.00, 276.00, 0.00, 1, NULL, 1, '2026-06-19 08:12:42', NULL, 'Agent辅助下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0014', 3020, '杨辉', 1, 4, 176.00, 0.00, 176.00, 0.00, 3, '2026-06-20 00:26:29', 2, '2026-06-20 00:26:29', '2026-06-20 01:15:29', '店员开单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0019', 3013, '梁敏', 1, 3, 144.70, 0.00, 144.70, 0.00, 2, '2026-06-20 12:44:33', 3, '2026-06-20 12:44:33', NULL, '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1004, '1001SO0020', 3013, '梁敏', 1, 2, 239.40, 0.00, 239.40, 0.00, 3, '2026-06-22 15:46:51', 1, '2026-06-22 15:46:51', NULL, 'Agent辅助下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0021', 3012, '赵艳', 1, 4, 35.20, 0.00, 35.20, 0.00, 2, '2026-06-28 03:30:30', 3, '2026-06-28 03:30:30', '2026-06-28 04:30:30', '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0022', 3016, '谢勇', 1, 3, 378.50, 0.00, 378.50, 0.00, 4, '2026-06-29 08:33:09', 3, '2026-06-29 08:33:09', NULL, '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0023', 3013, '梁敏', 1, 4, 89.70, 0.00, 89.70, 0.00, 1, '2026-06-29 21:40:55', 2, '2026-06-29 21:40:55', '2026-06-29 22:10:55', '会员回购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0024', 3003, '马倩', 1, 4, 403.30, 0.00, 403.30, 0.00, 4, '2026-07-04 15:05:03', 1, '2026-07-04 15:05:03', '2026-07-04 15:20:03', '常规采购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0025', 3012, '赵艳', 1, 2, 33.90, 0.00, 33.90, 0.00, 2, '2026-07-07 03:12:29', 2, '2026-07-07 03:12:29', NULL, '补货订单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0015', 3036, '张宇', 1, 4, 554.00, 0.00, 554.00, 0.00, 2, '2026-07-08 00:29:43', 3, '2026-07-08 00:29:43', '2026-07-08 00:51:43', '常规采购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1006, '1002SO0016', 3030, '郑辉', 1, 4, 408.00, 0.00, 408.00, 0.00, 3, '2026-07-08 15:11:34', 3, '2026-07-08 15:11:34', '2026-07-08 15:29:34', '顾客自提', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0026', 3001, '许超', 1, 5, 363.40, 0.00, 363.40, 0.00, 1, '2026-07-09 09:55:02', 2, '2026-07-09 09:55:02', NULL, '补货订单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0027', 3001, '许超', 1, 4, 280.50, 0.00, 280.50, 0.00, 4, '2026-07-09 10:17:59', 1, '2026-07-09 10:17:59', '2026-07-09 11:15:59', '线上下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:01', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0028', 3011, '孙宇', 1, 2, 47.70, 0.00, 47.70, 0.00, 3, '2026-07-09 17:44:09', 2, '2026-07-09 17:44:09', NULL, '线上下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1001, '1001SO0029', 3009, '赵强', 1, 2, 16.80, 0.00, 16.80, 0.00, 1, '2026-07-12 16:28:25', 2, '2026-07-12 16:28:25', NULL, '补货订单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1004, '1001SO0030', 3011, '孙宇', 1, 4, 98.00, 0.00, 98.00, 0.00, 2, '2026-07-15 10:20:00', 3, '2026-07-15 10:20:00', '2026-07-15 10:51:00', '会员回购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1004, '1001SO0031', 3003, '马倩', 1, 1, 38.40, 0.00, 38.40, 0.00, 1, NULL, 3, '2026-07-17 01:19:46', NULL, '活动促销', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:29', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1005, '1001SO0032', 3006, '马梅', 1, 2, 164.50, 0.00, 164.50, 0.00, 2, '2026-07-18 08:17:11', 1, '2026-07-18 08:17:11', NULL, 'Agent辅助下单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-06 07:40:17', NULL, 'internal-agent', NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0017', 3023, '罗鹏', 1, 4, 906.00, 0.00, 906.00, 0.00, 3, '2026-07-18 16:26:44', 2, '2026-07-18 16:26:44', '2026-07-18 16:55:44', '常规采购', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1001, 1002, '1001SO0033', 3017, '李哲', 1, 4, 25.60, 0.00, 25.60, 0.00, 1, '2026-07-19 13:06:28', 2, '2026-07-19 13:06:28', '2026-07-19 13:24:28', '测试：改备注', '李四', '13800001111', '杭州市西湖区文一西路1号', 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, 'internal-agent', NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0018', 3023, '罗鹏', 1, 4, 1416.00, 0.00, 1416.00, 0.00, 4, '2026-07-25 15:13:36', 2, '2026-07-25 15:13:36', '2026-07-25 15:43:36', '补货订单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);
INSERT INTO `order_info` VAlUES (NULL, 1002, 1003, '1002SO0019', 3019, '谢桂', 1, 2, 384.00, 0.00, 384.00, 0.00, 4, '2026-07-25 15:41:32', 2, '2026-07-25 15:41:32', NULL, '店员开单', NULL, NULL, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:04:12', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for order_item
-- ----------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL,
  `order_id` bigint NOT NULL COMMENT '订单ID，FK引用order_info.id',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '冗余便于查询',
  `product_id` bigint NOT NULL,
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品快照',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分类快照',
  `sku_id` bigint NULL DEFAULT NULL COMMENT 'SKU ID，无规格商品为NULL',
  `sku_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'SKU编码快照',
  `sku_spec` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '规格描述快照，如\"红色-XL\"',
  `unit_price` decimal(15, 2) NOT NULL COMMENT '成交单价（已含优惠分摊）',
  `qty` int NOT NULL COMMENT '购买数量',
  `subtotal` decimal(15, 2) NOT NULL COMMENT '小计金额=unit_price*qty',
  `cost_price` decimal(15, 2) NULL DEFAULT NULL COMMENT '成本价快照（用于毛利分析）',
  `refund_qty` int NOT NULL DEFAULT 0 COMMENT '已退款数量',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_order`(`tenant_id` ASC, `order_id` ASC) USING BTREE,
  INDEX `idx_tenant_product`(`tenant_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `idx_tenant_store_product`(`tenant_id` ASC, `store_id` ASC, `product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单明细表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_item
-- ----------------------------
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 1001, '1001SO0001', 1001, '阿克苏苹果', '生鲜水果/国产水果', 1001, 'PG-500G', '规格:500g', 12.80, 2, 25.60, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 1001, '1001SO0001', 1002, '智利车厘子', '生鲜水果/进口水果', 1003, 'CL-J', '规格:J级', 68.00, 1, 68.00, 45.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 1002, '1001SO0002', 1004, '云南夏威夷果', '休闲零食/坚果炒货', 1005, 'JG-YW', '口味:原味', 45.00, 3, 135.00, 28.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 1003, '1001SO0003', 1003, '海南贵妃芒', '生鲜水果/国产水果', NULL, NULL, NULL, 15.80, 5, 79.00, 9.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 1004, '1001SO0004', 1002, '智利车厘子', '生鲜水果/进口水果', 1004, 'CL-JJ', '规格:JJ级', 98.00, 2, 196.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 1005, '1001SO0005', 1001, '阿克苏苹果', '生鲜水果/国产水果', 1002, 'PG-1KG', '规格:1kg', 22.80, 2, 45.60, 16.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 2001, '1002SO0001', 2001, '飞鹤星飞帆奶粉3段', '奶粉辅食/婴儿奶粉', NULL, NULL, NULL, 328.00, 2, 656.00, 260.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 2001, '1002SO0001', 2002, '花王纸尿裤', '婴儿用品/纸尿裤', 2001, 'ZNK-L', '尺寸:L码', 128.00, 1, 128.00, 85.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 2002, '1002SO0002', 2003, '贝亲奶瓶', '婴儿用品/喂养用品', NULL, NULL, NULL, 44.00, 3, 132.00, 28.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3001, '1001SO0006', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3002, 'KSF-BHC-1L', '容量:1L', 5.50, 2, 11.00, 3.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3001, '1001SO0006', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3003, 'TLS-12', '规格:250ml*12', 65.00, 2, 130.00, 48.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3002, '1002SO0003', 3023, '帮宝适纸尿裤', '婴儿用品/纸尿裤', 3019, 'BBS-L', '尺寸:L码', 128.00, 1, 128.00, 85.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3003, '1002SO0004', 3023, '帮宝适纸尿裤', '婴儿用品/纸尿裤', 3019, 'BBS-L', '尺寸:L码', 128.00, 2, 256.00, 85.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3003, '1002SO0004', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3014, 'AN-TR', '类型:特润', 98.00, 4, 392.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3004, '1001SO0007', 3011, '良品铺子肉松饼', '休闲零食/坚果炒货', NULL, NULL, NULL, 19.90, 2, 39.80, 12.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3005, '1001SO0008', 3007, '伊利安慕希', '乳品蛋类/酸奶', 3006, 'YL-AMX-CM', '口味:草莓味', 12.90, 2, 25.80, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3005, '1001SO0008', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3001, 'KSF-BHC-500', '容量:500ml', 3.50, 3, 10.50, 2.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3006, '1001SO0009', 3006, '光明如实酸奶', '乳品蛋类/酸奶', NULL, NULL, NULL, 8.80, 3, 26.40, 5.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3007, '1002SO0005', 3014, '伊利金领冠奶粉1段', '奶粉辅食/婴儿奶粉', 3009, 'YL-JLG-400', '规格:400g', 258.00, 4, 1032.00, 200.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3008, '1002SO0006', 3022, '布鲁可启初精灵', '童装玩具/益智玩具', NULL, NULL, NULL, 128.00, 1, 128.00, 85.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3009, '1001SO0010', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3001, 'KSF-BHC-500', '容量:500ml', 3.50, 1, 3.50, 2.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3010, '1002SO0007', 3017, '强生婴儿润肤油', '洗护用品/护肤乳液', NULL, NULL, NULL, 45.00, 5, 225.00, 28.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3010, '1002SO0007', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3013, 'AN-RC', '类型:日常', 88.00, 1, 88.00, 58.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3010, '1002SO0007', 3021, '费雪积木', '童装玩具/益智玩具', NULL, NULL, NULL, 99.00, 2, 198.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3011, '1002SO0008', 3020, '巴拉巴拉童装T恤', '童装玩具/婴儿服饰', NULL, NULL, NULL, 49.00, 2, 98.00, 30.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3011, '1002SO0008', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3014, 'AN-TR', '类型:特润', 98.00, 2, 196.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3011, '1002SO0008', 3013, '君乐宝至臻奶粉2段', '奶粉辅食/婴儿奶粉', NULL, NULL, NULL, 268.00, 5, 1340.00, 210.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3012, '1001SO0011', 3012, '百草味手撕面包', '休闲零食/坚果炒货', NULL, NULL, NULL, 15.90, 4, 63.60, 9.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3012, '1001SO0011', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3004, 'TLS-24', '规格:250ml*24', 120.00, 3, 360.00, 92.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3013, '1001SO0012', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3003, 'TLS-12', '规格:250ml*12', 65.00, 4, 260.00, 48.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3013, '1001SO0012', 3009, '佳沛奇异果', '生鲜水果/进口水果', 3008, 'QY-LH', '规格:礼盒', 88.00, 3, 264.00, 58.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3013, '1001SO0012', 3008, '都乐香蕉', '生鲜水果/国产水果', NULL, NULL, NULL, 6.80, 5, 34.00, 4.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3014, '1002SO0009', 3021, '费雪积木', '童装玩具/益智玩具', NULL, NULL, NULL, 99.00, 1, 99.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3015, '1001SO0013', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3001, 'KSF-BHC-500', '容量:500ml', 3.50, 2, 7.00, 2.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3016, '1001SO0014', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3002, 'KSF-BHC-1L', '容量:1L', 5.50, 4, 22.00, 3.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3016, '1001SO0014', 3007, '伊利安慕希', '乳品蛋类/酸奶', 3006, 'YL-AMX-CM', '口味:草莓味', 12.90, 2, 25.80, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3017, '1002SO0010', 3022, '布鲁可启初精灵', '童装玩具/益智玩具', NULL, NULL, NULL, 128.00, 2, 256.00, 85.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3017, '1002SO0010', 3023, '帮宝适纸尿裤', '婴儿用品/纸尿裤', 3018, 'BBS-M', '尺寸:M码', 118.00, 1, 118.00, 78.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3017, '1002SO0010', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3014, 'AN-TR', '类型:特润', 98.00, 3, 294.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3018, '1001SO0015', 3010, '三只松鼠每日坚果', '休闲零食/坚果炒货', NULL, NULL, NULL, 29.90, 3, 89.70, 18.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3018, '1001SO0015', 3009, '佳沛奇异果', '生鲜水果/进口水果', 3008, 'QY-LH', '规格:礼盒', 88.00, 2, 176.00, 58.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3018, '1001SO0015', 3012, '百草味手撕面包', '休闲零食/坚果炒货', NULL, NULL, NULL, 15.90, 4, 63.60, 9.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3019, '1001SO0016', 3006, '光明如实酸奶', '乳品蛋类/酸奶', NULL, NULL, NULL, 8.80, 2, 17.60, 5.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3019, '1001SO0016', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3002, 'KSF-BHC-1L', '容量:1L', 5.50, 2, 11.00, 3.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3019, '1001SO0016', 3002, '汇源100%橙汁', '饮料冲调/果汁饮料', NULL, NULL, NULL, 12.80, 5, 64.00, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3020, '1002SO0011', 3022, '布鲁可启初精灵', '童装玩具/益智玩具', NULL, NULL, NULL, 128.00, 1, 128.00, 85.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3020, '1002SO0011', 3015, '亨氏米粉', '奶粉辅食/婴儿奶粉', 3011, 'HZ-MF-YW', '口味:原味', 35.00, 2, 70.00, 22.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3020, '1002SO0011', 3016, '妙思乐洗发沐浴露', '洗护用品/婴儿洗护', NULL, NULL, NULL, 78.00, 1, 78.00, 52.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3021, '1002SO0012', 3022, '布鲁可启初精灵', '童装玩具/益智玩具', NULL, NULL, NULL, 128.00, 5, 640.00, 85.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3021, '1002SO0012', 3019, '全棉时代婴儿连体衣', '童装玩具/婴儿服饰', 3017, 'CT-73', '尺码:73码', 75.00, 2, 150.00, 46.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3021, '1002SO0012', 3020, '巴拉巴拉童装T恤', '童装玩具/婴儿服饰', NULL, NULL, NULL, 49.00, 1, 49.00, 30.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3022, '1001SO0017', 3008, '都乐香蕉', '生鲜水果/国产水果', NULL, NULL, NULL, 6.80, 4, 27.20, 4.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3022, '1001SO0017', 3010, '三只松鼠每日坚果', '休闲零食/坚果炒货', NULL, NULL, NULL, 29.90, 2, 59.80, 18.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3022, '1001SO0017', 3009, '佳沛奇异果', '生鲜水果/进口水果', 3007, 'QY-DG', '规格:单果', 8.80, 1, 8.80, 5.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3023, '1001SO0018', 3007, '伊利安慕希', '乳品蛋类/酸奶', 3005, 'YL-AMX-YW', '口味:原味', 12.00, 1, 12.00, 7.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3024, '1002SO0013', 3019, '全棉时代婴儿连体衣', '童装玩具/婴儿服饰', 3015, 'CT-59', '尺码:59码', 69.00, 4, 276.00, 42.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3025, '1002SO0014', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3013, 'AN-RC', '类型:日常', 88.00, 2, 176.00, 58.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3026, '1001SO0019', 3007, '伊利安慕希', '乳品蛋类/酸奶', 3005, 'YL-AMX-YW', '口味:原味', 12.00, 4, 48.00, 7.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3026, '1001SO0019', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3001, 'KSF-BHC-500', '容量:500ml', 3.50, 2, 7.00, 2.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3026, '1001SO0019', 3010, '三只松鼠每日坚果', '休闲零食/坚果炒货', NULL, NULL, NULL, 29.90, 3, 89.70, 18.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3027, '1001SO0020', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3003, 'TLS-12', '规格:250ml*12', 65.00, 3, 195.00, 48.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3027, '1001SO0020', 3002, '汇源100%橙汁', '饮料冲调/果汁饮料', NULL, NULL, NULL, 12.80, 3, 38.40, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3027, '1001SO0020', 3001, '农夫山泉天然水', '饮料冲调/果汁饮料', NULL, NULL, NULL, 2.00, 3, 6.00, 1.20, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3028, '1001SO0021', 3006, '光明如实酸奶', '乳品蛋类/酸奶', NULL, NULL, NULL, 8.80, 4, 35.20, 5.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3029, '1001SO0022', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3002, 'KSF-BHC-1L', '容量:1L', 5.50, 3, 16.50, 3.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3029, '1001SO0022', 3001, '农夫山泉天然水', '饮料冲调/果汁饮料', NULL, NULL, NULL, 2.00, 1, 2.00, 1.20, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3029, '1001SO0022', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3004, 'TLS-24', '规格:250ml*24', 120.00, 3, 360.00, 92.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3030, '1001SO0023', 3010, '三只松鼠每日坚果', '休闲零食/坚果炒货', NULL, NULL, NULL, 29.90, 3, 89.70, 18.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3031, '1001SO0024', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3004, 'TLS-24', '规格:250ml*24', 120.00, 3, 360.00, 92.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3031, '1001SO0024', 3003, '康师傅冰红茶', '饮料冲调/茶饮', 3001, 'KSF-BHC-500', '容量:500ml', 3.50, 1, 3.50, 2.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3031, '1001SO0024', 3011, '良品铺子肉松饼', '休闲零食/坚果炒货', NULL, NULL, NULL, 19.90, 2, 39.80, 12.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3032, '1001SO0025', 3011, '良品铺子肉松饼', '休闲零食/坚果炒货', NULL, NULL, NULL, 19.90, 1, 19.90, 12.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3032, '1001SO0025', 3004, '统一绿茶', '饮料冲调/茶饮', NULL, NULL, NULL, 3.50, 4, 14.00, 2.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3033, '1002SO0015', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3014, 'AN-TR', '类型:特润', 98.00, 1, 98.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3033, '1002SO0015', 3017, '强生婴儿润肤油', '洗护用品/护肤乳液', NULL, NULL, NULL, 45.00, 4, 180.00, 28.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3033, '1002SO0015', 3023, '帮宝适纸尿裤', '婴儿用品/纸尿裤', 3020, 'BBS-XL', '尺寸:XL码', 138.00, 2, 276.00, 92.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3034, '1002SO0016', 3015, '亨氏米粉', '奶粉辅食/婴儿奶粉', 3012, 'HZ-MF-SC', '口味:蔬菜味', 38.00, 3, 114.00, 24.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1006, 3034, '1002SO0016', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3014, 'AN-TR', '类型:特润', 98.00, 3, 294.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3035, '1001SO0026', 3012, '百草味手撕面包', '休闲零食/坚果炒货', NULL, NULL, NULL, 15.90, 5, 79.50, 9.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3035, '1001SO0026', 3009, '佳沛奇异果', '生鲜水果/进口水果', 3008, 'QY-LH', '规格:礼盒', 88.00, 3, 264.00, 58.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3035, '1001SO0026', 3011, '良品铺子肉松饼', '休闲零食/坚果炒货', NULL, NULL, NULL, 19.90, 1, 19.90, 12.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3036, '1001SO0027', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3003, 'TLS-12', '规格:250ml*12', 65.00, 3, 195.00, 48.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3036, '1001SO0027', 3012, '百草味手撕面包', '休闲零食/坚果炒货', NULL, NULL, NULL, 15.90, 5, 79.50, 9.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3036, '1001SO0027', 3001, '农夫山泉天然水', '饮料冲调/果汁饮料', NULL, NULL, NULL, 2.00, 3, 6.00, 1.20, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3037, '1001SO0028', 3012, '百草味手撕面包', '休闲零食/坚果炒货', NULL, NULL, NULL, 15.90, 3, 47.70, 9.50, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3038, '1001SO0029', 3002, '汇源100%橙汁', '饮料冲调/果汁饮料', NULL, NULL, NULL, 12.80, 1, 12.80, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1001, 3038, '1001SO0029', 3001, '农夫山泉天然水', '饮料冲调/果汁饮料', NULL, NULL, NULL, 2.00, 2, 4.00, 1.20, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3039, '1001SO0030', 3009, '佳沛奇异果', '生鲜水果/进口水果', 3008, 'QY-LH', '规格:礼盒', 88.00, 1, 88.00, 58.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3039, '1001SO0030', 3001, '农夫山泉天然水', '饮料冲调/果汁饮料', NULL, NULL, NULL, 2.00, 5, 10.00, 1.20, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1004, 3040, '1001SO0031', 3002, '汇源100%橙汁', '饮料冲调/果汁饮料', NULL, NULL, NULL, 12.80, 3, 38.40, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3041, '1001SO0032', 3011, '良品铺子肉松饼', '休闲零食/坚果炒货', NULL, NULL, NULL, 19.90, 5, 99.50, 12.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1005, 3041, '1001SO0032', 3005, '特仑苏纯牛奶', '乳品蛋类/鲜牛奶', 3003, 'TLS-12', '规格:250ml*12', 65.00, 1, 65.00, 48.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3042, '1002SO0017', 3018, 'Aveeno婴儿润肤霜', '洗护用品/护肤乳液', 3014, 'AN-TR', '类型:特润', 98.00, 3, 294.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3042, '1002SO0017', 3024, '新安怡安抚奶嘴', '婴儿用品/喂养用品', NULL, NULL, NULL, 39.00, 3, 117.00, 24.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3042, '1002SO0017', 3021, '费雪积木', '童装玩具/益智玩具', NULL, NULL, NULL, 99.00, 5, 495.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1001, 1002, 3043, '1001SO0033', 3002, '汇源100%橙汁', '饮料冲调/果汁饮料', NULL, NULL, NULL, 12.80, 2, 25.60, 8.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3044, '1002SO0018', 3014, '伊利金领冠奶粉1段', '奶粉辅食/婴儿奶粉', 3010, 'YL-JLG-900', '规格:900g', 358.00, 3, 1074.00, 280.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3044, '1002SO0018', 3017, '强生婴儿润肤油', '洗护用品/护肤乳液', NULL, NULL, NULL, 45.00, 1, 45.00, 28.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3044, '1002SO0018', 3021, '费雪积木', '童装玩具/益智玩具', NULL, NULL, NULL, 99.00, 3, 297.00, 65.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3045, '1002SO0019', 3019, '全棉时代婴儿连体衣', '童装玩具/婴儿服饰', 3017, 'CT-73', '尺码:73码', 75.00, 2, 150.00, 46.00, 0, '2026-07-30 01:34:54', NULL);
INSERT INTO `order_item` VAlUES (NULL, 1002, 1003, 3045, '1002SO0019', 3016, '妙思乐洗发沐浴露', '洗护用品/婴儿洗护', NULL, NULL, NULL, 78.00, 3, 234.00, 52.00, 0, '2026-07-30 01:34:54', NULL);

-- ----------------------------
-- Table structure for order_refund
-- ----------------------------
DROP TABLE IF EXISTS `order_refund`;
CREATE TABLE `order_refund`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL,
  `refund_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '退款单号，UNIQUE',
  `order_id` bigint NOT NULL COMMENT '原订单ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '冗余',
  `member_id` bigint NULL DEFAULT NULL,
  `refund_type` tinyint NOT NULL COMMENT 'full全额/partial部分',
  `refund_amount` decimal(15, 2) NOT NULL COMMENT '退款金额',
  `refund_qty` int NULL DEFAULT NULL COMMENT '退款数量（部分退时）',
  `reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` tinyint NOT NULL COMMENT 'pending待审/approved通过/rejected拒绝/refunded已退款',
  `apply_time` datetime NOT NULL,
  `refund_time` datetime NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_refund_no`(`tenant_id` ASC, `refund_no` ASC) USING BTREE,
  INDEX `idx_tenant_order`(`tenant_id` ASC, `order_id` ASC) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '退款单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_refund
-- ----------------------------
INSERT INTO `order_refund` VAlUES (NULL, 1002, 1003, 'RF02SO0010', 3017, '1002SO0010', 3026, 2, 360.24, 1, '商品质量问题', 2, '2026-06-17 23:02:05', NULL, '2026-07-30 00:00:00', '2026-08-03 15:30:17', NULL, NULL);
INSERT INTO `order_refund` VAlUES (NULL, 1001, 1001, 'RF01SO0015', 3018, '1001SO0015', 3004, 1, 329.30, 1, '顾客不想要了', 2, '2026-06-18 12:22:33', NULL, '2026-07-30 00:00:00', '2026-08-03 15:30:19', NULL, NULL);
INSERT INTO `order_refund` VAlUES (NULL, 1001, 1001, 'RF01SO0016', 3019, '1001SO0015', 3004, 1, 329.30, 1, '顾客不想要了', 5, '2026-06-18 12:22:33', NULL, '2026-07-30 00:00:00', '2026-08-03 15:30:19', NULL, 'internal-agent');
INSERT INTO `order_refund` VAlUES (NULL, 1001, NULL, 'RF202608061220240137', 1005, '1001SO0005', 1003, 1, 45.60, NULL, '取消商品', 5, '2026-08-06 12:20:24', NULL, '2026-08-06 12:20:24', '2026-08-06 12:20:24', 'internal-agent', 'internal-agent');

-- ----------------------------
-- Table structure for order_trend
-- ----------------------------
DROP TABLE IF EXISTS `order_trend`;
CREATE TABLE `order_trend`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店ID（门店拦截器自动注入，NULL=租户级汇总）',
  `stat_date` datetime NOT NULL,
  `order_count` int NOT NULL DEFAULT 0,
  `order_amount` decimal(15, 2) NOT NULL DEFAULT 0.00,
  `refund_count` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充，批量任务回退system）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_date`(`tenant_id` ASC, `stat_date` ASC) USING BTREE,
  INDEX `idx_tenant_store_date`(`tenant_id` ASC, `store_id` ASC, `stat_date` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '订单趋势表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of order_trend
-- ----------------------------

-- ----------------------------
-- Table structure for points_log
-- ----------------------------
DROP TABLE IF EXISTS `points_log`;
CREATE TABLE `points_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `change_type` tinyint NOT NULL COMMENT 'earn消费获取/gift活动赠送/exchange兑换消耗/refund退款扣减/adjust手动调整',
  `change_points` int NOT NULL COMMENT '变动积分（正数增加，负数扣减）',
  `before_balance` int NOT NULL COMMENT '变动前余额',
  `after_balance` int NOT NULL COMMENT '变动后余额',
  `biz_type` tinyint NULL DEFAULT NULL COMMENT 'order/coupon/manual/activity',
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联单据号',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_member`(`tenant_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_tenant_store_member`(`tenant_id` ASC, `store_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_tenant_created`(`tenant_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会员积分流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of points_log
-- ----------------------------
INSERT INTO `points_log` VAlUES (NULL, 1001, 1001, 1001, 1, 93, 0, 93, 1, '1001SO0001', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1001, 1001, 1, 196, 93, 289, 1, '1001SO0004', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1002, 1002, 1, 135, 0, 135, 1, '1001SO0002', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1003, 2001, 1, 784, 0, 784, 1, '1002SO0001', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1005, 3002, 1, 39, 0, 39, 1, '1001SO0007', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1005, 3013, 1, 36, 0, 36, 1, '1001SO0008', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1004, 3003, 1, 26, 0, 26, 1, '1001SO0009', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1006, 3030, 1, 128, 0, 128, 1, '1002SO0006', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1001, 3009, 1, 3, 0, 3, 1, '1001SO0010', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1006, 3031, 1, 511, 0, 511, 1, '1002SO0007', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1006, 3031, 1, 1634, 511, 2145, 1, '1002SO0008', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1002, 3011, 1, 558, 0, 558, 1, '1001SO0012', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1001, 3006, 1, 7, 0, 7, 1, '1001SO0013', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1005, 3001, 1, 47, 0, 47, 1, '1001SO0014', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1003, 3026, 1, 668, 0, 668, 1, '1002SO0010', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1003, 3026, 4, -668, 668, 0, 5, '1002SO0010', '退款扣减积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1001, 3004, 1, 329, 0, 329, 1, '1001SO0015', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1001, 3004, 4, -329, 329, 0, 5, '1001SO0015', '退款扣减积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1005, 3017, 1, 92, 0, 92, 1, '1001SO0016', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1006, 3031, 1, 276, 2145, 2421, 1, '1002SO0011', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1002, 3018, 1, 95, 0, 95, 1, '1001SO0017', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1002, 3013, 1, 12, 36, 48, 1, '1001SO0018', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1006, 3020, 1, 176, 0, 176, 1, '1002SO0014', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1005, 3012, 1, 35, 0, 35, 1, '1001SO0021', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1002, 3013, 1, 89, 48, 137, 1, '1001SO0023', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1002, 3003, 1, 403, 26, 429, 1, '1001SO0024', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1006, 3036, 1, 554, 0, 554, 1, '1002SO0015', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1006, 3030, 1, 408, 128, 536, 1, '1002SO0016', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1005, 3001, 1, 280, 47, 327, 1, '1001SO0027', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1004, 3011, 1, 98, 558, 656, 1, '1001SO0030', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1003, 3023, 1, 906, 0, 906, 1, '1002SO0017', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, 1002, 3017, 1, 25, 92, 117, 1, '1001SO0033', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1002, 1003, 3023, 1, 1416, 906, 2322, 1, '1002SO0018', '订单完成赠送积分', '2026-07-30 01:34:54', NULL);
INSERT INTO `points_log` VAlUES (NULL, 1001, NULL, 3011, 3, -10, 656, 646, 3, NULL, '测试兑换', '2026-08-06 12:18:30', 'internal-agent');
INSERT INTO `points_log` VAlUES (NULL, 1001, NULL, 3011, 3, -10, 646, 636, 3, NULL, '测试兑换', '2026-08-06 12:19:43', 'internal-agent');

-- ----------------------------
-- Table structure for product_category
-- ----------------------------
DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID（拦截器自动注入）',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父分类ID，NULL=一级分类',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分类名称',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序权重',
  `status` tinyint NOT NULL COMMENT 'active/inactive',
  `description` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_parent`(`tenant_id` ASC, `parent_id` ASC) USING BTREE,
  INDEX `idx_tenant_status_sort`(`tenant_id` ASC, `status` ASC, `sort_order` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品分类表（二级树形）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_category
-- ----------------------------
INSERT INTO `product_category` VAlUES (NULL, 1001, NULL, '生鲜水果', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, 1001, '进口水果', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, 1001, '国产水果', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, NULL, '休闲零食', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, 1004, '坚果炒货', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, NULL, '饮料冲调', 3, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, 1006, '果汁饮料', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, 1006, '茶饮', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, NULL, '乳品蛋类', 4, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, 1009, '鲜牛奶', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1001, 1009, '酸奶', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1002, NULL, '奶粉辅食', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1002, 2001, '婴儿奶粉', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1002, NULL, '婴儿用品', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1002, 2003, '纸尿裤', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1002, 2003, '喂养用品', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 1002, NULL, '洗护用品', 3, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 2002, 2006, '婴儿洗护', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 2002, 2006, '护肤乳液', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 2002, NULL, '童装玩具', 4, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 2002, 2009, '婴儿服饰', 1, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_category` VAlUES (NULL, 2002, 2009, '益智玩具', 2, 1, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 13:12:47', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for product_info
-- ----------------------------
DROP TABLE IF EXISTS `product_info`;
CREATE TABLE `product_info`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL COMMENT '租户ID（拦截器自动注入）',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `category_id` bigint NULL DEFAULT NULL COMMENT '分类ID，FK引用product_category.id',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '分类名称冗余字段（格式：父/子）',
  `spu_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商品SPU编码',
  `brand` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '品牌名',
  `price` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '售价',
  `cost` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT '成本价',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1:on_shelf 2:off_shelf',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `image_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `stock_qty` int NOT NULL DEFAULT 0 COMMENT '当前库存',
  `safety_stock` int NOT NULL DEFAULT 0 COMMENT '安全库存阈值',
  `clearance` tinyint(1) NOT NULL DEFAULT 0 COMMENT '清仓标记(0否1是)',
  `shelf_life_days` int NULL DEFAULT NULL COMMENT '保质期天数(临期计算用)',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_category`(`tenant_id` ASC, `category` ASC) USING BTREE,
  INDEX `idx_tenant_category_id`(`tenant_id` ASC, `category_id` ASC) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_tenant_clearance`(`tenant_id` ASC, `clearance` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_info
-- ----------------------------
INSERT INTO `product_info` VAlUES (NULL, 1001, '阿克苏苹果', 1003, '生鲜水果/国产水果', 'SPU-PG1001', '西域果园', 12.80, 8.00, 1, NULL, NULL, 396, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '智利车厘子', 1002, '生鲜水果/进口水果', 'SPU-CL1002', '佳沛', 60.00, 45.00, 1, NULL, NULL, 197, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-06 01:56:29', NULL, 'internal-agent', NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '海南贵妃芒', 1003, '生鲜水果/国产水果', 'SPU-FG1003', '海南甄选', 15.80, 9.00, 1, NULL, NULL, 155, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '云南夏威夷果', 1005, '休闲零食/坚果炒货', 'SPU-JG1004', '滇果', 45.00, 28.00, 1, NULL, NULL, 237, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '飞鹤星飞帆奶粉3段', 2002, '奶粉辅食/婴儿奶粉', 'SPU-NF2001', '飞鹤', 328.00, 260.00, 1, NULL, NULL, 38, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '花王纸尿裤', 2004, '婴儿用品/纸尿裤', 'SPU-ZN2002', '花王', 128.00, 85.00, 1, NULL, NULL, 99, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '贝亲奶瓶', 2005, '婴儿用品/喂养用品', 'SPU-PP2003', '贝亲', 44.00, 28.00, 1, NULL, NULL, 97, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '农夫山泉天然水', 1007, '饮料冲调/果汁饮料', 'SPU-NS3001', '农夫山泉', 2.00, 1.20, 1, NULL, NULL, 384, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '汇源100%橙汁', 1007, '饮料冲调/果汁饮料', 'SPU-HY3002', '汇源', 12.80, 8.00, 1, NULL, NULL, 337, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '康师傅冰红茶', 1008, '饮料冲调/茶饮', 'SPU-KSF3003', '康师傅', 3.50, 2.00, 1, NULL, NULL, 396, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '统一绿茶', 1008, '饮料冲调/茶饮', 'SPU-TY3004', '统一', 3.50, 2.00, 1, NULL, NULL, 373, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '特仑苏纯牛奶', 1010, '乳品蛋类/鲜牛奶', 'SPU-TLS3005', '蒙牛', 65.00, 48.00, 1, NULL, NULL, 392, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '光明如实酸奶', 1011, '乳品蛋类/酸奶', 'SPU-GM3006', '光明', 8.80, 5.50, 1, NULL, NULL, 316, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-06 01:16:07', NULL, 'system', NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '伊利安慕希', 1011, '乳品蛋类/酸奶', 'SPU-YL3007', '伊利', 12.00, 7.50, 1, NULL, NULL, 411, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, 'system', NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '都乐香蕉', 1003, '生鲜水果/国产水果', 'SPU-XJ3008', '都乐', 6.80, 4.00, 1, NULL, NULL, 427, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '佳沛奇异果', 1002, '生鲜水果/进口水果', 'SPU-QY3009', '佳沛', 8.80, 5.50, 1, NULL, NULL, 347, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '三只松鼠每日坚果', 1005, '休闲零食/坚果炒货', 'SPU-JG3010', '三只松鼠', 29.90, 18.00, 1, NULL, NULL, 372, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '良品铺子肉松饼', 1005, '休闲零食/坚果炒货', 'SPU-RS3011', '良品铺子', 19.90, 12.00, 1, NULL, NULL, 324, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1001, '百草味手撕面包', 1005, '休闲零食/坚果炒货', 'SPU-MB3012', '百草味', 15.90, 9.50, 1, NULL, NULL, 385, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '君乐宝至臻奶粉2段', 2002, '奶粉辅食/婴儿奶粉', 'SPU-JLB3013', '君乐宝', 268.00, 210.00, 1, NULL, NULL, 166, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '伊利金领冠奶粉1段', 2002, '奶粉辅食/婴儿奶粉', 'SPU-YL3014', '伊利', 258.00, 200.00, 1, NULL, NULL, 260, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '亨氏米粉', 2001, '奶粉辅食/婴儿奶粉', 'SPU-HZ3015', '亨氏', 35.00, 22.00, 1, NULL, NULL, 193, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '妙思乐洗发沐浴露', 2007, '洗护用品/婴儿洗护', 'SPU-MSL3016', '妙思乐', 78.00, 52.00, 1, NULL, NULL, 194, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '强生婴儿润肤油', 2008, '洗护用品/护肤乳液', 'SPU-QS3017', '强生', 45.00, 28.00, 1, NULL, NULL, 181, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, 'Aveeno婴儿润肤霜', 2008, '洗护用品/护肤乳液', 'SPU-AN3018', 'Aveeno', 88.00, 58.00, 1, NULL, NULL, 223, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '全棉时代婴儿连体衣', 2010, '童装玩具/婴儿服饰', 'SPU-CT3019', '全棉时代', 69.00, 42.00, 1, NULL, NULL, 344, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '巴拉巴拉童装T恤', 2010, '童装玩具/婴儿服饰', 'SPU-BLB3020', '巴拉巴拉', 49.00, 30.00, 1, NULL, NULL, 209, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '费雪积木', 2011, '童装玩具/益智玩具', 'SPU-FX3021', '费雪', 99.00, 65.00, 1, NULL, NULL, 174, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '布鲁可启初精灵', 2011, '童装玩具/益智玩具', 'SPU-BLK3022', '布鲁可', 128.00, 85.00, 1, NULL, NULL, 171, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-03 12:28:47', NULL, NULL, NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '帮宝适纸尿裤', 2004, '婴儿用品/纸尿裤', 'SPU-BBS3023', '帮宝适', 99.00, 72.00, 1, NULL, NULL, 349, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-06 01:43:06', NULL, 'admin', NULL, NULL);
INSERT INTO `product_info` VAlUES (NULL, 1002, '新安怡安抚奶嘴', 2005, '婴儿用品/喂养用品', 'SPU-XAY3024', '新安怡', 39.00, 24.00, 1, NULL, NULL, 188, 10, 0, NULL, 0, '2026-07-30 01:34:54', '2026-08-06 01:43:52', NULL, 'admin', NULL, NULL);

-- ----------------------------
-- Table structure for product_review
-- ----------------------------
DROP TABLE IF EXISTS `product_review`;
CREATE TABLE `product_review`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `product_id` bigint NOT NULL COMMENT '商品ID，FK引用product_info.id',
  `rating` int NOT NULL COMMENT '评分1-5',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '评价内容',
  `images` json NULL COMMENT '评价图片URL列表',
  `status` tinyint NOT NULL COMMENT 'pending/approved/rejected',
  `reply_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '商家回复',
  `reply_at` datetime NULL DEFAULT NULL COMMENT '回复时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product_created`(`product_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品评价表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_review
-- ----------------------------
INSERT INTO `product_review` VAlUES (NULL, 1001, 3009, 3, '用了几天，效果明显，值得入手。', NULL, 2, NULL, NULL, 0, '2026-07-12 00:00:00', 'member_3018', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3015, 2, '发货速度快，东西是正品，满意。', NULL, 2, NULL, NULL, 0, '2026-07-02 21:00:00', 'member_3027', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3002, 4, '发货速度快，东西是正品，满意。', NULL, 2, NULL, NULL, 0, '2026-07-26 14:00:00', 'member_3006', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3023, 5, '性价比高，比超市便宜，推荐购买。', NULL, 2, NULL, NULL, 0, '2026-07-15 08:00:00', 'member_3032', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3022, 5, '用了几天，效果明显，值得入手。', NULL, 2, NULL, NULL, 0, '2026-07-06 13:00:00', 'member_3032', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3005, 4, '价格实惠，活动力度大，囤货了。', NULL, 2, NULL, NULL, 0, '2026-07-01 11:00:00', 'member_3003', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3012, 5, '商品质量不错，物流也很快，下次还会购买。', NULL, 2, NULL, NULL, 0, '2026-07-05 01:00:00', 'member_3016', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3001, 5, '客服态度好，物流快，产品也不错。', NULL, 2, NULL, NULL, 0, '2026-07-20 05:00:00', 'member_3016', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3001, 5, '一直买这个牌子，品质有保障。', NULL, 2, NULL, NULL, 0, '2026-07-16 13:00:00', 'member_3018', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3010, 5, '回购第三次了，一如既往的好。', NULL, 2, NULL, NULL, 0, '2026-07-15 16:00:00', 'member_3015', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3016, 5, '宝宝很喜欢，会继续回购。', NULL, 2, NULL, NULL, 0, '2026-07-24 06:00:00', 'member_3028', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3003, 5, '日期很新鲜，满意。', NULL, 2, NULL, NULL, 0, '2026-07-23 02:00:00', 'member_3014', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3017, 4, '性价比高，比超市便宜，推荐购买。', NULL, 2, NULL, NULL, 0, '2026-07-03 07:00:00', 'member_3033', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3014, 2, '商品质量不错，物流也很快，下次还会购买。', NULL, 2, NULL, NULL, 0, '2026-07-04 10:00:00', 'member_3026', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3017, 5, '一般般，没有想象中那么好。', NULL, 2, NULL, NULL, 0, '2026-07-19 10:00:00', 'member_3029', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3008, 4, '回购第三次了，一如既往的好。', NULL, 2, NULL, NULL, 0, '2026-07-09 13:00:00', 'member_3001', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3002, 4, '客服态度好，物流快，产品也不错。', NULL, 2, NULL, NULL, 0, '2026-07-15 14:00:00', 'member_3015', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3006, 4, '一般般，没有想象中那么好。', NULL, 2, NULL, NULL, 0, '2026-07-18 16:00:00', 'member_3001', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3009, 5, '日期很新鲜，满意。', NULL, 2, NULL, NULL, 0, '2026-07-11 06:00:00', 'member_3005', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3010, 5, '日期很新鲜，满意。', NULL, 2, NULL, NULL, 0, '2026-07-11 13:00:00', 'member_3002', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3014, 5, '日期很新鲜，满意。', NULL, 2, NULL, NULL, 0, '2026-07-13 00:00:00', 'member_3029', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3009, 4, '日期很新鲜，满意。', NULL, 2, NULL, NULL, 0, '2026-07-10 08:00:00', 'member_3006', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3016, 5, '回购第三次了，一如既往的好。', NULL, 2, NULL, NULL, 0, '2026-07-10 12:00:00', 'member_3027', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1001, 3002, 5, '回购第三次了，一如既往的好。', NULL, 2, NULL, NULL, 0, '2026-07-05 16:00:00', 'member_3010', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3015, 4, '回购第三次了，一如既往的好。', NULL, 2, NULL, NULL, 0, '2026-07-26 03:00:00', 'member_3022', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3013, 5, '日期很新鲜，满意。', NULL, 2, NULL, NULL, 0, '2026-07-09 10:00:00', 'member_3019', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3013, 5, '客服态度好，物流快，产品也不错。', NULL, 2, NULL, NULL, 0, '2026-07-25 17:00:00', 'member_3033', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3022, 5, '发货速度快，东西是正品，满意。', NULL, 2, NULL, NULL, 0, '2026-07-17 22:00:00', 'member_3036', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3014, 3, '一直买这个牌子，品质有保障。', NULL, 2, NULL, NULL, 0, '2026-07-07 04:00:00', 'member_3028', NULL, NULL);
INSERT INTO `product_review` VAlUES (NULL, 1002, 3016, 5, '发货速度快，东西是正品，满意。', NULL, 2, NULL, NULL, 0, '2026-07-28 20:00:00', 'member_3025', NULL, NULL);

-- ----------------------------
-- Table structure for product_sku
-- ----------------------------
DROP TABLE IF EXISTS `product_sku`;
CREATE TABLE `product_sku`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `sku_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SKU编码，租户内唯一',
  `sku_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SKU名称，如\"红色-XL\"',
  `spec_json` json NOT NULL COMMENT '规格键值对，如{\"颜色\":\"红\",\"尺寸\":\"XL\"}',
  `price` decimal(15, 2) NOT NULL COMMENT 'SKU售价',
  `cost` decimal(15, 2) NOT NULL DEFAULT 0.00 COMMENT 'SKU成本',
  `stock_qty` int NOT NULL DEFAULT 0 COMMENT '冗余总库存（汇总各门店）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT 'on_shelf上架/off_shelf下架',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_sku_code`(`tenant_id` ASC, `sku_code` ASC) USING BTREE,
  INDEX `idx_tenant_product`(`tenant_id` ASC, `product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品SKU表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_sku
-- ----------------------------
INSERT INTO `product_sku` VAlUES (NULL, 1001, 1001, 'PG-500G', '阿克苏苹果500g', '{\"规格\": \"500g\"}', 12.80, 8.00, 198, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 1001, 'PG-1KG', '阿克苏苹果1kg', '{\"规格\": \"1kg\"}', 22.80, 16.00, 198, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 1002, 'CL-J', '智利车厘子J级', '{\"规格\": \"J级\"}', 68.00, 45.00, 99, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 1002, 'CL-JJ', '智利车厘子JJ级', '{\"规格\": \"JJ级\"}', 98.00, 65.00, 98, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 1004, 'JG-YW', '夏威夷果原味', '{\"口味\": \"原味\"}', 45.00, 28.00, 117, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 1004, 'JG-NX', '夏威夷果奶香', '{\"口味\": \"奶香\"}', 48.00, 30.00, 120, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 2002, 'ZNK-L', '花王纸尿裤L码', '{\"尺寸\": \"L码\"}', 128.00, 85.00, 49, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 2002, 'ZNK-XL', '花王纸尿裤XL码', '{\"尺寸\": \"XL码\"}', 138.00, 92.00, 50, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3003, 'KSF-BHC-500', '冰红茶500ml', '{\"容量\": \"500ml\"}', 3.50, 2.00, 177, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3003, 'KSF-BHC-1L', '冰红茶1L', '{\"容量\": \"1L\"}', 5.50, 3.50, 219, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3005, 'TLS-12', '特仑苏250ml*12', '{\"规格\": \"250ml*12\"}', 65.00, 48.00, 234, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3005, 'TLS-24', '特仑苏250ml*24', '{\"规格\": \"250ml*24\"}', 120.00, 92.00, 158, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3007, 'YL-AMX-YW', '安慕希原味', '{\"口味\": \"原味\"}', 12.00, 7.50, 197, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3007, 'YL-AMX-CM', '安慕希草莓味', '{\"口味\": \"草莓味\"}', 12.90, 8.00, 214, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3009, 'QY-DG', '奇异果单果', '{\"规格\": \"单果\"}', 8.80, 5.50, 177, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1001, 3009, 'QY-LH', '奇异果礼盒', '{\"规格\": \"礼盒\"}', 88.00, 58.00, 170, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3014, 'YL-JLG-400', '金领冠400g', '{\"规格\": \"400g\"}', 258.00, 200.00, 119, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3014, 'YL-JLG-900', '金领冠900g', '{\"规格\": \"900g\"}', 358.00, 280.00, 141, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3015, 'HZ-MF-YW', '亨氏米粉原味', '{\"口味\": \"原味\"}', 35.00, 22.00, 119, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3015, 'HZ-MF-SC', '亨氏米粉蔬菜味', '{\"口味\": \"蔬菜味\"}', 38.00, 24.00, 74, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3018, 'AN-RC', 'Aveeno日常款', '{\"类型\": \"日常\"}', 88.00, 58.00, 113, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3018, 'AN-TR', 'Aveeno特润款', '{\"类型\": \"特润\"}', 98.00, 65.00, 110, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3019, 'CT-59', '连体衣59码', '{\"尺码\": \"59码\"}', 69.00, 42.00, 108, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3019, 'CT-66', '连体衣66码', '{\"尺码\": \"66码\"}', 72.00, 44.00, 100, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3019, 'CT-73', '连体衣73码', '{\"尺码\": \"73码\"}', 75.00, 46.00, 136, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3023, 'BBS-M', '帮宝适M码', '{\"尺寸\": \"M码\"}', 118.00, 78.00, 106, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3023, 'BBS-L', '帮宝适L码', '{\"尺寸\": \"L码\"}', 128.00, 85.00, 99, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);
INSERT INTO `product_sku` VAlUES (NULL, 1002, 3023, 'BBS-XL', '帮宝适XL码', '{\"尺寸\": \"XL码\"}', 138.00, 92.00, 144, 1, 0, '2026-07-30 01:34:54', '2026-08-03 14:48:52', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for product_spec
-- ----------------------------
DROP TABLE IF EXISTS `product_spec`;
CREATE TABLE `product_spec`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `spec_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规格名，如\"颜色\"/\"尺寸\"',
  `spec_values` json NOT NULL COMMENT '规格值数组，如[\"红\",\"蓝\"]',
  `sort_order` int NOT NULL DEFAULT 0,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_product`(`tenant_id` ASC, `product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品规格定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_spec
-- ----------------------------
INSERT INTO `product_spec` VAlUES (NULL, 1001, 1001, '规格', '[\"500g\", \"1kg\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1001, 1002, '规格', '[\"J级\", \"JJ级\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1001, 1004, '口味', '[\"原味\", \"奶香\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1002, 2002, '尺寸', '[\"L码\", \"XL码\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1001, 3003, '容量', '[\"500ml\", \"1L\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1001, 3005, '规格', '[\"250ml*12\", \"250ml*24\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1001, 3007, '口味', '[\"原味\", \"草莓味\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1001, 3009, '规格', '[\"单果\", \"礼盒\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1002, 3014, '规格', '[\"400g\", \"900g\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1002, 3015, '口味', '[\"原味\", \"蔬菜味\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1002, 3018, '类型', '[\"日常\", \"特润\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1002, 3019, '尺码', '[\"59码\", \"66码\", \"73码\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_spec` VAlUES (NULL, 1002, 3023, '尺寸', '[\"M码\", \"L码\", \"XL码\"]', 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for product_stock
-- ----------------------------
DROP TABLE IF EXISTS `product_stock`;
CREATE TABLE `product_stock`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店ID，NULL=租户中心仓',
  `product_id` bigint NOT NULL,
  `sku_id` bigint NULL DEFAULT NULL COMMENT 'SKU ID，无规格商品为NULL',
  `available_qty` int NOT NULL DEFAULT 0 COMMENT '可用库存',
  `locked_qty` int NOT NULL DEFAULT 0 COMMENT '锁定库存（待付款订单）',
  `in_transit_qty` int NOT NULL DEFAULT 0 COMMENT '在途库存（采购在途）',
  `safety_stock` int NOT NULL DEFAULT 0 COMMENT '安全库存阈值',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_store_sku`(`tenant_id` ASC, `store_id` ASC, `sku_id` ASC) USING BTREE,
  INDEX `idx_tenant_product`(`tenant_id` ASC, `product_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品库存账户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of product_stock
-- ----------------------------
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 1001, 1001, 92, 0, 0, 20, 0, '2026-07-30 01:34:54', '2026-08-06 06:28:49', NULL, 'internal-agent', NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 1001, 1002, 100, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 1002, 1003, 49, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 1002, 1004, 48, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 1004, 1005, 60, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 1004, 1006, 60, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 1003, NULL, 75, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 1001, 1001, 105, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-08-06 06:27:41', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 1001, 1002, 98, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 1002, 1003, 50, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 1002, 1004, 50, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 1004, 1005, 57, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 1004, 1006, 60, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 1003, NULL, 80, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 2001, NULL, 38, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 2002, 2001, 49, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 2002, 2002, 50, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 2003, NULL, 97, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3001, NULL, 124, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3001, NULL, 72, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3001, NULL, 74, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3001, NULL, 114, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3002, NULL, 70, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3002, NULL, 52, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3002, NULL, 119, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3002, NULL, 96, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3003, 3001, 57, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3003, 3001, 61, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3003, 3001, 28, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3003, 3001, 31, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3003, 3002, 59, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3003, 3002, 46, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3003, 3002, 61, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3003, 3002, 53, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3004, NULL, 110, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3004, NULL, 109, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3004, NULL, 95, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3004, NULL, 59, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3005, 3003, 74, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3005, 3003, 54, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3005, 3003, 37, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3005, 3003, 69, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3005, 3004, 43, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3005, 3004, 45, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3005, 3004, 37, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3005, 3004, 33, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3006, NULL, 101, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3006, NULL, 85, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3006, NULL, 78, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3006, NULL, 52, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3007, 3005, 69, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3007, 3005, 28, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3007, 3005, 55, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3007, 3005, 45, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3007, 3006, 50, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3007, 3006, 39, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3007, 3006, 51, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3007, 3006, 74, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3008, NULL, 100, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3008, NULL, 103, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3008, NULL, 118, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3008, NULL, 106, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3009, 3007, 27, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3009, 3007, 72, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3009, 3007, 47, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3009, 3007, 31, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3009, 3008, 67, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3009, 3008, 29, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3009, 3008, 32, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3009, 3008, 42, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3010, NULL, 78, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3010, NULL, 78, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3010, NULL, 95, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3010, NULL, 121, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3011, NULL, 111, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3011, NULL, 63, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3011, NULL, 73, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3011, NULL, 77, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1001, 3012, NULL, 43, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1002, 3012, NULL, 95, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1004, 3012, NULL, 126, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1001, 1005, 3012, NULL, 121, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3013, NULL, 59, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3013, NULL, 107, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3014, 3009, 44, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3014, 3009, 75, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3014, 3010, 74, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3014, 3010, 67, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3015, 3011, 69, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3015, 3011, 50, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3015, 3012, 30, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3015, 3012, 44, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3016, NULL, 87, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3016, NULL, 107, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3017, NULL, 98, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3017, NULL, 83, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3018, 3013, 39, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3018, 3013, 74, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3018, 3014, 52, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3018, 3014, 58, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3019, 3015, 58, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3019, 3015, 50, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3019, 3016, 27, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3019, 3016, 73, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3019, 3017, 61, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3019, 3017, 75, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3020, NULL, 127, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3020, NULL, 82, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3021, NULL, 77, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3021, NULL, 97, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3022, NULL, 73, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3022, NULL, 98, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3023, 3018, 56, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3023, 3018, 50, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3023, 3019, 37, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3023, 3019, 62, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3023, 3020, 72, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3023, 3020, 72, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1003, 3024, NULL, 83, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `product_stock` VAlUES (NULL, 1002, 1006, 3024, NULL, 105, 0, 0, 10, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for promotion
-- ----------------------------
DROP TABLE IF EXISTS `promotion`;
CREATE TABLE `promotion`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '活动名称',
  `type` tinyint NOT NULL COMMENT 'coupon/discount/flash_sale',
  `target_type` tinyint NOT NULL COMMENT 'product/category/all',
  `target_ids` json NULL COMMENT '适用对象ID列表',
  `status` tinyint NOT NULL COMMENT 'pending/active/expired',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `end_time` datetime NOT NULL COMMENT '结束时间',
  `rules` json NULL COMMENT '活动规则JSON',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status_time`(`tenant_id` ASC, `status` ASC, `start_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '促销活动表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of promotion
-- ----------------------------
INSERT INTO `promotion` VAlUES (NULL, 1001, '夏季清凉节-租户1001', 1, 1, NULL, 2, '2026-07-10 00:00:00', '2026-07-21 00:00:00', '{\"desc\": \"满减优惠\", \"discount\": null}', 0, '2026-07-30 00:00:00', '2026-08-03 14:16:57', NULL, NULL, NULL, NULL);
INSERT INTO `promotion` VAlUES (NULL, 1001, '母婴关爱周-租户1001', 2, 1, NULL, 2, '2026-07-09 00:00:00', '2026-07-18 00:00:00', '{\"desc\": \"折扣活动\", \"discount\": 0.88}', 0, '2026-07-30 00:00:00', '2026-08-03 14:16:57', NULL, NULL, NULL, NULL);
INSERT INTO `promotion` VAlUES (NULL, 1001, '限时秒杀日-租户1001', 3, 1, NULL, 3, '2026-07-11 00:00:00', '2026-07-30 00:00:00', '{\"desc\": \"秒杀活动\", \"discount\": null}', 0, '2026-07-30 00:00:00', '2026-08-03 14:16:57', NULL, NULL, NULL, NULL);
INSERT INTO `promotion` VAlUES (NULL, 1002, '夏季清凉节-租户1002', 1, 1, NULL, 2, '2026-07-04 00:00:00', '2026-07-16 00:00:00', '{\"desc\": \"满减优惠\", \"discount\": null}', 0, '2026-07-30 00:00:00', '2026-08-03 14:16:57', NULL, NULL, NULL, NULL);
INSERT INTO `promotion` VAlUES (NULL, 1002, '母婴关爱周-租户1002', 2, 1, NULL, 2, '2026-07-09 00:00:00', '2026-07-24 00:00:00', '{\"desc\": \"折扣活动\", \"discount\": 0.88}', 0, '2026-07-30 00:00:00', '2026-08-03 14:16:57', NULL, NULL, NULL, NULL);
INSERT INTO `promotion` VAlUES (NULL, 1002, '限时秒杀日-租户1002', 3, 1, NULL, 3, '2026-07-07 00:00:00', '2026-07-19 00:00:00', '{\"desc\": \"秒杀活动\", \"discount\": null}', 0, '2026-07-30 00:00:00', '2026-08-03 14:16:57', NULL, NULL, NULL, NULL);
INSERT INTO `promotion` VAlUES (NULL, 1001, '????-????', 2, 2, '[\"3012\", \"3011\"]', 2, '2026-08-01 00:00:00', '2026-08-31 23:59:59', '{\"discount\": 0.85}', 1, '2026-08-02 00:53:43', '2026-08-03 14:17:08', 'test1001_admin', 'test1001_admin', '2026-08-02 00:53:59', 'test1001_admin');

-- ----------------------------
-- Table structure for sales_record
-- ----------------------------
DROP TABLE IF EXISTS `sales_record`;
CREATE TABLE `sales_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL COMMENT '门店ID（门店拦截器自动注入，NULL=租户级汇总）',
  `product_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `sales_amount` decimal(15, 2) NOT NULL DEFAULT 0.00,
  `sales_qty` int NOT NULL DEFAULT 0,
  `order_count` int NOT NULL DEFAULT 0,
  `record_date` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充，批量任务回退system）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_date`(`tenant_id` ASC, `record_date` ASC) USING BTREE,
  INDEX `idx_tenant_store_date`(`tenant_id` ASC, `store_id` ASC, `record_date` ASC) USING BTREE,
  INDEX `idx_tenant_category_date`(`tenant_id` ASC, `category` ASC, `record_date` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '销售记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sales_record
-- ----------------------------

-- ----------------------------
-- Table structure for stock_movement
-- ----------------------------
DROP TABLE IF EXISTS `stock_movement`;
CREATE TABLE `stock_movement`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `sku_id` bigint NULL DEFAULT NULL,
  `stock_id` bigint NOT NULL COMMENT '库存账户ID，FK引用product_stock.id',
  `movement_type` tinyint NOT NULL COMMENT 'inbound入库/outbound出库/adjust调整/reservation锁定/release释放/check_gain盘盈/check_loss盘亏',
  `change_qty` int NOT NULL COMMENT '变动数量（正数增加，负数减少）',
  `before_qty` int NOT NULL COMMENT '变动前可用库存',
  `after_qty` int NOT NULL COMMENT '变动后可用库存',
  `biz_type` tinyint NULL DEFAULT NULL COMMENT 'order/purchase/adjust/refund/manual',
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联单据号',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_product`(`tenant_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `idx_tenant_store_product`(`tenant_id` ASC, `store_id` ASC, `product_id` ASC) USING BTREE,
  INDEX `idx_tenant_biz`(`tenant_id` ASC, `biz_type` ASC, `biz_no` ASC) USING BTREE,
  INDEX `idx_tenant_created`(`tenant_id` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '库存流水表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of stock_movement
-- ----------------------------
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 1001, 1001, 5001, 2, -2, 100, 98, 1, '1001SO0001', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 1002, 1003, 5003, 2, -1, 50, 49, 1, '1001SO0001', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 1004, 1005, 5012, 2, -3, 60, 57, 1, '1001SO0002', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 1003, NULL, 5007, 2, -5, 80, 75, 1, '1001SO0003', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 1002, 1004, 5004, 2, -2, 50, 48, 1, '1001SO0004', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 1001, 1002, 5009, 2, -2, 100, 98, 1, '1001SO0005', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 2001, NULL, 6001, 2, -2, 40, 38, 1, '1002SO0001', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 2002, 2001, 6002, 2, -1, 50, 49, 1, '1002SO0001', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 2003, NULL, 6004, 2, -3, 100, 97, 1, '1002SO0002', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3023, 3019, 7098, 2, -1, 63, 62, 1, '1002SO0003', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3023, 3019, 7097, 2, -2, 39, 37, 1, '1002SO0004', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3018, 3014, 7081, 2, -4, 62, 58, 1, '1002SO0004', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3011, NULL, 7060, 2, -2, 84, 82, 1, '1001SO0007', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3007, 3006, 7040, 2, -2, 78, 76, 1, '1001SO0008', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3003, 3001, 7012, 2, -3, 36, 33, 1, '1001SO0008', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1004, 3006, NULL, 7031, 2, -3, 81, 78, 1, '1001SO0009', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3014, 3009, 7067, 2, -4, 48, 44, 1, '1002SO0005', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3022, NULL, 7094, 2, -1, 100, 99, 1, '1002SO0006', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3003, 3001, 7009, 2, -1, 60, 59, 1, '1001SO0010', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3017, NULL, 7078, 2, -5, 92, 87, 1, '1002SO0007', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3018, 3013, 7080, 2, -1, 77, 76, 1, '1002SO0007', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3021, NULL, 7092, 2, -2, 100, 98, 1, '1002SO0007', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3020, NULL, 7090, 2, -2, 84, 82, 1, '1002SO0008', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3018, 3014, 7082, 2, -2, 64, 62, 1, '1002SO0008', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3013, NULL, 7066, 2, -5, 112, 107, 1, '1002SO0008', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3012, NULL, 7064, 2, -4, 130, 126, 1, '1001SO0011', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3005, 3004, 7028, 2, -3, 39, 36, 1, '1001SO0011', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3005, 3003, 7022, 2, -4, 58, 54, 1, '1001SO0012', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3009, 3008, 7050, 2, -3, 32, 29, 1, '1001SO0012', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3008, NULL, 7042, 2, -5, 112, 107, 1, '1001SO0012', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3021, NULL, 7092, 2, -1, 98, 97, 1, '1002SO0009', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3003, 3001, 7009, 2, -2, 59, 57, 1, '1001SO0013', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3003, 3002, 7016, 2, -4, 62, 58, 1, '1001SO0014', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3007, 3006, 7040, 2, -2, 76, 74, 1, '1001SO0014', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3022, NULL, 7093, 2, -2, 75, 73, 1, '1002SO0010', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3023, 3018, 7095, 2, -1, 57, 56, 1, '1002SO0010', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3018, 3014, 7081, 2, -3, 58, 55, 1, '1002SO0010', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3010, NULL, 7053, 2, -3, 81, 78, 1, '1001SO0015', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3009, 3008, 7049, 2, -2, 69, 67, 1, '1001SO0015', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3012, NULL, 7061, 2, -4, 50, 46, 1, '1001SO0015', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3006, NULL, 7032, 2, -2, 58, 56, 1, '1001SO0016', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3003, 3002, 7016, 2, -2, 58, 56, 1, '1001SO0016', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3002, NULL, 7008, 2, -5, 101, 96, 1, '1001SO0016', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3022, NULL, 7094, 2, -1, 99, 98, 1, '1002SO0011', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3015, 3011, 7072, 2, -2, 52, 50, 1, '1002SO0011', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3016, NULL, 7076, 2, -1, 108, 107, 1, '1002SO0011', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3008, NULL, 7042, 2, -4, 107, 103, 1, '1001SO0017', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3010, NULL, 7054, 2, -2, 83, 81, 1, '1001SO0017', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3009, 3007, 7046, 2, -1, 73, 72, 1, '1001SO0017', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3007, 3005, 7034, 2, -1, 29, 28, 1, '1001SO0018', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3018, 3013, 7080, 2, -2, 76, 74, 1, '1002SO0014', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3007, 3005, 7036, 2, -4, 49, 45, 1, '1001SO0019', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3003, 3001, 7012, 2, -2, 33, 31, 1, '1001SO0019', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3010, NULL, 7056, 2, -3, 124, 121, 1, '1001SO0019', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1004, 3005, 3003, 7023, 2, -3, 40, 37, 1, '1001SO0020', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1004, 3002, NULL, 7007, 2, -3, 122, 119, 1, '1001SO0020', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1004, 3001, NULL, 7003, 2, -3, 82, 79, 1, '1001SO0020', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3006, NULL, 7032, 2, -4, 56, 52, 1, '1001SO0021', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3003, 3002, 7016, 2, -3, 56, 53, 1, '1001SO0022', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3001, NULL, 7004, 2, -1, 118, 117, 1, '1001SO0022', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3005, 3004, 7028, 2, -3, 36, 33, 1, '1001SO0022', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3010, NULL, 7054, 2, -3, 81, 78, 1, '1001SO0023', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3005, 3004, 7026, 2, -3, 48, 45, 1, '1001SO0024', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3003, 3001, 7010, 2, -1, 62, 61, 1, '1001SO0024', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3011, NULL, 7058, 2, -2, 65, 63, 1, '1001SO0024', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3011, NULL, 7057, 2, -1, 112, 111, 1, '1001SO0025', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3004, NULL, 7017, 2, -4, 114, 110, 1, '1001SO0025', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3018, 3014, 7082, 2, -1, 62, 61, 1, '1002SO0015', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3017, NULL, 7078, 2, -4, 87, 83, 1, '1002SO0015', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3023, 3020, 7100, 2, -2, 74, 72, 1, '1002SO0015', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3015, 3012, 7074, 2, -3, 47, 44, 1, '1002SO0016', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1006, 3018, 3014, 7082, 2, -3, 61, 58, 1, '1002SO0016', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3005, 3003, 7024, 2, -3, 73, 70, 1, '1001SO0027', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3012, NULL, 7064, 2, -5, 126, 121, 1, '1001SO0027', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3001, NULL, 7004, 2, -3, 117, 114, 1, '1001SO0027', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3012, NULL, 7061, 2, -3, 46, 43, 1, '1001SO0028', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3002, NULL, 7005, 2, -1, 71, 70, 1, '1001SO0029', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 3001, NULL, 7001, 2, -2, 126, 124, 1, '1001SO0029', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1004, 3009, 3008, 7051, 2, -1, 33, 32, 1, '1001SO0030', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1004, 3001, NULL, 7003, 2, -5, 79, 74, 1, '1001SO0030', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3011, NULL, 7060, 2, -5, 82, 77, 1, '1001SO0032', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1005, 3005, 3003, 7024, 2, -1, 70, 69, 1, '1001SO0032', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3018, 3014, 7081, 2, -3, 55, 52, 1, '1002SO0017', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3024, NULL, 7101, 2, -3, 86, 83, 1, '1002SO0017', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3021, NULL, 7091, 2, -5, 85, 80, 1, '1002SO0017', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 3002, NULL, 7006, 2, -2, 54, 52, 1, '1001SO0033', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3014, 3010, 7069, 2, -3, 77, 74, 1, '1002SO0018', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3017, NULL, 7077, 2, -1, 99, 98, 1, '1002SO0018', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3021, NULL, 7091, 2, -3, 80, 77, 1, '1002SO0018', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3019, 3017, 7087, 2, -2, 63, 61, 1, '1002SO0019', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1002, 1003, 3016, NULL, 7075, 2, -3, 90, 87, 1, '1002SO0019', '订单创建出库', '2026-07-30 01:34:54', NULL);
INSERT INTO `stock_movement` VAlUES (NULL, 1001, NULL, 1001, 1001, 5001, 1, 5, 98, 103, 2, 'PO-TEST-001', '补货到货', '2026-08-06 06:27:42', 'internal-agent');
INSERT INTO `stock_movement` VAlUES (NULL, 1001, NULL, 1001, 1001, 5001, 7, -5, 103, 98, 2, 'COUNT1785968861523', '月度盘点', '2026-08-06 06:27:42', 'internal-agent');
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1001, 1001, 1001, 5001, 2, -5, 98, 93, 3, 'TRANSFER1785968861559', '门店间调货', '2026-08-06 06:27:41', 'internal-agent');
INSERT INTO `stock_movement` VAlUES (NULL, 1001, 1002, 1001, 1001, 5008, 1, 5, 100, 105, 3, 'TRANSFER1785968861559', '门店间调货', '2026-08-06 06:27:41', 'internal-agent');
INSERT INTO `stock_movement` VAlUES (NULL, 1001, NULL, 1001, 1001, 5001, 3, 2, 93, 95, 5, NULL, '损耗补录', '2026-08-06 06:27:42', 'internal-agent');
INSERT INTO `stock_movement` VAlUES (NULL, 1001, NULL, 1001, 1001, 5001, 2, -3, 95, 92, 5, NULL, '活动领用', '2026-08-06 06:27:42', 'internal-agent');

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID（NULL=平台级全局配置）',
  `config_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置名称',
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置键，租户内唯一',
  `config_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置值',
  `config_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'string' COMMENT 'string/number/boolean/json',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '说明',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `delete_at` datetime NULL DEFAULT NULL,
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_key`(`tenant_id` ASC, `config_key` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VAlUES (NULL, NULL, '积分费率', 'points.rate', '1', 'number', '1元获取多少积分（订单完成时计算）', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_config` VAlUES (NULL, NULL, '订单超时时间(分钟)', 'order.timeout.minutes', '30', 'number', '待支付订单超时自动关闭时间', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_config` VAlUES (NULL, NULL, '安全库存默认值', 'stock.safety.default', '10', 'number', '新建商品默认安全库存阈值', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_config` VAlUES (NULL, NULL, '库存预警开关', 'stock.alert.enabled', 'true', 'boolean', '是否启用库存预警通知', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_config` VAlUES (NULL, NULL, '优惠券过期检查开关', 'coupon.expire.enabled', 'true', 'boolean', '是否启用优惠券自动过期定时任务', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_config` VAlUES (NULL, NULL, '会员等级自动升降开关', 'member.level.auto', 'true', 'boolean', '是否启用会员等级自动升降定时任务', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NULL DEFAULT NULL,
  `dict_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型',
  `dict_label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典标签，如\"已付款\"',
  `dict_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典键值，如\"paid\"',
  `dict_sort` int NOT NULL DEFAULT 0 COMMENT '显示排序',
  `css_class` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '样式属性（前端用）',
  `list_class` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '表格样式 success/info/warning/danger',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认',
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `delete_at` datetime NULL DEFAULT NULL,
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_type`(`tenant_id` ASC, `dict_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典数据表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_status', '待付款', 'pending', 1, NULL, 'warning', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_status', '已付款', 'paid', 2, NULL, 'primary', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_status', '已发货', 'shipped', 3, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_status', '已完成', 'completed', 4, NULL, 'success', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_status', '已关闭', 'closed', 5, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_status', '退款中', 'refunding', 6, NULL, 'danger', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_status', '已退款', 'refunded', 7, NULL, 'danger', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'pay_type', '微信支付', 'wechat', 1, NULL, 'success', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'pay_type', '支付宝', 'alipay', 2, NULL, 'primary', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'pay_type', '余额支付', 'balance', 3, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'pay_type', '现金', 'cash', 4, NULL, 'warning', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_type', '正常订单', 'normal', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_type', '闪购订单', 'quick', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_type', '秒杀订单', 'flash_sale', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_channel', '线上', 'online', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_channel', 'Agent下单', 'agent', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'order_channel', '手工下单', 'manual', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'coupon_type', '满减券', 'fullcut', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'coupon_type', '折扣券', 'discount', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'coupon_type', '代金券', 'cash', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'coupon_status', '未使用', 'unused', 1, NULL, 'success', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'coupon_status', '已使用', 'used', 2, NULL, 'primary', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'coupon_status', '已过期', 'expired', 3, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'coupon_status', '已退还', 'refunded', 4, NULL, 'warning', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'movement_type', '入库', 'inbound', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'movement_type', '出库', 'outbound', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'movement_type', '调整', 'adjust', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'movement_type', '锁定', 'reservation', 4, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'movement_type', '释放', 'release', 5, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'movement_type', '盘盈', 'check_gain', 6, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'movement_type', '盘亏', 'check_loss', 7, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'points_change_type', '消费获取', 'earn', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'points_change_type', '活动赠送', 'gift', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'points_change_type', '兑换消耗', 'exchange', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'points_change_type', '退款扣减', 'refund', 4, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'points_change_type', '手动调整', 'adjust', 5, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'refund_status', '待审核', 'pending', 1, NULL, 'warning', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'refund_status', '审核通过', 'approved', 2, NULL, 'primary', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'refund_status', '审核拒绝', 'rejected', 3, NULL, 'danger', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'refund_status', '已退款', 'refunded', 4, NULL, 'success', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'refund_type', '全额退款', 'full', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'refund_type', '部分退款', 'partial', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'member_level', '普通会员', 'normal', 1, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'member_level', '银卡会员', 'silver', 2, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'member_level', '金卡会员', 'gold', 3, NULL, 'warning', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'member_level', '钻石会员', 'diamond', 4, NULL, 'danger', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'product_status', '上架', 'on_shelf', 1, NULL, 'success', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'product_status', '下架', 'off_shelf', 2, NULL, 'danger', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'promotion_type', '优惠券', 'coupon', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'promotion_type', '折扣', 'discount', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'promotion_type', '秒杀', 'flash_sale', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'promotion_status', '未开始', 'pending', 1, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'promotion_status', '进行中', 'active', 2, NULL, 'success', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'promotion_status', '已结束', 'expired', 3, NULL, 'info', 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'config_type', '字符串', 'string', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'config_type', '数字', 'number', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'config_type', '布尔', 'boolean', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'config_type', 'JSON', 'json', 4, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'business_type', '其他', 'OTHER', 1, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'business_type', '新增', 'INSERT', 2, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'business_type', '修改', 'UPDATE', 3, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'business_type', '删除', 'DELETE', 4, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'business_type', '导出', 'EXPORT', 5, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_data` VAlUES (NULL, NULL, 'business_type', '导入', 'IMPORT', 6, NULL, NULL, 0, 1, NULL, 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID（NULL=平台全局字典）',
  `dict_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典名称，如\"订单状态\"',
  `dict_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字典类型，如\"order_status\"',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '0停用 1启用',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `delete_at` datetime NULL DEFAULT NULL,
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_type`(`tenant_id` ASC, `dict_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '字典类型表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '订单状态', 'order_status', 1, '订单状态枚举', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '支付方式', 'pay_type', 1, '订单支付方式', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '订单类型', 'order_type', 1, '订单业务类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '订单渠道', 'order_channel', 1, '订单来源渠道', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '优惠券类型', 'coupon_type', 1, '优惠券模板类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '优惠券状态', 'coupon_status', 1, '用户优惠券状态', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '库存流水类型', 'movement_type', 1, '库存变动类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '积分变动类型', 'points_change_type', 1, '会员积分变动类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '退款状态', 'refund_status', 1, '退款单状态', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '退款类型', 'refund_type', 1, '退款方式', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '会员等级', 'member_level', 1, '会员等级类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '商品状态', 'product_status', 1, '商品上下架状态', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '促销类型', 'promotion_type', 1, '促销活动类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '促销状态', 'promotion_status', 1, '促销活动状态', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '配置类型', 'config_type', 1, '系统配置值类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_dict_type` VAlUES (NULL, NULL, '操作类型', 'business_type', 1, '操作日志业务类型', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menu_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父菜单ID，0=根',
  `menu_type` tinyint(1) NOT NULL COMMENT '1目录 2菜单 3按钮',
  `perms` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '权限标识，如 rbac:user:list',
  `path` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `component` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `order_num` int NOT NULL DEFAULT 0,
  `visible` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1显示 0隐藏',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_parent`(`parent_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单/权限表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VAlUES (NULL, '系统管理', 0, 1, NULL, '/system', NULL, 'system', 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '用户管理', 1, 2, 'rbac:user:list', 'user', 'system/user/index', 'user', 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '角色管理', 1, 2, 'rbac:role:list', 'role', 'system/role/index', 'role', 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '菜单管理', 1, 2, 'rbac:menu:list', 'menu', 'system/menu/index', 'menu', 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '门店管理', 1, 2, 'rbac:store:list', 'store', 'system/store/index', 'store', 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '操作日志', 1, 2, 'system:operlog:list', 'operlog', 'system/operlog/index', 'log', 5, 0, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '系统配置', 1, 2, 'system:config:list', 'config', 'system/config/index', 'edit', 6, 0, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '数据字典', 1, 2, 'system:dict:list', 'dict', 'system/dict/index', 'dict', 7, 0, 1, 0, '2026-08-01 19:47:23', '2026-08-06 00:45:08', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '租户管理', 1, 2, 'system:tenant:list', 'tenant', 'system/tenant/index', 'office_building', 8, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '业务管理', 0, 1, NULL, '/business', NULL, 'shopping', 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '订单管理', 10, 2, 'business:order:list', 'order', 'business/order/index', 'order', 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '退款管理', 10, 2, 'business:refund:list', 'refund', 'business/refund/index', 'refund', 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '库存管理', 10, 2, 'business:stock:list', 'stock', 'business/stock/index', 'stock', 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品规格', 10, 2, 'business:sku:list', 'sku', 'business/sku/index', 'sku', 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '优惠券管理', 10, 2, 'business:coupon:list', 'coupon', 'business/coupon/index', 'coupon', 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员积分', 10, 2, 'business:points:list', 'points', 'business/points/index', 'points', 6, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '经营报表', 10, 2, 'business:report:list', 'report', 'business/report/index', 'report', 7, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品管理', 10, 2, 'business:product:list', 'product', 'business/product/index', 'goods', 0, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '促销管理', 10, 2, 'business:promotion:list', 'promotion', 'business/promotion/index', 'discount', 8, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '评价管理', 10, 2, 'business:review:list', 'review', 'business/review/index', 'star', 9, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品分类', 10, 2, 'business:category:list', 'category', 'business/category/index', 'files', 10, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员标签', 10, 2, 'business:membertag:list', 'member-tag', 'business/member-tag/index', 'price-tag', 11, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '用户优惠券', 10, 2, 'business:usercoupon:list', 'user-coupon', 'business/user-coupon/index', 'ticket', 12, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '知识库管理', 0, 1, NULL, '/kb', NULL, 'education', 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '文档管理', 30, 2, 'kb:query', 'doc', 'kb/doc/index', 'document', 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-04 07:43:02', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '同义词管理', 30, 2, 'kb:synonym:query', 'synonym', 'kb/synonym/index', 'edit', 2, 1, 1, 1, '2026-08-01 19:47:23', '2026-08-07 08:43:03', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '智能助手', 0, 1, NULL, '/agent', NULL, 'chat', 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, 'Agent 对话', 40, 2, 'agent:chat', 'index', 'agent/index', 'chat-dot', 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '流程配置', 1, 2, 'system:flow:list', 'flow-config', 'system/flow-config/index', 'setting', 9, 1, 1, 1, '2026-08-01 19:47:23', '2026-08-07 08:44:32', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '用户查询', 2, 3, 'rbac:user:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '用户新增', 2, 3, 'rbac:user:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '用户修改', 2, 3, 'rbac:user:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '用户删除', 2, 3, 'rbac:user:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '重置密码', 2, 3, 'rbac:user:reset', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '分配角色', 2, 3, 'rbac:user:assign', NULL, NULL, NULL, 6, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '角色查询', 3, 3, 'rbac:role:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '角色新增', 3, 3, 'rbac:role:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '角色修改', 3, 3, 'rbac:role:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '角色删除', 3, 3, 'rbac:role:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '分配菜单', 3, 3, 'rbac:role:assign', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '菜单查询', 4, 3, 'rbac:menu:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '菜单新增', 4, 3, 'rbac:menu:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '菜单修改', 4, 3, 'rbac:menu:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '菜单删除', 4, 3, 'rbac:menu:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '门店查询', 5, 3, 'rbac:store:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '门店新增', 5, 3, 'rbac:store:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '门店修改', 5, 3, 'rbac:store:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '门店删除', 5, 3, 'rbac:store:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '租户查询', 9, 3, 'system:tenant:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '租户新增', 9, 3, 'system:tenant:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '租户修改', 9, 3, 'system:tenant:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '租户删除', 9, 3, 'system:tenant:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '订单查询', 11, 3, 'business:order:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '订单创建', 11, 3, 'business:order:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '订单修改', 11, 3, 'business:order:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '订单删除', 11, 3, 'business:order:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '退款审核', 11, 3, 'business:order:refund', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '退款查询', 12, 3, 'business:refund:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '退款审核', 12, 3, 'business:refund:audit', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '退款撤销', 12, 3, 'business:refund:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-06 12:17:06', '2026-08-06 12:17:06', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '库存查询', 13, 3, 'business:stock:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '库存调整', 13, 3, 'business:stock:adjust', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '流水查询', 13, 3, 'business:stock:movement', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '规格查询', 14, 3, 'business:sku:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, 'SKU新增', 14, 3, 'business:sku:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, 'SKU修改', 14, 3, 'business:sku:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, 'SKU删除', 14, 3, 'business:sku:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '券模板查询', 15, 3, 'business:coupon:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '券模板新增', 15, 3, 'business:coupon:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '券模板修改', 15, 3, 'business:coupon:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '券模板删除', 15, 3, 'business:coupon:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '券发放', 15, 3, 'business:coupon:issue', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '用户券查询', 23, 3, 'business:usercoupon:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '积分流水查询', 16, 3, 'business:points:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '积分调整', 16, 3, 'business:points:adjust', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员标签查询', 22, 3, 'business:membertag:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员标签管理', 22, 3, 'business:membertag:manage', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '积分规则与兑换', 16, 3, 'business:points:edit', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-06 12:17:06', '2026-08-06 12:17:06', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '销售报表', 17, 3, 'business:report:sales', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '库存报表', 17, 3, 'business:report:inventory', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '订单报表', 17, 3, 'business:report:order', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员报表', 17, 3, 'business:report:member', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '营销报表', 17, 3, 'business:report:coupon', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '财务报表', 17, 3, 'business:report:finance', NULL, NULL, NULL, 6, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '统计数据', 17, 3, 'business:stats:query', NULL, NULL, NULL, 7, 1, 1, 0, '2026-08-10 22:40:00', '2026-08-10 22:40:00', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品查询', 18, 3, 'business:product:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品新增', 18, 3, 'business:product:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品修改', 18, 3, 'business:product:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品删除', 18, 3, 'business:product:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '促销查询', 19, 3, 'business:promotion:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '促销新增', 19, 3, 'business:promotion:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '促销修改', 19, 3, 'business:promotion:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '促销删除', 19, 3, 'business:promotion:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '评价查询', 20, 3, 'business:review:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '评价回复', 20, 3, 'business:review:reply', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '评价删除', 20, 3, 'business:review:remove', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '评价审核', 20, 3, 'business:review:audit', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-06 09:08:05', '2026-08-06 09:08:05', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '评价删除', 20, 0, 'business:review:delete', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-06 09:08:05', '2026-08-06 09:08:05', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品下架', 18, 3, 'business:product:offShelf', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-06 01:05:47', '2026-08-06 01:05:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品上架', 18, 3, 'business:product:onShelf', NULL, NULL, NULL, 6, 1, 1, 0, '2026-08-06 01:05:47', '2026-08-06 01:05:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '商品调价', 18, 3, 'business:product:priceAdjust', NULL, NULL, NULL, 7, 1, 1, 0, '2026-08-06 01:05:47', '2026-08-06 01:05:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员查询', 16, 0, 'business:member:query', NULL, NULL, NULL, 5, 1, 1, 1, '2026-08-06 07:54:03', '2026-08-06 07:54:03', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员新增', 16, 0, 'business:member:add', NULL, NULL, NULL, 6, 1, 1, 1, '2026-08-06 07:54:03', '2026-08-06 07:54:03', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员修改', 16, 0, 'business:member:edit', NULL, NULL, NULL, 7, 1, 1, 1, '2026-08-06 07:54:03', '2026-08-06 07:54:03', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '文档查询', 31, 3, 'kb:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '文档新增', 31, 3, 'kb:manage', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '文档发布', 31, 3, 'kb:publish', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '文档删除', 31, 3, 'kb:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '索引重建', 31, 3, 'kb:rebuild', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '同义词查询', 32, 3, 'kb:synonym:query', NULL, NULL, NULL, 1, 1, 1, 1, '2026-08-01 19:47:23', '2026-08-07 08:44:55', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '同义词新增', 32, 3, 'kb:synonym:manage', NULL, NULL, NULL, 2, 1, 1, 1, '2026-08-01 19:47:23', '2026-08-07 08:44:55', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '同义词删除', 32, 3, 'kb:synonym:remove', NULL, NULL, NULL, 3, 1, 1, 1, '2026-08-01 19:47:23', '2026-08-07 08:44:55', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '日志查询', 6, 3, 'system:operlog:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '日志清空', 6, 3, 'system:operlog:remove', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '配置查询', 7, 3, 'system:config:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '配置新增', 7, 3, 'system:config:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '配置修改', 7, 3, 'system:config:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '配置删除', 7, 3, 'system:config:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '字典类型查询', 8, 3, 'system:dict:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '字典类型新增', 8, 3, 'system:dict:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '字典类型修改', 8, 3, 'system:dict:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '字典类型删除', 8, 3, 'system:dict:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '字典数据新增', 8, 3, 'system:dict:data:add', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '字典数据修改', 8, 3, 'system:dict:data:edit', NULL, NULL, NULL, 6, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '流程配置查询', 50, 3, 'system:flow:query', NULL, NULL, NULL, 1, 1, 1, 1, '2026-08-01 19:47:23', '2026-08-07 08:44:32', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '流程配置修改', 50, 3, 'system:flow:edit', NULL, NULL, NULL, 2, 1, 1, 1, '2026-08-01 19:47:23', '2026-08-07 08:44:32', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '分类查询', 21, 3, 'business:category:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '分类新增', 21, 3, 'business:category:add', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '分类修改', 21, 3, 'business:category:edit', NULL, NULL, NULL, 3, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '分类删除', 21, 3, 'business:category:remove', NULL, NULL, NULL, 4, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '会员管理', 10, 2, 'business:member:list', 'member', 'business/member/index', 'member', 6, 1, 1, 0, '2026-08-07 10:04:09', '2026-08-07 10:04:09', 'admin', 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '新增', 344, 3, 'business:member:add', '', '', '', 3, 1, 1, 0, '2026-08-07 10:13:13', '2026-08-07 10:13:13', 'admin', 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '删除', 344, 3, 'business:member:remove', '', '', '', 0, 1, 1, 0, '2026-08-07 10:14:47', '2026-08-07 10:14:47', 'admin', 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '编辑', 344, 3, 'business:member:edit', '', '', '', 0, 1, 1, 0, '2026-08-07 10:15:18', '2026-08-07 10:15:18', 'admin', 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '查询', 344, 3, 'business:member:query', '', '', '', 0, 1, 1, 0, '2026-08-07 10:15:41', '2026-08-07 10:15:41', 'admin', 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '对话查询', 41, 3, 'business:chat:query', NULL, NULL, NULL, 1, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '对话管理', 41, 3, 'business:chat:manage', NULL, NULL, NULL, 2, 1, 1, 0, '2026-08-01 19:47:23', '2026-08-03 12:12:47', NULL, NULL, NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '等级调整', 344, 3, 'business:member:levelAdjust', NULL, NULL, NULL, 5, 1, 1, 0, '2026-08-07 10:04:09', '2026-08-07 10:04:09', 'admin', 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '睡眠会员', 344, 3, 'business:member:sleeping', NULL, NULL, NULL, 6, 1, 1, 0, '2026-08-07 10:04:09', '2026-08-07 10:04:09', 'admin', 'admin', NULL, NULL);
INSERT INTO `sys_menu` VAlUES (NULL, '新增评价', 20, 3, 'business:review:add', NULL, NULL, NULL, 10, 1, 1, 0, '2026-08-06 09:08:05', '2026-08-06 09:08:05', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID（平台管理员操作为NULL）',
  `title` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模块标题，如\"库存调整\"',
  `business_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'OTHER/INSERT/UPDATE/DELETE/EXPORT/IMPORT',
  `method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '方法全名（类.方法）',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'HTTP方法 GET/POST/PUT/DELETE',
  `request_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '请求URL',
  `request_param` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '请求参数（JSON，脱敏后）',
  `response_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '返回结果（JSON，截断超长）',
  `oper_user_id` bigint NULL DEFAULT NULL COMMENT '操作人ID',
  `oper_user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作人姓名',
  `oper_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作IP',
  `oper_location` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '操作位置（IP解析）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '0异常 1正常',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '异常信息（status=0时）',
  `cost_time` bigint NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_time`(`tenant_id` ASC, `oper_time` ASC) USING BTREE,
  INDEX `idx_tenant_user`(`tenant_id` ASC, `oper_user_id` ASC) USING BTREE,
  INDEX `idx_tenant_biz`(`tenant_id` ASC, `business_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作日志表（物理删除，仅追加）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID，NULL=平台内置角色',
  `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `role_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限标识，如 admin/tenant_admin',
  `role_sort` int NOT NULL DEFAULT 0,
  `data_scope` tinyint NOT NULL DEFAULT 1 COMMENT '1全部 5仅本人（预留）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_role_key`(`tenant_id` ASC, `role_key` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VAlUES (NULL, NULL, '超级管理员', 'admin', 1, 1, 1, '平台内置超管，拥有全部权限', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '租户管理员', 'tenant_admin', 1, 1, 1, '租户管理员（全租户数据）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '门店店长', 'store_manager', 2, 2, 1, '门店店长（本店数据）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '门店店员', 'store_staff', 3, 5, 1, '门店店员（仅本人数据）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '运营专员', 'operation', 4, 1, 1, '运营专员（营销域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '采购专员', 'purchaser', 5, 1, 1, '采购专员（商品域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '财务专员', 'finance', 6, 1, 1, '财务专员（报表域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '客服专员', 'customer_service', 7, 1, 1, '客服专员（售后域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '知识库维护', 'knowledge_admin', 8, 1, 1, '知识库维护（知识库域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '仓库管理员', 'warehouse', 9, 2, 1, '仓库管理员（空壳，暂无独立模块）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1001, '供应商管理员', 'supplier', 10, 1, 1, '供应商管理员（空壳，暂无独立模块）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '租户管理员', 'tenant_admin', 1, 1, 1, '租户管理员（全租户数据）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '门店店长', 'store_manager', 2, 2, 1, '门店店长（本店数据）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '门店店员', 'store_staff', 3, 5, 1, '门店店员（仅本人数据）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '运营专员', 'operation', 4, 1, 1, '运营专员（营销域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '采购专员', 'purchaser', 5, 1, 1, '采购专员（商品域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '财务专员', 'finance', 6, 1, 1, '财务专员（报表域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '客服专员', 'customer_service', 7, 1, 1, '客服专员（售后域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '知识库维护', 'knowledge_admin', 8, 1, 1, '知识库维护（知识库域）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '仓库管理员', 'warehouse', 9, 2, 1, '仓库管理员（空壳，暂无独立模块）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_role` VAlUES (NULL, 1002, '供应商管理员', 'supplier', 10, 1, 1, '供应商管理员（空壳，暂无独立模块）', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_menu`(`role_id` ASC, `menu_id` ASC) USING BTREE,
  INDEX `idx_menu`(`menu_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-菜单关系' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 1);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 2);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 3);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 4);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 5);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 6);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 7);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 8);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 9);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 50);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 100);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 101);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 102);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 103);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 104);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 105);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 110);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 111);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 112);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 113);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 114);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 120);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 121);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 122);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 123);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 130);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 131);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 132);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 133);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 140);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 141);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 142);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 143);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 201);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 202);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 203);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 204);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 221);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 222);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 231);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 232);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 233);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 241);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 242);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 243);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 244);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 251);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 253);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 264);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 265);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 354);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 271);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 272);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 273);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 275);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 276);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 277);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 280);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 283);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 284);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 285);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 291);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 292);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 293);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 294);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 296);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 297);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 300);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 301);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 310);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 311);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 312);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 313);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 320);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 321);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 322);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 323);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 324);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 325);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 330);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 331);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 341);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 342);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1, 343);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 1);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 2);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 3);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 5);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 100);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 101);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 102);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 103);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 104);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 105);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 110);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 111);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 112);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 113);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 114);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 130);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 131);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 132);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 133);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 201);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 202);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 203);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 204);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 212);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 221);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 222);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 231);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 232);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 233);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 241);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 242);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 243);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 244);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 251);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 253);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 254);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 264);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 265);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 354);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 271);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 272);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 273);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 275);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 276);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 277);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 280);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 281);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 282);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 283);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 284);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 285);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 291);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 292);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 293);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 294);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 341);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 342);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 343);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 344);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 345);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 346);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 347);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 348);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 349);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 350);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 351);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 352);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1001, 353);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 201);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 202);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 221);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 222);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 281);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 282);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 283);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 284);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 285);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 344);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 345);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 347);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 348);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 349);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 350);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 351);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 352);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1002, 353);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 201);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 286);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 344);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1003, 348);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 241);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 242);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 243);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 244);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 251);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 253);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 264);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 275);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 276);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 277);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 280);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 281);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 282);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 286);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 287);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 288);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1004, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 221);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 222);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 231);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 232);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 233);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 271);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 272);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 273);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 275);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 276);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 277);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 341);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 342);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1005, 343);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 264);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 265);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 354);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1006, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1007, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 291);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 292);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 293);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 294);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 296);
INSERT INTO `sys_role_menu` VAlUES (NULL, 1008, 297);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 1);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 2);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 3);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 5);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 50);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 100);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 101);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 102);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 103);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 104);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 105);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 110);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 111);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 112);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 113);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 114);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 130);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 131);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 132);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 133);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 201);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 202);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 203);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 204);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 212);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 221);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 222);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 231);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 232);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 233);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 241);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 242);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 243);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 244);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 251);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 253);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 254);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 264);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 265);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 354);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 271);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 272);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 273);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 275);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 276);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 277);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 280);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 281);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 282);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 283);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 284);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 285);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 286);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 287);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 288);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 291);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 292);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 293);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 294);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 296);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 297);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 330);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 331);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 341);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 342);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2001, 343);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 201);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 202);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 221);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 222);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 281);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 282);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 286);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 287);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 288);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2002, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 201);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 286);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2003, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 241);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 242);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 243);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 244);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 251);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 253);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 264);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 275);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 276);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 277);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 280);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 281);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 282);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 286);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 287);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 288);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2004, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 221);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 222);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 231);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 232);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 233);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 271);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 272);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 273);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 275);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 276);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 277);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 341);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 342);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2005, 343);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 17);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 260);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 261);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 262);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 263);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 264);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 265);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 354);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2006, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 10);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 11);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 12);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 13);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 14);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 15);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 16);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 18);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 19);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 20);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 21);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 22);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 23);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 200);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 210);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 211);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 220);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 230);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 240);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 245);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 250);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 252);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 270);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 274);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 278);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 279);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2007, 340);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 30);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 31);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 32);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 40);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 41);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 290);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 291);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 292);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 293);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 294);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 295);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 296);
INSERT INTO `sys_role_menu` VAlUES (NULL, 2008, 297);

-- ----------------------------
-- Table structure for sys_store
-- ----------------------------
DROP TABLE IF EXISTS `sys_store`;
CREATE TABLE `sys_store`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `store_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '门店地址',
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `business_hours` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '营业时间，如 09:00-22:00',
  `manager_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '店长姓名（冗余便于展示）',
  `manager_id` bigint NULL DEFAULT NULL COMMENT '店长用户ID，关联sys_user.id',
  `longitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10, 7) NULL DEFAULT NULL COMMENT '纬度',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_store_code`(`tenant_id` ASC, `store_code` ASC) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_manager`(`manager_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '门店表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_store
-- ----------------------------
INSERT INTO `sys_store` VAlUES (NULL, 1001, '城西店', 'TEST001', '杭州市西湖区文三路100号', '0571-88001001', '08:00-22:00', '城西店员', 1002, NULL, NULL, NULL, 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `sys_store` VAlUES (NULL, 1001, '滨江店', 'TEST002', '杭州市滨江区江南大道200号', '0571-88001002', '08:30-21:30', '城西店员', 1002, NULL, NULL, NULL, 1, 0, '2026-07-30 01:34:54', '2026-07-30 01:34:54', NULL, NULL, NULL, NULL);
INSERT INTO `sys_store` VAlUES (NULL, 1002, '浦东店', 'TEST003', '上海市浦东新区张杨路300号', '021-58001003', '09:00-21:00', '浦东店员', 2002, NULL, NULL, NULL, 1, 0, '2026-07-30 01:34:54', '2026-08-04 07:33:36', NULL, NULL, NULL, NULL);
INSERT INTO `sys_store` VAlUES (NULL, 1001, '城北店', 'TEST004', '杭州市拱墅区莫干山路400号', '0571-88001004', '08:00-22:00', '城北店员', 1011, NULL, NULL, NULL, 1, 0, '2026-07-30 01:34:54', '2026-08-04 07:33:36', NULL, NULL, NULL, NULL);
INSERT INTO `sys_store` VAlUES (NULL, 1001, '萧山店', 'TEST005', '杭州市萧山区市心路500号', '0571-88001005', '08:30-21:30', '萧山店员', 1012, NULL, NULL, NULL, 1, 0, '2026-07-30 01:34:54', '2026-08-04 07:33:36', NULL, NULL, NULL, NULL);
INSERT INTO `sys_store` VAlUES (NULL, 1002, '徐汇店', 'TEST006', '上海市徐汇区漕溪北路600号', '021-58001006', '09:00-21:00', '徐汇店员', 2011, NULL, NULL, NULL, 1, 0, '2026-07-30 01:34:54', '2026-08-04 07:33:36', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NULL DEFAULT NULL COMMENT '租户ID，NULL=平台管理员',
  `store_id` bigint NULL DEFAULT NULL COMMENT '所属门店ID，NULL=无固定门店',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录用户名，全局唯一',
  `password_hash` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'bcrypt哈希',
  `nick_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `gender` tinyint NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `last_login_at` datetime NULL DEFAULT NULL,
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  INDEX `idx_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_store`(`store_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VAlUES (NULL, NULL, NULL, 'admin', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '平台管理员', NULL, NULL, 0, 1, '2026-08-07 09:54:00', '平台内置超管', 0, '2026-07-30 01:34:53', '2026-07-30 01:34:53', NULL, 'admin', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, NULL, 'test1001_admin', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛管理员', NULL, NULL, 1, 1, '2026-08-07 10:18:18', '租户管理员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_admin', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, 1001, 'test1001_manager', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '城西店长', NULL, NULL, 1, 1, '2026-08-04 11:36:21', '门店店长-城西店', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_manager', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, 1001, 'test1001_staff', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '城西店员', NULL, NULL, 2, 1, '2026-08-06 08:19:34', '门店店员-城西店', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_staff', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, NULL, 'test1001_operation', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛运营', NULL, NULL, 1, 1, '2026-08-04 07:48:40', '运营专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_operation', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, NULL, 'test1001_purchaser', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛采购', NULL, NULL, 1, 1, '2026-08-04 07:48:40', '采购专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_purchaser', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, NULL, 'test1001_finance', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛财务', NULL, NULL, 1, 1, '2026-08-04 07:48:41', '财务专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_finance', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, NULL, 'test1001_service', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛客服', NULL, NULL, 1, 1, '2026-08-04 07:48:41', '客服专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_service', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, NULL, 'test1001_kb', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛知识库', NULL, NULL, 1, 1, '2026-08-04 07:48:41', '知识库维护', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_kb', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, 1001, 'test1001_warehouse', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛仓库', NULL, NULL, 1, 1, '2026-08-04 07:48:42', '仓库管理员-空壳', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_warehouse', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, NULL, 'test1001_supplier', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '鼎盛供应商', NULL, NULL, 1, 1, '2026-08-04 07:48:42', '供应商管理员-空壳', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1001_supplier', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, 1004, 'test1001_clerk2', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '城北店员', NULL, NULL, 2, 1, NULL, '门店店员-城北店', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1001, 1005, 'test1001_clerk3', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '萧山店员', NULL, NULL, 1, 1, NULL, '门店店员-萧山店', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, NULL, 'test1002_admin', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰管理员', NULL, NULL, 1, 1, '2026-08-05 02:35:13', '租户管理员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, 'test1002_admin', NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, 1003, 'test1002_manager', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '浦东店长', NULL, NULL, 1, 1, NULL, '门店店长-浦东店', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, 1003, 'test1002_staff', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '浦东店员', NULL, NULL, 2, 1, NULL, '门店店员-浦东店', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, NULL, 'test1002_operation', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰运营', NULL, NULL, 1, 1, NULL, '运营专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, NULL, 'test1002_purchaser', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰采购', NULL, NULL, 1, 1, NULL, '采购专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, NULL, 'test1002_finance', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰财务', NULL, NULL, 1, 1, NULL, '财务专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, NULL, 'test1002_service', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰客服', NULL, NULL, 1, 1, NULL, '客服专员', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, NULL, 'test1002_kb', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰知识库', NULL, NULL, 1, 1, NULL, '知识库维护', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, 1003, 'test1002_warehouse', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰仓库', NULL, NULL, 1, 1, NULL, '仓库管理员-空壳', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, NULL, 'test1002_supplier', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '星辰供应商', NULL, NULL, 1, 1, NULL, '供应商管理员-空壳', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);
INSERT INTO `sys_user` VAlUES (NULL, 1002, 1006, 'test1002_clerk2', '$2a$10$bhHrhDyXtlF7qRNxX4hFSuG4emzM6aIx9svpAx5ZOGHqAr0/awE7S', '徐汇店员', NULL, NULL, 2, 1, NULL, '门店店员-徐汇店', 0, '2026-08-04 07:40:26', '2026-08-04 07:40:26', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_role`(`user_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_role`(`role_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色关系' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VAlUES (NULL, 1, 1);
INSERT INTO `sys_user_role` VAlUES (NULL, 1001, 1001);
INSERT INTO `sys_user_role` VAlUES (NULL, 1002, 1002);
INSERT INTO `sys_user_role` VAlUES (NULL, 1003, 1003);
INSERT INTO `sys_user_role` VAlUES (NULL, 1004, 1004);
INSERT INTO `sys_user_role` VAlUES (NULL, 1005, 1005);
INSERT INTO `sys_user_role` VAlUES (NULL, 1006, 1006);
INSERT INTO `sys_user_role` VAlUES (NULL, 1007, 1007);
INSERT INTO `sys_user_role` VAlUES (NULL, 1008, 1008);
INSERT INTO `sys_user_role` VAlUES (NULL, 1009, 1009);
INSERT INTO `sys_user_role` VAlUES (NULL, 1010, 1010);
INSERT INTO `sys_user_role` VAlUES (NULL, 1011, 1003);
INSERT INTO `sys_user_role` VAlUES (NULL, 1012, 1003);
INSERT INTO `sys_user_role` VAlUES (NULL, 2001, 2001);
INSERT INTO `sys_user_role` VAlUES (NULL, 2002, 2002);
INSERT INTO `sys_user_role` VAlUES (NULL, 2003, 2003);
INSERT INTO `sys_user_role` VAlUES (NULL, 2004, 2004);
INSERT INTO `sys_user_role` VAlUES (NULL, 2005, 2005);
INSERT INTO `sys_user_role` VAlUES (NULL, 2006, 2006);
INSERT INTO `sys_user_role` VAlUES (NULL, 2007, 2007);
INSERT INTO `sys_user_role` VAlUES (NULL, 2008, 2008);
INSERT INTO `sys_user_role` VAlUES (NULL, 2009, 2009);
INSERT INTO `sys_user_role` VAlUES (NULL, 2010, 2010);
INSERT INTO `sys_user_role` VAlUES (NULL, 2011, 2003);

-- ----------------------------
-- Table structure for user_coupon
-- ----------------------------
DROP TABLE IF EXISTS `user_coupon`;
CREATE TABLE `user_coupon`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `store_id` bigint NULL DEFAULT NULL COMMENT '领取门店',
  `coupon_id` bigint NOT NULL COMMENT '模板ID',
  `coupon_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '冗余',
  `coupon_type` tinyint NOT NULL COMMENT '冗余',
  `member_id` bigint NOT NULL,
  `status` tinyint NOT NULL COMMENT 'unused未使用/used已使用/expired已过期/refunded已退',
  `order_id` bigint NULL DEFAULT NULL COMMENT '核销订单ID',
  `order_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '冗余',
  `face_value` decimal(15, 2) NOT NULL COMMENT '冗余面额',
  `threshold` decimal(15, 2) NOT NULL COMMENT '冗余门槛',
  `receive_time` datetime NOT NULL COMMENT '领取时间',
  `used_time` datetime NULL DEFAULT NULL COMMENT '使用时间',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人（自动填充）',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人（自动填充）',
  `delete_at` datetime NULL DEFAULT NULL COMMENT '删除时间（逻辑删除填充）',
  `delete_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '删除人（逻辑删除填充）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_member_status`(`tenant_id` ASC, `member_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_tenant_coupon`(`tenant_id` ASC, `coupon_id` ASC) USING BTREE,
  INDEX `idx_tenant_store`(`tenant_id` ASC, `store_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户优惠券表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_coupon
-- ----------------------------
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3003, '全场8.8折券', 2, 3018, 3, NULL, NULL, 8.80, 0.00, '2026-07-24 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:18:29', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3004, '10元代金券券', 3, 3001, 1, NULL, NULL, 10.00, 0.00, '2026-07-03 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3008, '10元代金券券', 3, 3020, 1, NULL, NULL, 10.00, 0.00, '2026-07-06 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3003, '全场8.8折券', 2, 3009, 2, 3009, '1001SO0010', 8.80, 0.00, '2026-07-14 00:00:00', '2026-07-20 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:21:05', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3008, '10元代金券券', 3, 3031, 3, NULL, NULL, 10.00, 0.00, '2026-07-10 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:18:29', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3003, '全场8.8折券', 2, 3003, 1, NULL, NULL, 8.80, 0.00, '2026-07-25 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3002, '满100减15券', 1, 3006, 1, NULL, NULL, 15.00, 100.00, '2026-07-13 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3002, '满100减15券', 1, 3007, 1, NULL, NULL, 15.00, 100.00, '2026-07-18 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3003, '全场8.8折券', 2, 3006, 2, 3015, '1001SO0013', 8.80, 0.00, '2026-07-08 00:00:00', '2026-07-15 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:21:05', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3001, '满30减20券', 1, 3017, 2, 3043, '1001SO0033', 20.00, 30.00, '2026-07-28 00:00:00', '2026-07-17 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:21:05', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3006, '满100减15券', 1, 3030, 1, NULL, NULL, 15.00, 100.00, '2026-07-13 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3003, '全场8.8折券', 2, 3017, 2, 3043, '1001SO0033', 8.80, 0.00, '2026-07-21 00:00:00', '2026-07-27 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:21:05', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3006, '满100减15券', 1, 3035, 1, NULL, NULL, 15.00, 100.00, '2026-07-01 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3004, '10元代金券券', 3, 3005, 1, NULL, NULL, 10.00, 0.00, '2026-07-06 00:00:00', '2026-07-18 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3003, '全场8.8折券', 2, 3006, 3, NULL, NULL, 8.80, 0.00, '2026-07-28 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:18:29', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1001, NULL, 3001, '满30减20券', 1, 3013, 3, NULL, NULL, 20.00, 30.00, '2026-07-16 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:18:29', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3005, '满30减20券', 1, 3028, 1, NULL, NULL, 20.00, 30.00, '2026-07-20 00:00:00', '2026-07-12 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3005, '满30减20券', 1, 3028, 1, NULL, NULL, 20.00, 30.00, '2026-07-27 00:00:00', '2026-07-20 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3007, '全场8.8折券', 2, 3022, 1, NULL, NULL, 8.80, 0.00, '2026-07-28 00:00:00', '2026-07-22 00:00:00', '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);
INSERT INTO `user_coupon` VAlUES (NULL, 1002, NULL, 3005, '满30减20券', 1, 3022, 1, NULL, NULL, 20.00, 30.00, '2026-07-10 00:00:00', NULL, '2026-08-31 23:59:59', 0, '2026-07-30 00:00:00', '2026-08-03 13:17:54', NULL, NULL, NULL, NULL);

-- ----------------------------
-- Table structure for user_quick_query
-- ----------------------------
DROP TABLE IF EXISTS `user_quick_query`;
CREATE TABLE `user_quick_query`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '代理主键',
  `tenant_id` bigint NOT NULL COMMENT '租户ID (拦截器自动注入)',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID: 个人快捷提问填; is_public=1 时 NULL',
  `is_public` tinyint(1) NOT NULL DEFAULT 0 COMMENT '1=租户级公共 (管理员设) / 0=个人',
  `shortcut_text` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '快捷提问文本 (用户常用问法)',
  `canonical_query` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规范化 query (缓存 key)',
  `scenario` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '业务场景 (与 paradigm_router scenario 对齐)',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人 (自动填充)',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人 (自动填充)',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_user`(`tenant_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_tenant_public`(`tenant_id` ASC, `is_public` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户快捷提问表 (canonical_query 一鱼三吃)' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_quick_query
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
