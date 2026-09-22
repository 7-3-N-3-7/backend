package com.journal.crm;

import org.h2.tools.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;

@Configuration
public class H2StandaloneConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public Server h2WebServer() throws SQLException {
        // Starts the H2 Console on a completely separate port (8082)
        // bypassing Tomcat and the Servlet API entirely!
        return Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082");
    }
}
