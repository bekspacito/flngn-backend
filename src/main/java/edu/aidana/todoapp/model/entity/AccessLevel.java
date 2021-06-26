package edu.aidana.todoapp.model.entity;

import edu.aidana.todoapp.model.enums.AccessLevelType;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter

@Entity
@Table(name = "access_level")
public class AccessLevel {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private FileRecord file;

    @Enumerated(EnumType.STRING)
    private AccessLevelType level;
}
