# saas-admin-api 컨테이너 이미지.
#
# 배포 대상은 EC2 3.34.183.170 (Amazon Linux 2, 1 vCPU / 952MB RAM) 이다.
# 같은 장비에서 Jenkins(9090) 와 food-biz-api(8080) 가 이미 돌고 있어서
# 메모리가 넉넉하지 않다. 아래 두 곳에서 힙을 묶어 두는 이유다.

# ── 빌드 단계 ──────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# pom.xml 만 먼저 넣고 의존성을 받는다. 소스만 바뀐 재빌드에서는
# 이 레이어가 캐시에 걸려 의존성을 다시 받지 않는다.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
# 빌드도 이 좁은 장비에서 돈다. Maven 기본값을 그대로 두면 힙을 크게 잡아
# 스왑으로 밀려 빌드가 몇 배로 느려진다.
ENV MAVEN_OPTS="-Xmx384m"
RUN mvn package -DskipTests -B

# ── 실행 단계 ──────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# application.yml 의 server.port 와 같은 값이다.
EXPOSE 8089

# -Xmx256m: 이 장비에 Spring Boot 두 개와 Jenkins 가 함께 올라간다.
#   제한을 걸지 않으면 JVM 이 물리 메모리의 1/4 을 기본 최대힙으로 잡고
#   서로 밀어내다 스왑을 타게 된다.
# 프로필은 지정하지 않는다. application-prod.yml 이 없어서 application.yml
#   하나로 뜨는 구성이고, 없는 프로필을 켜면 설정을 찾는 자리만 늘어난다.
ENTRYPOINT ["java", "-Xmx256m", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
