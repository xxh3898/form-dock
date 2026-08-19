package com.formdock.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CreatorBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CreatorBootstrapRunner.class);

    private final CreatorBootstrapProperties properties;
    private final CreatorBootstrapProvisioner provisioner;

    public CreatorBootstrapRunner(
            CreatorBootstrapProperties properties,
            CreatorBootstrapProvisioner provisioner) {
        this.properties = properties;
        this.provisioner = provisioner;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        CreatorBootstrapResult result = provisioner.provision(properties);
        switch (result) {
            case DISABLED -> log.info("Initial creator bootstrap is disabled");
            case CREATED -> log.info("Initial creator bootstrap created the creator account");
            case ALREADY_PROVISIONED ->
                    log.info("Initial creator bootstrap found the creator already provisioned");
        }
    }
}
