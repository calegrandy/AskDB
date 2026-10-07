package com.example.logicomposer.dto;

import com.example.logicomposer.enums.ConnectionType;
import com.example.logicomposer.model.Connection;

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
