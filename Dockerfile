# Stage 1: Build the Spring Boot app
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
COPY omni-merchant-common/pom.xml omni-merchant-common/
COPY omni-merchant-tenant/pom.xml omni-merchant-tenant/
COPY omni-merchant-agent/pom.xml omni-merchant-agent/
COPY omni-merchant-intent/pom.xml omni-merchant-intent/
COPY omni-merchant-knowledge/pom.xml omni-merchant-knowledge/
COPY omni-merchant-channel/pom.xml omni-merchant-channel/
COPY omni-merchant-message/pom.xml omni-merchant-message/
COPY omni-merchant-bootstrap/pom.xml omni-merchant-bootstrap/
RUN mvn dependency:go-offline -pl omni-merchant-bootstrap -am -B
COPY . .
RUN mvn package -DskipTests -pl omni-merchant-bootstrap -am

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/omni-merchant-bootstrap/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
