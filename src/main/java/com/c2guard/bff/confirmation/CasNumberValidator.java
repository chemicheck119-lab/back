package com.c2guard.bff.confirmation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CasNumberValidator {

    private static final Pattern FORMAT = Pattern.compile("^([0-9]{2,7})-([0-9]{2})-([0-9])$");

    private CasNumberValidator() {
    }

    public static boolean isValid(String casNumber) {
        if (casNumber == null) {
            return false;
        }
        Matcher matcher = FORMAT.matcher(casNumber);
        if (!matcher.matches()) {
            return false;
        }
        String body = matcher.group(1) + matcher.group(2);
        int weightedSum = 0;
        int weight = 1;
        for (int index = body.length() - 1; index >= 0; index--) {
            weightedSum += Character.digit(body.charAt(index), 10) * weight;
            weight++;
        }
        return weightedSum % 10 == Character.digit(matcher.group(3).charAt(0), 10);
    }
}
