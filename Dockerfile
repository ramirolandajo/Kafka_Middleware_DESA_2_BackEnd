# Build stage
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
# copy entire context (avoids missing .mvn/.mvnw issues)
COPY . .

# Build the application (skip tests to speed up)
RUN mvn -B -DskipTests package

# Runtime stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy jar from build stage
COPY --from=build /workspace/target/kafka-middleware-desa-backend-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8090

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
