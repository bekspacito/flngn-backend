package edu.aidana.todoapp.model.dto.files;

import edu.aidana.todoapp.model.dto.search.NavDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FolderContentDto {
    private List<NavDto> navigation = new ArrayList<>();
    private List<FileRecordDto> content = new ArrayList<>();
}
