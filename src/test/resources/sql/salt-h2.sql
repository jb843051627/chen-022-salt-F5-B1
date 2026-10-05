-- H2（MySQL 兼容模式）盐政测试库：列与 doc/schema/salt.sql 逐列对齐，
-- 仅去掉 ENGINE/CHARSET/COLLATE/KEY 等 MySQL 专写（H2 不认）。
-- 时区随测试进程钉在 GMT+8（见 SaltDbSupport），与生产墙钟串口径一致。

CREATE TABLE IF NOT EXISTS t_salt_ent (
  id BIGINT NOT NULL,
  site_no VARCHAR(64),
  site_name VARCHAR(128),
  site_type VARCHAR(32),
  road_name VARCHAR(128),
  contact_person VARCHAR(64),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_notify_script (
  id BIGINT NOT NULL,
  script_no VARCHAR(64),
  script_name VARCHAR(128),
  biz_type INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE (script_no)
);

CREATE TABLE IF NOT EXISTS t_salt_officer (
  id BIGINT NOT NULL,
  officer_no VARCHAR(64),
  officer_name VARCHAR(64),
  daily_limit INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE (officer_no)
);

CREATE TABLE IF NOT EXISTS t_salt_officer_scope (
  id BIGINT NOT NULL,
  officer_id BIGINT,
  biz_type INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_officer_off (
  id BIGINT NOT NULL,
  officer_id BIGINT,
  off_date DATE,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_calendar (
  id BIGINT NOT NULL,
  cal_date DATE,
  day_kind INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE (cal_date)
);

CREATE TABLE IF NOT EXISTS t_salt_iod_line (
  id BIGINT NOT NULL,
  rule_code VARCHAR(64),
  rule_name VARCHAR(128),
  th1_max DECIMAL(12,3),
  th2_max DECIMAL(12,3),
  th3_max DECIMAL(12,3),
  eff_start DATETIME,
  eff_end DATETIME,
  priority INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_link_page (
  id BIGINT NOT NULL,
  bill_no VARCHAR(64),
  node_no INT,
  site_id BIGINT,
  site_no VARCHAR(64),
  should_count INT,
  done_count INT,
  lack_count INT,
  content VARCHAR(255),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_record_flow (
  id BIGINT NOT NULL,
  biz_no VARCHAR(64),
  stage INT,
  status INT,
  content VARCHAR(255),
  last_action VARCHAR(64),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_renew_bill (
  id BIGINT NOT NULL,
  bill_no VARCHAR(64),
  node_no INT,
  sign_mode INT,
  need_count INT,
  sign_count INT,
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_spot_row (
  id BIGINT NOT NULL,
  batch_no VARCHAR(64),
  row_no INT,
  item_code VARCHAR(64),
  qty DECIMAL(12,2),
  hours_num DECIMAL(12,2),
  status INT,
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_salt_year_task (
  id BIGINT NOT NULL,
  item_no VARCHAR(64),
  site_id BIGINT,
  biz_type INT DEFAULT 0,
  due_at DATETIME,
  amount DECIMAL(12,2),
  script_no VARCHAR(64),
  content VARCHAR(255),
  status INT,
  officer_id BIGINT,
  dispatch_at DATETIME,
  fail_reason VARCHAR(500),
  del_flag INT DEFAULT 0,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (id),
  UNIQUE (site_id, biz_type, due_at)
);
