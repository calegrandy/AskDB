package io.github.calegrandy.askdb.model;

import io.github.calegrandy.askdb.enums.ConnectionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "connection")
public class Connection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    @ToString.Exclude
    private String password;
    private String url;
    @Enumerated(EnumType.STRING)
    private ConnectionType type;
}
