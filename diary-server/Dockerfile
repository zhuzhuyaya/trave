# =============================================================================
#  diary-server · Spring Boot 后端镜像
#
#  两阶段构建:maven 镜像只用来产出 jar,最终镜像里不带 maven 与源码。
#  image 大小从 ~800MB(带 maven)降到 ~250MB(jre)。
# =============================================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom.xml 与 settings.xml 预热依赖。
# 这一层只认这两份文件的内容:改 Java 源码不会让它失效,重建时省掉整轮依赖下载。
# settings.xml 把中央仓指向阿里云 —— 容器内直连 repo.maven.apache.org 在国内是几十 KB/s。
COPY pom.xml settings.xml ./
RUN mvn -B -q -s settings.xml dependency:go-offline

COPY src ./src
RUN mvn -B -s settings.xml -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre

# 不用 root 跑。上传目录是应用唯一需要写盘的地方,
# 因此只需要把 /data 交给这个用户,jar 与源码保持只读。
RUN useradd --system --create-home --uid 10001 diary \
    && mkdir -p /data/uploads \
    && chown -R diary:diary /data

WORKDIR /app
COPY --from=build /build/target/diary-server-*.jar app.jar

# UPLOAD_DIR 显式写成绝对路径:
# yml 里的默认值是 ./uploads(相对 JVM 工作目录),容器里工作目录一旦变化,
# 图片会静默落到别处或 404。这里把它钉死在挂载点上。
ENV TZ=Asia/Shanghai \
    UPLOAD_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"

USER diary
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
