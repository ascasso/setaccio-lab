package com.setaccio.lab.config;

import com.setaccio.core.model.Blake3Implementation;
import com.setaccio.core.service.ApacheCommonsBlake3HashingServiceImpl;
import com.setaccio.core.service.Blake3HashingService;
import com.setaccio.core.service.BouncyCastleBlake3HashingServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class HashingConfig {

    @Value("${blake3.implementation:apache-commons-codec}")
    private String blake3ImplementationKey;

    @Bean("apacheCommonsBlake3HashingService")
    public Blake3HashingService apacheCommonsBlake3HashingService() {
        return new ApacheCommonsBlake3HashingServiceImpl();
    }

    @Bean("bouncyCastleBlake3HashingService")
    public Blake3HashingService bouncyCastleBlake3HashingService() {
        return new BouncyCastleBlake3HashingServiceImpl();
    }

    @Bean
    @Primary
    public Blake3HashingService blake3HashingService(
            @Qualifier("apacheCommonsBlake3HashingService") Blake3HashingService apacheCommonsService,
            @Qualifier("bouncyCastleBlake3HashingService") Blake3HashingService bouncyCastleService) {

        Blake3Implementation implementation = Blake3Implementation.fromKey(blake3ImplementationKey);
        return switch (implementation) {
            case APACHE_COMMONS_CODEC -> apacheCommonsService;
            case BOUNCY_CASTLE -> bouncyCastleService;
        };
    }
}
