package edu.myrza.todoapp.model.dto.files;

import edu.myrza.todoapp.model.dto.user.UserDto;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class FileRecordDetailsDto {

    private String name;
    private String ext;
    private long size;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UserDto owner;

}
