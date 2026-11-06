# OM-017：Seata TC db 存储模式需要 MySQL 驱动在 classpath 上。
# 官方镜像把驱动放在 /seata-server/libs/jdbc/（不在 jib classpath `libs/*` 通配范围内），
# 直接以 db 模式启动会报「Failed to load driver class com.mysql.cj.jdbc.Driver」。复制 8.x 驱动到 libs/ 即可。
FROM seataio/seata-server:2.0.0
RUN cp /seata-server/libs/jdbc/mysql-connector-java-8.0.27.jar /seata-server/libs/
