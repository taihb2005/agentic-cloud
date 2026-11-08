package vn.edu.hust.kat.storageservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fs_node_permission")
public class FsNodePermission {

    public static final String VIEW = "VIEW";
    public static final String EDIT = "EDIT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", length = 20, nullable = false, unique = true)
    private PermissionCode code;

    @Column(name = "description", length = 255)
    private String description;

    public enum PermissionCode {
        VIEW, EDIT
    }
}
