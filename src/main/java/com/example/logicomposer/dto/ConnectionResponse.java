package com.example.logicomposer.dto;

import com.example.logicomposer.enums.ConnectionType;
import com.example.logicomposer.model.Connection;

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
