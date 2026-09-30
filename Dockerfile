# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mkdir -p /root/.m2 && echo '<?xml version="1.0" encoding="UTF8"?><toolchains><toolchain><type>jdk</type><provides><version>17</version></provides><configuration><jdkHome>/opt/java/openjdk</jdkHome></configuration></toolchain></toolchains>' > /root/.m2/toolchains.xml
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
