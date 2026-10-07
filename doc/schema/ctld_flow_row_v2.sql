-- ctld_flow_row 核收流水状态机补强迁移（chen-028）
-- 与基线 doc/schema/ctld.sql 配套：本脚本只做增量，已在库上执行一次即可，重复执行靠 IF NOT EXISTS / 元信息判断。
-- 口径：行去处、重到批次码怎么办、整拨成不成、拨头三个数，四问只由后端核收引擎这一处回答；
--       本脚本只把这一处的落库形状钉死，不另立尺子。

-- ============ 一、核收行增列 ============
-- 报文带入的镉实测数、底册带出的行面、待核标注、退回理由、原始报文行
ALTER TABLE t_ctld_flow_row
  ADD COLUMN cd_value decimal(10,3) DEFAULT NULL COMMENT '土壤镉实测数（报文带入，与当地那条告警线比对）' AFTER chk_num,
  ADD COLUMN plot_title varchar(128) DEFAULT NULL COMMENT '地块题名（一律以底册带出的那一份作准）' AFTER cd_value,
  ADD COLUMN zone_no varchar(64) DEFAULT NULL COMMENT '分区代号（一律以底册带出的那一份作准）' AFTER plot_title,
  ADD COLUMN flag_kind varchar(32) DEFAULT NULL COMMENT '待核标注 PARSE报文切不开/ENCODING编码混杂/INCOMPLETE两数未同到/NO_PLOT底册查无此码' AFTER zone_no,
  ADD COLUMN reject_reason varchar(255) DEFAULT NULL COMMENT '退回理由（为哪一条退，逐行追溯）' AFTER flag_kind,
  ADD COLUMN msg_raw varchar(500) DEFAULT NULL COMMENT '原始报文行（回核对照用，只用来比对不作行面）' AFTER reject_reason,
  ADD COLUMN mismatch int DEFAULT '0' COMMENT '题名或分区与底册比对不合 0合 1不合（不合的行回执单列一份）' AFTER msg_raw;

-- 行去处只有三种写法：0未勾 1收下 2退回；第四种写法落不了库
ALTER TABLE t_ctld_flow_row
  ADD CONSTRAINT chk_flow_row_status CHECK (status IN (0, 1, 2));

-- ============ 二、拨头流水（一拨一行，批次码唯一） ============
-- 拨一进门先把行数点出来，点出的数连同写定的上限一并存进本拨流水，日后翻得出来当初报了几行
CREATE TABLE IF NOT EXISTS t_ctld_flow_batch (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) NOT NULL COMMENT '协同检测流水批次码（同一批次码走两回只认头一拨）',
  limit_declared int DEFAULT NULL COMMENT '拨头抬头那一格写定的上限（原样留存，空/负/离谱都照实留）',
  limit_resolved int DEFAULT NULL COMMENT '认定上限（抬头空着、负数、大得离谱一律按 300 认）',
  parse_mode varchar(16) DEFAULT NULL COMMENT '本拨认的切法 DELIMITED按分隔符/FIXED_WIDTH按定宽，一拨之内认一种',
  put_kind varchar(32) DEFAULT NULL COMMENT '来路（县农技中心直报／镇级补报两条来路）',
  in_count int DEFAULT '0' COMMENT '几行进门（=收下+退回，与逐行去向同一次点算出来）',
  accept_count int DEFAULT '0' COMMENT '几行收下',
  reject_count int DEFAULT '0' COMMENT '几行退回',
  pending_count int DEFAULT '0' COMMENT '几行待核（未勾，单列一份待核清单，不进三栏）',
  overflow_from varchar(64) DEFAULT NULL COMMENT '由哪一拨到顶溢出另起（头一拨为 NULL）',
  receipt_msg varchar(255) DEFAULT NULL COMMENT '回执那句原话（空拨只落「空拨」两个字，不收讫那句不出）',
  status int DEFAULT NULL COMMENT '拨的情形 0已收讫 1空拨',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='土壤-农产品协同检测流水拨头（一拨一行）';

-- ============ 三、到门流水（同一批次码每到一回落一笔，只追加） ============
-- 镇级上报一回、县局复核一回，两回各留一份流水，并起来期数才对得平；
-- 第二回到门只照面：既不许把头一遍落定的行翻回未勾，也不许另立一拨
CREATE TABLE IF NOT EXISTS t_ctld_flow_arrive (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) NOT NULL COMMENT '到门的批次码',
  arrive_seq int NOT NULL COMMENT '第几回到门（本批次码内时序）',
  result_kind varchar(16) DEFAULT NULL COMMENT '这一回的下场 LANDED头一回落地/REPLAYED照面重递',
  note varchar(255) DEFAULT NULL COMMENT '这一回的记要',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（到门流水只追加，不置1）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flow_arrive (batch_no, arrive_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核收流水到门账（同一批次码每到一回落一笔，只追加）';
