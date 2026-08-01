package com.c2guard.bff.confirmation;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConfirmationIdGenerator {

    public String nextId() {
        return "CNF-" + UUID.randomUUID();
    }
}
