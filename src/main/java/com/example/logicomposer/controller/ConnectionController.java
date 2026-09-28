package com.example.logicomposer.controller;

import com.example.logicomposer.dto.Connection;
import com.example.logicomposer.model.ConnectionResponse;
import com.example.logicomposer.model.CreateConnectionRequest;
import com.example.logicomposer.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/connections")
public class ConnectionController {

    private final ConnectionService connectionService;

    public ConnectionController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping
    public List<ConnectionResponse> getAllConnections() {
        return connectionService.getAllConnections()
                .stream()
                .map(ConnectionResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ConnectionResponse getConnection(@PathVariable Long id) {
        return ConnectionResponse.from(connectionService.getConnection(id));
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> createConnection(@RequestBody @Valid CreateConnectionRequest request) {
        Connection connection = connectionService.createConnection(request);
        ConnectionResponse response = ConnectionResponse.from(connection);

        return ResponseEntity
                .created(URI.create("/api/connections/" + connection.getId()))  // 201 + Location header
                .body(response);
    }

    @PutMapping("/{id}")
    public ConnectionResponse replaceConnection(@RequestBody Connection connection) {
        Connection updated = connectionService.replaceConnection(connection);
        return ConnectionResponse.from(updated);
    }

    @PatchMapping("/{id}")
    public Connection updateConnection(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return connectionService.partialUpdate(id, updates);
    }

    // DELETE /connections/{id} -> remove a connection
    @DeleteMapping("/{id}")
    public void deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
    }
}
