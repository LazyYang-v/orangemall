# Nacos v2.3.2 官方镜像自带 Temurin JDK 8u372：在 cgroup v2 的 GitHub ubuntu runner 上，
# 读容器 CPU 指标时 jdk.internal.platform.cgroupv2.CgroupV2Subsystem.getInstance 抛 NPE，
# Spring Boot 的 ProcessorMetrics 初始化失败 → Nacos 启动即退出（-XX:-UseContainerSupport 对 JDK 8 的 Java 层无效）。
# 只替换同厂商、同目录布局的 8u504 JRE（该缺陷已修复），Nacos 本身与启动脚本不变。
FROM eclipse-temurin:8u504-b01-jre AS jre

FROM nacos/nacos-server:v2.3.2-slim
RUN rm -rf /opt/java/openjdk
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
