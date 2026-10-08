package io.github.calegrandy.askdb.dto;

import io.github.calegrandy.askdb.enums.ConnectionType;
import io.github.calegrandy.askdb.model.Connection;

// Deliberately has no password field: credentials are write-only and never returned by the API.
public record ConnectionResponse(Long id, String username, String url, ConnectionType type) {

    public static ConnectionResponse from(Connection connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getUsername(),
                connection.getUrl(),
                connection.getType()
        );
    }
}
