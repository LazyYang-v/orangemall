-- Seata TC 存储库（OM-017：store.mode=db，全局事务 / 分支 / 全局锁持久化在 MySQL，TC 重启或重建容器后可恢复在途事务）
-- 表结构取自 Seata 2.0.0 script/server/db/mysql.sql。首次初始化自动执行；存量环境可重复执行（IF NOT EXISTS / INSERT IGNORE）：
--   docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 < mysql/init/02-seata.sql
CREATE DATABASE IF NOT EXISTS seata DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seata;

CREATE TABLE IF NOT EXISTS global_table (
  xid                       VARCHAR(128) NOT NULL,
  transaction_id            BIGINT,
  status                    TINYINT      NOT NULL,
  application_id            VARCHAR(32),
  transaction_service_group VARCHAR(32),
  transaction_name          VARCHAR(128),
  timeout                   INT,
  begin_time                BIGINT,
  application_data          VARCHAR(2000),
  gmt_create                DATETIME,
  gmt_modified              DATETIME,
  PRIMARY KEY (xid),
  KEY idx_status_gmt_modified (status, gmt_modified),
  KEY idx_transaction_id (transaction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS branch_table (
  branch_id         BIGINT       NOT NULL,
  xid               VARCHAR(128) NOT NULL,
  transaction_id    BIGINT,
  resource_group_id VARCHAR(32),
  resource_id       VARCHAR(256),
  branch_type       VARCHAR(8),
  status            TINYINT,
  client_id         VARCHAR(64),
  application_data  VARCHAR(2000),
  gmt_create        DATETIME(6),
  gmt_modified      DATETIME(6),
  PRIMARY KEY (branch_id),
  KEY idx_xid (xid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS lock_table (
  row_key        VARCHAR(128) NOT NULL,
  xid            VARCHAR(128),
  transaction_id BIGINT,
  branch_id      BIGINT       NOT NULL,
  resource_id    VARCHAR(256),
  table_name     VARCHAR(32),
  pk             VARCHAR(36),
  status         TINYINT      NOT NULL DEFAULT '0' COMMENT '0:locked ,1:rollbacking',
  gmt_create     DATETIME,
  gmt_modified   DATETIME,
  PRIMARY KEY (row_key),
  KEY idx_status (status),
  KEY idx_branch_id (branch_id),
  KEY idx_xid (xid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS distributed_lock (
  lock_key   CHAR(20)    NOT NULL,
  lock_value VARCHAR(20) NOT NULL,
  expire     BIGINT,
  PRIMARY KEY (lock_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO distributed_lock (lock_key, lock_value, expire) VALUES
  ('AsyncCommitting', ' ', 0), ('RetryCommitting', ' ', 0), ('RetryRollbacking', ' ', 0),
  ('TxTimeoutCheck', ' ', 0), ('UndologDelete', ' ', 0);
