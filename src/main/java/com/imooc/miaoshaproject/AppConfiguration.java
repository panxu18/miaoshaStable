package com.imooc.miaoshaproject;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfiguration {

    /**
     * 嵌入Tomcat配置
     */
    @Bean
    public ConfigurableServletWebServerFactory webServerFactoryCustomizer() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.addConnectorCustomizers((connector) -> {
            Http11NioProtocol protocolHandler = (Http11NioProtocol) connector.getProtocolHandler();
            protocolHandler.setKeepAliveTimeout(30000);
            protocolHandler.setMaxKeepAliveRequests(1000);
        });
        return factory;
    }
}
