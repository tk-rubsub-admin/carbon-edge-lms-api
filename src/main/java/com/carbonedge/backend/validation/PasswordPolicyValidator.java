package com.carbonedge.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

    private static final Pattern UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }

        if (value.length() < 8) {
            return false;
        }
        if (!UPPERCASE.matcher(value).matches()) {
            return false;
        }
        if (!LOWERCASE.matcher(value).matches()) {
            return false;
        }
        if (!DIGIT.matcher(value).matches()) {
            return false;
        }
        return SPECIAL.matcher(value).matches();
    }
}
