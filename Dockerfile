# Stage 1: Build and test the application (generates JaCoCo & Cucumber HTML reports)
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mkdir -p /root/.m2 && echo '<?xml version="1.0" encoding="UTF-8"?><toolchains><toolchain><type>jdk</type><provides><version>17</version></provides><configuration><jdkHome>/opt/java/openjdk</jdkHome></configuration></toolchain></toolchains>' > /root/.m2/toolchains.xml
RUN mvn clean package jacoco:report jacoco:check && \
    mkdir -p /app/reports/backend-coverage /app/reports/backend-cucumber && \
    cp -r /app/target/site/jacoco/* /app/reports/backend-coverage/ && \
    cp /app/target/cucumber-reports/cucumber-report.html /app/reports/backend-cucumber/index.html && \
    cp /app/target/cucumber-reports/cucumber-report.html /app/reports/backend-cucumber/cucumber-report.html

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/reports /app/reports
COPY entrypoint-secrets.sh /entrypoint-secrets.sh
RUN chmod +x /entrypoint-secrets.sh
EXPOSE 8081
ENTRYPOINT ["/entrypoint-secrets.sh", "java", "-jar", "app.jar"]

