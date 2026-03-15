# 멀티 스테이지 빌드 - smwr-api 모듈
# Stage 1: Maven 빌드
FROM maven:3.8-eclipse-temurin-8 AS build

WORKDIR /app

# 부모 POM 및 모듈 POM 복사
COPY pom.xml .
COPY smwr-common/pom.xml smwr-common/
COPY smwr-monster/pom.xml smwr-monster/
COPY smwr-admin/pom.xml smwr-admin/
COPY smwr-api/pom.xml smwr-api/

# 의존성만 먼저 다운로드 (캐시 활용)
RUN mvn dependency:go-offline -B -pl smwr-api -am || true

# 소스 코드 복사
COPY smwr-common/src smwr-common/src
COPY smwr-monster/src smwr-monster/src
COPY smwr-api/src smwr-api/src

# smwr-api 빌드
RUN mvn clean package -DskipTests -B -pl smwr-api -am

# 빌드된 JAR 복사
RUN cp /app/smwr-api/target/smwr-api-*.jar /app/target/app.jar

# Stage 2: 실행 환경
FROM eclipse-temurin:8-jre

WORKDIR /app

COPY --from=build /app/target/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
