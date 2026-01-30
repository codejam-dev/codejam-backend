FROM eclipse-temurin:21-jdk

# Install wget for healthcheck
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/codejam-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# JVM memory optimization for small instances
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
