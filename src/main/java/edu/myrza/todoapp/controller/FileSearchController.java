package edu.myrza.todoapp.controller;

import edu.myrza.todoapp.model.dto.files.FileRecordDto;
import edu.myrza.todoapp.model.dto.search.NavDto;
import edu.myrza.todoapp.model.entity.User;
import edu.myrza.todoapp.service.FileSearchService;
import edu.myrza.todoapp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
public class FileSearchController {

    private final UserService userService;
    private final FileSearchService fileSearchService;

    @Autowired
    public FileSearchController(FileSearchService fileSearchService, UserService userService) {
        this.userService = userService;
        this.fileSearchService = fileSearchService;
    }

    @GetMapping("/file/search/{namePart}")
    public List<FileRecordDto> searchByName(Principal principal, @PathVariable("namePart") String namePart) {
        User currentUser = userService.loadUserByUsername(principal.getName());
        return fileSearchService.searchByName(currentUser, namePart);
    }

    @GetMapping("/file/navigation/{fileId}")
    public List<NavDto> buildNavigation(Principal principal, @PathVariable("fileId") String fileId) {
        User currentUser = userService.loadUserByUsername(principal.getName());
        return fileSearchService.buildNavigation(currentUser, fileId);
    }

}
