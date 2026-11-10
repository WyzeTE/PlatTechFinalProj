package com.example.dentalclinicappointmentapp;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Decides if a password is weak or strong. Kept separate from the screen so it is easy to test.
// Strong = at least 8 characters, with an uppercase letter, a lowercase letter, a number
// and a special character, and not a commonly used password.
public class PasswordChecker {
    public static final int MIN_LENGTH = 8;

    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
            "password", "password1", "password123", "passw0rd", "12345678", "123456789",
            "1234567890", "qwerty123", "qwertyuiop", "iloveyou", "admin123", "welcome1",
            "letmein123", "abc12345", "11111111", "00000000"));

    public static boolean isStrong(String password) {
        return missing(password).isEmpty();
    }

    // What the password still needs, in plain words. Empty when it is strong.
    public static String missing(String password) {
        if (password == null) password = "";

        StringBuilder sb = new StringBuilder();
        if (password.length() < MIN_LENGTH) append(sb, MIN_LENGTH + "+ characters");

        boolean upper = false, lower = false, digit = false, special = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else if (!Character.isWhitespace(c)) special = true;
        }
        if (!upper) append(sb, "an uppercase letter");
        if (!lower) append(sb, "a lowercase letter");
        if (!digit) append(sb, "a number");
        if (!special) append(sb, "a special character (like ! @ # $)");

        if (sb.length() == 0 && COMMON.contains(password.toLowerCase())) {
            return "something less common";
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(part);
    }
}