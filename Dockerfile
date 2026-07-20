# --- build ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src ./src
RUN ./mvnw -q clean package -DskipTests

# --- runtime ---
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*-LOCAL.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
