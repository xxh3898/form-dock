package com.formdock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.formdock.auth.CreatorBootstrapException;
import com.formdock.auth.CreatorBootstrapProperties;
import com.formdock.auth.CreatorBootstrapProvisioner;
import com.formdock.auth.CreatorBootstrapRunner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class CreatorBootstrapRunnerTest {

    @Test
    void should_propagateFailure_when_provisioningFailsDuringStartup() {
        CreatorBootstrapProperties properties = new CreatorBootstrapProperties(
                true,
                "creator@example.test",
                "test-only-local-passphrase",
                "Local Creator");
        CreatorBootstrapProvisioner provisioner = mock(CreatorBootstrapProvisioner.class);
        CreatorBootstrapException failure = new CreatorBootstrapException(
                "Creator bootstrap configuration is invalid");
        when(provisioner.provision(properties)).thenThrow(failure);
        CreatorBootstrapRunner runner = new CreatorBootstrapRunner(properties, provisioner);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isSameAs(failure);
    }
}
