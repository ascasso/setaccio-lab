package com.setaccio.lab.chat;

/** Records whether a common contract option is applied as requested or handled provider-specifically. */
public enum ChatProviderOptionStatus {
    SUPPORTED,
    TRANSLATED,
    IGNORED,
    REJECTED
}
