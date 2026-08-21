package com.formdock.response;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublicResponseRateLimitProperties.class)
class PublicResponseConfiguration {
}
