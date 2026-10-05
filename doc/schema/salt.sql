-- salt 食盐专营管理与食盐质量安全监管 -- schema (chen-022)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_022

CREATE TABLE IF NOT EXISTS t_salt_ent (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '食盐定点企业代号',
  site_name varchar(128) DEFAULT NULL COMMENT '企业全称',
  site_type varchar(32) DEFAULT NULL COMMENT '企业类别',
  road_name varchar(128) DEFAULT NULL COMMENT '属地那一路(省—市—县)',
  contact_person varchar(64) DEFAULT NULL COMMENT '联络人（空着几季没人补的，单子开不成）',
  status int DEFAULT NULL COMMENT '名录情形 0在册 1已摘牌',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='食盐定点企业总名录';

-- 催办话本：条目按 script_no 照本开口；话本停用/缺失，单子无本可依，按分不出挂起。
CREATE TABLE IF NOT EXISTS t_salt_notify_script (
  id bigint NOT NULL COMMENT '主键',
  script_no varchar(64) DEFAULT NULL COMMENT '话本代号',
  script_name varchar(128) DEFAULT NULL COMMENT '话本名目',
  biz_type int DEFAULT NULL COMMENT '所对事由 0年检催办 1换证提醒（空=两类皆可）',
  status int DEFAULT NULL COMMENT '话本情形 0在场 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_salt_notify_script_no (script_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='催办换证话本';

-- 执法员：daily_limit 为当日接活定额，接满即不再落条。
CREATE TABLE IF NOT EXISTS t_salt_officer (
  id bigint NOT NULL COMMENT '主键',
  officer_no varchar(64) DEFAULT NULL COMMENT '执法员代号',
  officer_name varchar(64) DEFAULT NULL COMMENT '执法员姓名',
  daily_limit int DEFAULT NULL COMMENT '当日接活定额',
  status int DEFAULT NULL COMMENT '在岗情形 0在岗 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_salt_officer_no (officer_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='盐政执法员名录';

-- 执法员接案能力：有 (执法员, 事由) 一行才算接得动这一类，没有即跳过看下一位。
CREATE TABLE IF NOT EXISTS t_salt_officer_scope (
  id bigint NOT NULL COMMENT '主键',
  officer_id bigint DEFAULT NULL COMMENT '执法员主键',
  biz_type int DEFAULT NULL COMMENT '接得动的事由 0年检催办 1换证提醒',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_salt_officer_scope_officer (officer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执法员接案能力档';

-- 执法员当日休办：撞上该日的，挑人时跳过他。
CREATE TABLE IF NOT EXISTS t_salt_officer_off (
  id bigint NOT NULL COMMENT '主键',
  officer_id bigint DEFAULT NULL COMMENT '执法员主键',
  off_date date DEFAULT NULL COMMENT '休办之日',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_salt_officer_off_officer_day (officer_id, off_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='执法员休办日';

-- 全省一把尺所依的省历：0国家法定假 1省通令不办公 2全省集中办案窗口（窗口日照常办公，只是年检单不许动）。
CREATE TABLE IF NOT EXISTS t_salt_calendar (
  id bigint NOT NULL COMMENT '主键',
  cal_date date DEFAULT NULL COMMENT '所注之日',
  day_kind int DEFAULT NULL COMMENT '日类 0国家假 1省通令不办公 2集中办案窗口',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_salt_calendar_day (cal_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全省办公日历（假日/省令/办案窗口）';

CREATE TABLE IF NOT EXISTS t_salt_iod_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '碘含量分档判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '起算数值(毫克每千克)',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '够线数值(毫克每千克)',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶数值(毫克每千克)',
  eff_start datetime DEFAULT NULL COMMENT '立线之日',
  eff_end datetime DEFAULT NULL COMMENT '让位之日(不含)',
  priority int DEFAULT NULL COMMENT '让位顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在场 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='碘含量分档判定线';

CREATE TABLE IF NOT EXISTS t_salt_link_page (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '企业品种挂接代号',
  node_no int DEFAULT NULL COMMENT '适用区域层级序',
  site_id int DEFAULT NULL COMMENT '所属定点企业',
  site_no varchar(64) DEFAULT NULL COMMENT '所属企业代号',
  should_count int DEFAULT NULL COMMENT '应挂品种数',
  done_count int DEFAULT NULL COMMENT '已挂品种数',
  lack_count int DEFAULT NULL COMMENT '欠挂品种数',
  content varchar(255) DEFAULT NULL COMMENT '随页交来的标签要件',
  status int DEFAULT NULL COMMENT '挂接进展 0待挂 1已挂讫 2压页',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业品种挂接簿页';

CREATE TABLE IF NOT EXISTS t_salt_record_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '跨省经营备案单代号',
  stage int DEFAULT NULL COMMENT '当前步次 0..3（递单受理/材料核验/生效确认/缴回封卷）',
  status int DEFAULT NULL COMMENT '备案单走到的那一步 0未起 1在办 2已封卷',
  content varchar(255) DEFAULT NULL COMMENT '一段一记',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪步动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨省经营备案单';

CREATE TABLE IF NOT EXISTS t_salt_renew_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '延续换证核签单',
  node_no int DEFAULT NULL COMMENT '当前所在档口 0..2（县初核/市复审/省核准）',
  sign_mode int DEFAULT NULL COMMENT '同档算齐办法 0一名即可 1两名须同判',
  need_count int DEFAULT NULL COMMENT '本档应落人数',
  sign_count int DEFAULT NULL COMMENT '本档已落印数',
  status int DEFAULT NULL COMMENT '核签情形 0在核 1已办结 2已挪回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='延续换证核签单';

CREATE TABLE IF NOT EXISTS t_salt_spot_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '监督抽检结果报送册码',
  row_no int DEFAULT NULL COMMENT '原报行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的定点企业代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行不合格件数',
  hours_num decimal(12,2) DEFAULT NULL COMMENT '本行送检件数',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽检结果报送核收行';

CREATE TABLE IF NOT EXISTS t_salt_year_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '年检催办条目代号',
  site_id bigint DEFAULT NULL COMMENT '拴着哪家企业(t_salt_ent.id)',
  biz_type int DEFAULT '0' COMMENT '事由 0年检催办 1换证提醒',
  due_at datetime DEFAULT NULL COMMENT '应开口那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '最可提前几日开口（按工作日数）',
  script_no varchar(64) DEFAULT NULL COMMENT '照哪一份话本去发(t_salt_notify_script.script_no)',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对企业的记要',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派出 2分不出',
  officer_id bigint DEFAULT NULL COMMENT '派给了哪位执法员(t_salt_officer.id)',
  dispatch_at datetime DEFAULT NULL COMMENT '派出那一刻',
  fail_reason varchar(500) DEFAULT NULL COMMENT '分不出原委（卡在哪一环，日后按原委挑得出一批）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_salt_year_task_status_due (status, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='年检到期催办单';

-- 初始档案数据（状态约定：id=1 启用 / id=2 停用）
-- t_salt_ent 预置两条种子名录：id=0 在册、id=1 已摘牌；企业代号与类别按各表既有排法给值。
-- id=0 有联络人，单开得出；id=1 已摘牌且无联络人，两类分不出原委都踩得上。
INSERT IGNORE INTO t_salt_ent (id, site_no, site_name, site_type, road_name, contact_person, status, del_flag, create_by, create_time)
VALUES (0, 'JY00', '盐泽制盐一厂（制盐企业，在册）', '制盐企业', '盐岭省—盐泽市—临卤县', '卤延年', 0, 0, 'seed', NOW()),
       (1, 'JY01', '青卤批发部（转行后摘牌）', '省内批发企业', '盐岭省—盐泽市—青卤县', NULL, 1, 0, 'seed', NOW());

-- 话本两本：年检催办、换证提醒，各一本在场。
INSERT IGNORE INTO t_salt_notify_script (id, script_no, script_name, biz_type, status, del_flag, create_by, create_time)
VALUES (0, 'NB-NJ', '食盐许可证年检催办话本', 0, 0, 0, 'seed', NOW()),
       (1, 'NB-HZ', '许可证到期换证提醒话本', 1, 0, 0, 'seed', NOW());

-- 执法员三名：甲乙丙；丙停岗，不参与挑人。
INSERT IGNORE INTO t_salt_officer (id, officer_no, officer_name, daily_limit, status, del_flag, create_by, create_time)
VALUES (0, 'ZF00', '严甲', 10, 0, 0, 'seed', NOW()),
       (1, 'ZF01', '严乙', 10, 0, 0, 'seed', NOW()),
       (2, 'ZF02', '严丙（停岗）', 10, 1, 0, 'seed', NOW());

-- 接案能力：甲乙两类皆接。
INSERT IGNORE INTO t_salt_officer_scope (id, officer_id, biz_type, del_flag, create_by, create_time)
VALUES (0, 0, 0, 0, 'seed', NOW()),
       (1, 0, 1, 0, 'seed', NOW()),
       (2, 1, 0, 0, 'seed', NOW()),
       (3, 1, 1, 0, 'seed', NOW());

