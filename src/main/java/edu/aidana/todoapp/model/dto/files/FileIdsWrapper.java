package edu.aidana.todoapp.model.dto.files;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class FileIdsWrapper {

    private List<String> fileIds = new ArrayList<>();

}
