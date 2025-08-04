package com.routepick.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI routePickOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RoutePick API")
                        .description("RoutePick 애플리케이션 API 문서")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RoutePick Team")
                                .email("support@routepick.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
} 