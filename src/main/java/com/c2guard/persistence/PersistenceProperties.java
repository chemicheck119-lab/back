package com.c2guard.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chemicheck119.persistence")
public class PersistenceProperties {

    private boolean requireExternalDatabase;

    public boolean isRequireExternalDatabase() {
        return requireExternalDatabase;
    }

    public void setRequireExternalDatabase(boolean requireExternalDatabase) {
        this.requireExternalDatabase = requireExternalDatabase;
    }
}
