package com.tradingx.config;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class H2ConsoleConfig {

    @Bean
    public ServletRegistrationBean<?> h2ConsoleServlet() throws ClassNotFoundException {
        Class<?> servletClass = Class.forName("org.h2.server.web.JakartaWebServlet");
        Object servlet;
        try {
            servlet = servletClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate H2 Console servlet", e);
        }
        ServletRegistrationBean<?> registration = new ServletRegistrationBean<>(
                (jakarta.servlet.Servlet) servlet, "/h2-console/*");
        registration.addInitParameter("webAllowOthers", "true");
        return registration;
    }
}
