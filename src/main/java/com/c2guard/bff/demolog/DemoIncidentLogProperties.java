package com.c2guard.bff.demolog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chemicheck119.demo-logs")
public class DemoIncidentLogProperties {

    private boolean enabled;
    private int recordsPerStation = 15;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRecordsPerStation() {
        return recordsPerStation;
    }

    public void setRecordsPerStation(int recordsPerStation) {
        if (recordsPerStation < 1 || recordsPerStation > 15) {
            throw new IllegalArgumentException(
                    "records-per-station은 1 이상 15 이하여야 합니다.");
        }
        this.recordsPerStation = recordsPerStation;
    }
}
