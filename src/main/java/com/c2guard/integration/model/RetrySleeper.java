package com.c2guard.integration.model;

@FunctionalInterface
interface RetrySleeper {

    void sleep(long millis) throws InterruptedException;
}
