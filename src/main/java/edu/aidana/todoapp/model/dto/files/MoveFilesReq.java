package edu.aidana.todoapp.model.dto.files;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class MoveFilesReq {

    private String srcId;
    private String destId;
    private List<String> fileIds = new ArrayList<>();

}
