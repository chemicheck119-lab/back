package com.c2guard.bff.record;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RecordIdGenerator {

    public String nextId() {
        return "REC-" + UUID.randomUUID();
    }
}
