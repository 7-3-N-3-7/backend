import re

with open('src/main/java/com/platform/config/SecurityConfig.java', 'r') as f:
    content = f.read()

content = content.replace('.requestMatchers("/api/v1/public/**", "/api/v1/i18n/**", "/actuator/health").permitAll()',
                          '.requestMatchers("/api/v1/public/**", "/actuator/health").permitAll()\\n                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/i18n/**").permitAll()')

with open('src/main/java/com/platform/config/SecurityConfig.java', 'w') as f:
    f.write(content)
