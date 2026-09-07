package com.c2guard.bff.confirmation;

record ConfirmationCancelResult(
        ConfirmationCancellation cancellation,
        boolean created
) {
}
