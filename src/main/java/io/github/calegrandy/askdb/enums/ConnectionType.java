package io.github.calegrandy.askdb.enums;

public enum ConnectionType {
    POSTGRESQL("postgresql");

    private final String value;

    ConnectionType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
