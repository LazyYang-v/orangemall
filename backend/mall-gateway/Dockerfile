FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/mall-gateway.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -jar app.jar"]
