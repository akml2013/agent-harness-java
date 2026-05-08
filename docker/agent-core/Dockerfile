FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY agent-common/pom.xml agent-common/pom.xml
COPY agent-common/common-core/pom.xml agent-common/common-core/pom.xml
COPY agent-common/common-api/pom.xml agent-common/common-api/pom.xml
COPY agent-common/common-util/pom.xml agent-common/common-util/pom.xml
COPY agent-core/pom.xml agent-core/pom.xml
RUN mvn dependency:go-offline -B -q 2>/dev/null || true
COPY . .
RUN mvn package -DskipTests -q -pl agent-core -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/agent-core/target/agent-core-1.0.0-SNAPSHOT.jar app.jar
COPY skills/ /app/skills/
COPY docker/agent-core/application-docker.properties /app/config/application-docker.properties
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker", "--spring.config.additional-location=/app/config/"]
