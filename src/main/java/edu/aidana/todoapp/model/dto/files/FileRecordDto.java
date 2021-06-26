package edu.aidana.todoapp.model.dto.files;

import edu.aidana.todoapp.model.enums.AccessLevelType;
import edu.aidana.todoapp.model.enums.FileType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FileRecordDto {

    private String id;
    private String name;
    private LocalDateTime lastUpdate;
    private FileType type;
    private long size; // in bytes
    private AccessLevelType accessLevel;

}
