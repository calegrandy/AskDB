package com.example.logicomposer.controller;

import com.example.logicomposer.model.Connection;
import com.example.logicomposer.dto.ConnectionResponse;
import com.example.logicomposer.dto.ConnectionRequest;
import com.example.logicomposer.service.ConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/connections")
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
    public ResponseEntity<ConnectionResponse> createConnection(@RequestBody @Valid ConnectionRequest request) {
        Connection connection = connectionService.createConnection(request);
        ConnectionResponse response = ConnectionResponse.from(connection);

        // Build the Location from the current request URL, so it always matches this controller's mapping
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(connection.getId())
                .toUri();

        return ResponseEntity
                .created(location)  // 201 + Location header
                .body(response);
    }

    @PutMapping("/{id}")
    public ConnectionResponse replaceConnection(@PathVariable Long id, @RequestBody @Valid ConnectionRequest request) {
        Connection updated = connectionService.replaceConnection(id, request);
        return ConnectionResponse.from(updated);
    }

    @PatchMapping("/{id}")
    public ConnectionResponse updateConnection(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        return ConnectionResponse.from(connectionService.partialUpdate(id, updates));
    }

    // DELETE /connections/{id} -> remove a connection
    @DeleteMapping("/{id}")
    public void deleteConnection(@PathVariable Long id) {
        connectionService.deleteConnection(id);
    }
}
