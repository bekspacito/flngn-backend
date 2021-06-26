package edu.aidana.todoapp.model.dto.share;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor

public class ShareDto {
    private Long userId;
    private String fileId;
}
