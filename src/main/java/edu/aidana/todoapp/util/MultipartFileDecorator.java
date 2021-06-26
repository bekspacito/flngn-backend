package edu.aidana.todoapp.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class MultipartFileDecorator {

    private MultipartFile multipartFile;
    private String name;

    public MultipartFileDecorator(MultipartFile multipartFile, String newName) {
        this.multipartFile = multipartFile;
        this.name = newName;
    }

}
