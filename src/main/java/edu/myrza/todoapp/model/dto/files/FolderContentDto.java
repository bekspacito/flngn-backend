package edu.myrza.todoapp.model.dto.files;

import edu.myrza.todoapp.model.dto.search.NavDto;
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
