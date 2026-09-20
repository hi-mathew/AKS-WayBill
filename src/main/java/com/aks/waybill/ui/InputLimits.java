package com.aks.waybill.ui;

import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

/** Centralized input-length and numeric-input rules for the desktop UI. */
public final class InputLimits {
    public static final int COMPANY_NAME = 300;
    public static final int CONTACT_PERSON = 200;
    public static final int ADDRESS = 1000;
    public static final int PHONE = 100;
    public static final int EMAIL = 300;
    public static final int CARRIER = 300;
    public static final int DRIVER = 200;
    public static final int VEHICLE = 200;
    public static final int LOCATION = 400;
    public static final int PACKAGE_TYPE = 200;
    public static final int ITEM_DESCRIPTION = 600;
    public static final int SPECIAL_INSTRUCTIONS = Integer.MAX_VALUE;
    public static final int REMARKS = Integer.MAX_VALUE;
    public static final int USERNAME = 100;
    public static final int DISPLAY_NAME = 200;
    public static final int USER_CODE = 20;
    public static final int CR_NUMBER = 100;
    public static final int VAT_NUMBER = 100;
    public static final int PART1 = 60;
    public static final int SEQUENCE = 24;

    private InputLimits() {}

    public static void maxLength(TextInputControl control, int max) {
        control.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= max ? change : null));
    }

    public static void numeric(TextInputControl control, int maxLength, int maxDecimals) {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String value = change.getControlNewText();
            if (value.length() > maxLength) return null;
            if (value.isEmpty()) return change;
            String regex = maxDecimals <= 0
                    ? "\\d*"
                    : "\\d*(\\.\\d{0," + maxDecimals + "})?";
            return value.matches(regex) ? change : null;
        };
        control.setTextFormatter(new TextFormatter<String>(filter));
    }
}
