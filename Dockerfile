# 멀티 스테이지 빌드
# Stage 1: Maven 빌드
FROM maven:3.8-openjdk-8-slim AS build

WORKDIR /app

# pom.xml과 소스 코드 복사
COPY pom.xml .
COPY src ./src

# Maven 빌드 실행 (의존성 다운로드 및 빌드)
RUN mvn clean package -DskipTests

# Stage 2: 실행 환경
FROM openjdk:8-jre-slim

WORKDIR /app

# 빌드된 JAR 파일 복사
# Spring Boot는 WAR로 패키징되어도 내장 Tomcat으로 실행 가능
COPY --from=build /app/target/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]

