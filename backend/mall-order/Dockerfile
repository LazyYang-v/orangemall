FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/mall-order.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 9005
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS --add-opens java.base/java.lang=ALL-UNNAMED -Duser.timezone=Asia/Shanghai -jar app.jar"]
