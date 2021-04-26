package edu.myrza.todoapp.service;

import edu.myrza.todoapp.model.dto.files.FileRecordDto;
import edu.myrza.todoapp.model.dto.search.NavDto;
import edu.myrza.todoapp.model.entity.FileRecord;
import edu.myrza.todoapp.model.entity.Status;
import edu.myrza.todoapp.model.entity.User;
import edu.myrza.todoapp.model.enums.EdgeType;
import edu.myrza.todoapp.model.enums.FileType;
import edu.myrza.todoapp.repos.AccessLevelRepository;
import edu.myrza.todoapp.repos.EdgeRepository;
import edu.myrza.todoapp.repos.FileRepository;
import edu.myrza.todoapp.util.FileRecordProjection;
import edu.myrza.todoapp.util.FileRecordProjectionImpl;
import edu.myrza.todoapp.util.Tuple;
import edu.myrza.todoapp.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class FileSearchService {

    private final AccessLevelRepository accessLevelRepo;
    private final EdgeRepository edgeRepo;
    private final FileRepository fileRepo;

    @Autowired
    public FileSearchService(
            EdgeRepository edgeRepo,
            FileRepository fileRepo,
            AccessLevelRepository accessLevelRepo)
    {
        this.edgeRepo = edgeRepo;
        this.fileRepo = fileRepo;
        this.accessLevelRepo = accessLevelRepo;
    }

    @Transactional(readOnly = true)
    public List<FileRecordDto> searchByName(User user, String namePart) {

        Optional<FileRecord> optRootFolder = fileRepo.findById(user.getUsername());
        if(!optRootFolder.isPresent())
            return Collections.emptyList();

        FileRecord root = optRootFolder.get();
        if(root.getStatus().getCode().equals(Status.Code.DELETED))
            return Collections.emptyList();

        // 1. Prepare ancestors (root + shared folders)
        List<FileRecord> searchStartingPoints = Utils.append(edgeRepo.serveDescendants(user.getUsername(), EdgeType.SHARED, FileType.FOLDER), root);

        List<FileRecord> result = edgeRepo.searchByName(searchStartingPoints, namePart);

        return result.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NavDto> buildNavigation(User currentUser, String fileId) {
        return buildNav(currentUser, fileId).stream()
                                            .map(this::toDto)
                                            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FileRecordProjection> buildNav(User currentUser, String fileId) {

        Optional<FileRecord> optFile = fileRepo.findById(fileId);
        if(!optFile.isPresent())
            return Collections.emptyList();

        FileRecord file = optFile.get();
        User owner = file.getOwner();
        if(!checkFileAccess(owner, currentUser).test(file))
            return Collections.emptyList();

        // Build a path up to owner's root folder
        List<FileRecordProjection> navigationList = edgeRepo.serveNavigation(fileId);

        if(!currentUser.equals(owner)) {
            // The file was shared with current user
            List<FileRecordProjection> newNavigationList = new ArrayList<>();

            // Access current user's root folder
            Optional<FileRecord> usersRootFolder = fileRepo.findById(currentUser.getUsername());
            if(!usersRootFolder.isPresent())
                return Collections.emptyList();
            FileRecord root = usersRootFolder.get();

            // Filter current navigation folders and leave only those that has been shared by an owner with the current user
            List<Tuple<FileRecord,FileRecord>> sharedFiles = edgeRepo.findSharedOnes(currentUser, toIds(navigationList));
            if(sharedFiles.isEmpty())
                return Collections.emptyList();

            // Then just use any of said folders
            Tuple<FileRecord,FileRecord> sharedFile = sharedFiles.get(0);
            FileRecord ancs = sharedFile._t1;
            FileRecord desc = sharedFile._t2;
            for(FileRecordProjection f : navigationList) {
                newNavigationList.add(f);
                if(f.getId().equals(desc.getId()))
                    break;
            }

            newNavigationList.add(toProj(ancs));
            newNavigationList.add(toProj(root));
            navigationList = newNavigationList;
        }

        Collections.reverse(navigationList);
        return navigationList;
    }

    private NavDto toDto (FileRecordProjection file) {
        return new NavDto(file.getId(), file.getName(), file.getFiletype());
    }

    private FileRecordDto toDto (FileRecord fileRecord) {
        FileRecordDto dto = new FileRecordDto();
        dto.setId(fileRecord.getId());
        dto.setName(fileRecord.getName());
        dto.setType(fileRecord.getFileType());
        dto.setSize(fileRecord.getSize());
        dto.setLastUpdate(fileRecord.getUpdatedAt());
        return dto;
    }

    private List<String> toIds(List<FileRecordProjection> files) {
        return files.stream().map(FileRecordProjection::getId).collect(Collectors.toList());
    }

    private FileRecordProjection toProj(FileRecord file) {
        return new FileRecordProjectionImpl(file.getId(), file.getName(), file.getFileType().name());
    }

    private Predicate<FileRecord> checkFileAccess(User owner, User currentUser) {
        return file -> {
            // file has been deleted
            if(file.getStatus().getCode().equals(Status.Code.DELETED)) return false;
            // the current user is not an owner and has no access level for downloading
            return owner.equals(currentUser) || accessLevelRepo.hasReadOnlyLevel(currentUser, file);
        };
    }
}
