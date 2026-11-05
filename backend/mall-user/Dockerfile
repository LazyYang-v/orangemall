FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/mall-user.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 9001
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS --add-opens java.base/java.lang=ALL-UNNAMED -Duser.timezone=Asia/Shanghai -jar app.jar"]
