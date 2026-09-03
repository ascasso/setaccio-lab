package com.setaccio.lab.thinking;

/** Which of the two pre-registered models an arm uses. */
public enum ThinkingDiagnosticModelRole {

    /** The model under test, expected to advertise the thinking capability. */
    SUBJECT,

    /** The supplementary model control, expected not to advertise thinking. */
    CONTROL
}
