package com.example.logicomposer.model;

import com.example.logicomposer.dto.Connection;
import com.example.logicomposer.enums.ConnectionType;

public record ConnectionResponse(Long id, String username, String password, String url, ConnectionType type) {

    public static ConnectionResponse from(Connection connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getUsername(),
                connection.getPassword(),
                connection.getUrl(),
                connection.getType()
        );
    }
}
