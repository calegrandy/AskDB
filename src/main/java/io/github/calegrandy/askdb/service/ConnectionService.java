package io.github.calegrandy.askdb.service;

import io.github.calegrandy.askdb.enums.ConnectionType;
import io.github.calegrandy.askdb.exception.ConnectionNotFoundException;
import io.github.calegrandy.askdb.model.Connection;
import io.github.calegrandy.askdb.dto.ConnectionRequest;
import io.github.calegrandy.askdb.repository.ConnectionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ConnectionService {
    ConnectionRepository connectionRepository;

    public ConnectionService(ConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    public Connection createConnection(ConnectionRequest request) {
        Connection connection = new Connection();
        connection.setUsername(request.username());
        connection.setPassword(request.password());
        connection.setUrl(request.url());
        connection.setType(request.type());
        return connectionRepository.save(connection);
    }

    public List<Connection> getAllConnections() {
        return connectionRepository.findAll();
    }

    public Connection getConnection(Long id) {
        return connectionRepository.findById(id)
                .orElseThrow(() -> new ConnectionNotFoundException(
                        "No connection found with id: " + id));
    }

    public Connection replaceConnection(Long id, ConnectionRequest request) {
        Connection connection = getConnection(id);
        connection.setUsername(request.username());
        connection.setPassword(request.password());
        connection.setUrl(request.url());
        connection.setType(request.type());
        return connectionRepository.save(connection);
    }

    public Connection partialUpdate(Long id, Map<String, Object> updates) {
        Connection connection = connectionRepository.findById(id)
                .orElseThrow(() -> new ConnectionNotFoundException("No connection found with id: " + id));

        updates.forEach((field, value) -> {
            switch (field) {
                case "username" -> connection.setUsername((String) value);
                case "password" -> connection.setPassword((String) value);
                case "url" -> connection.setUrl((String) value);
                case "type" -> connection.setType(ConnectionType.valueOf(((String) value).toUpperCase()));
                default -> throw new IllegalArgumentException("Unknown field: " + field);
            }
        });

        return connectionRepository.save(connection);
    }

    public void deleteConnection(Long id) {
        connectionRepository.delete(getConnection(id));
    }
}
