package com.c2guard.bff.intake;

import com.c2guard.bff.confirmation.ConfirmationRole;

enum SyntheticReplaySubstance {
    INCIDENT(ConfirmationRole.INCIDENT, "7681-52-9", "차아염소산나트륨"),
    FACILITY(ConfirmationRole.FACILITY, "7647-01-0", "염산");

    private final ConfirmationRole role;
    private final String casNumber;
    private final String displayName;

    SyntheticReplaySubstance(ConfirmationRole role, String casNumber, String displayName) {
        this.role = role;
        this.casNumber = casNumber;
        this.displayName = displayName;
    }

    ConfirmationRole role() {
        return role;
    }

    String casNumber() {
        return casNumber;
    }

    String displayName() {
        return displayName;
    }

    static SyntheticReplaySubstance forRole(ConfirmationRole role) {
        return role == ConfirmationRole.INCIDENT ? INCIDENT : FACILITY;
    }
}
