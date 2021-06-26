package edu.aidana.todoapp.model.entity;

import edu.aidana.todoapp.model.enums.FileType;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;


@Getter
@Setter

@Entity
@Table(name = "file")
public class FileRecord {

    @Id
    private String id;
    private String name;
    @Column(name = "ext")
    private String extension;
    private long size;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id")
    private Status status;
    @Enumerated(EnumType.STRING)
    private FileType fileType;

    public FileRecord () {}

    public static FileRecord createFolder(String id, String name, User owner, Status status) {
        return new FileRecord(id, name, owner, status, FileType.FOLDER);
    }

    public static FileRecord createFile(String id, String name, String extension, long size, User owner, Status status) {
        return new FileRecord(id, name, extension, size, owner, status, FileType.FILE);
    }

    // create folder
    private FileRecord(String id, String name, User owner, Status status, FileType fileType) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.status = status;
        this.fileType = fileType;

        LocalDateTime _now = LocalDateTime.now();
        this.createdAt = _now;
        this.updatedAt = _now;
    }

    private FileRecord(String id, String name, String extension, long size, User owner, Status status, FileType fileType) {
        this.id = id;
        this.name = name;
        this.extension = extension;
        this.size = size;
        this.owner = owner;
        this.status = status;
        this.fileType = fileType;

        LocalDateTime _now = LocalDateTime.now();
        this.createdAt = _now;
        this.updatedAt = _now;
    }
}
