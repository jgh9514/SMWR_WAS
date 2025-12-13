# 멀티 스테이지 빌드
# Stage 1: Maven 빌드
FROM maven:3.8-eclipse-temurin-8 AS build

WORKDIR /app

# pom.xml과 소스 코드 복사
COPY pom.xml .
COPY src ./src

# Maven 빌드 실행 (의존성 다운로드 및 빌드)
RUN mvn clean package -DskipTests

# 빌드된 실행 가능한 JAR/WAR 파일을 찾아서 app.jar로 복사
# 프로젝트가 WAR로 패키징되어 있으므로 WAR 파일도 확인
# Spring Boot는 WAR 파일도 java -jar로 실행 가능
RUN cd /app/target && \
    if [ -f smw-*.war ]; then \
        cp smw-*.war app.jar; \
    elif [ -f smw-*.jar ]; then \
        JAR_FILE=$(ls -t smw-*.jar 2>/dev/null | grep -v "sources\|javadoc" | head -1) && \
        cp "$JAR_FILE" app.jar; \
    else \
        echo "Error: No JAR or WAR file found in /app/target" && \
        ls -la /app/target && \
        exit 1; \
    fi

# Stage 2: 실행 환경
FROM eclipse-temurin:8-jre

WORKDIR /app

# 빌드 스테이지에서 준비된 app.jar 파일 복사
COPY --from=build /app/target/app.jar app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]

