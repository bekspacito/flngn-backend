package edu.myrza.todoapp.service;

import edu.myrza.todoapp.model.dto.share.ShareDto;
import edu.myrza.todoapp.model.entity.*;
import edu.myrza.todoapp.model.enums.AccessLevelType;
import edu.myrza.todoapp.model.enums.EdgeType;
import edu.myrza.todoapp.model.enums.FileType;
import edu.myrza.todoapp.repos.AccessLevelRepository;
import edu.myrza.todoapp.repos.EdgeRepository;
import edu.myrza.todoapp.repos.StatusRepository;
import edu.myrza.todoapp.util.Tuple;
import edu.myrza.todoapp.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShareService {

    private AccessLevelRepository accessLevelRepo;
    private EdgeRepository edgeRepo;
    private StatusRepository statusRepo;

    private FileService fileService;

    @Autowired
    public ShareService(
            AccessLevelRepository accessLevelRepo,
            EdgeRepository edgeRepo,
            StatusRepository statusRepo,
            FileService fileService)
    {
        this.accessLevelRepo = accessLevelRepo;
        this.edgeRepo = edgeRepo;
        this.statusRepo = statusRepo;
        this.fileService = fileService;

    }

    // Returns list of successfully shared files/folders
    @Transactional
    public List<ShareDto> share(User owner, List<User> users, List<String> fileIds) {

        // 1. access users' root folders
        List<Tuple<User, FileRecord>> usersAndRootFolders = fileService.getRootFolders(users);
        List<ShareDto> result = new ArrayList<>();

        Status deleted = statusRepo.findByCode(Status.Code.DELETED);

        // 2.
        for(String fileId : fileIds) {

            // 3. Access file
            Optional<FileRecord> optFile = fileService.findById(fileId);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();
            if(!file.getOwner().equals(owner))
                continue;

            if(file.getStatus().equals(deleted))
                continue;

            // Here, we create new edges, each per new user and the edges point to
            // the shared file/folder
            List<Edge> newSharedEdges = usersAndRootFolders.stream().map(toSharedEdge(file)).collect(Collectors.toList());
            fileService.saveEdges(newSharedEdges);

            // 4.
            if(file.getFileType().equals(FileType.FILE)) {
                // 4.2. Add new entries into 'access_level' table for the file
                List<AccessLevel> accessLevelEntries = users.stream().map(toAccessLevel(file)).collect(Collectors.toList());

                accessLevelRepo.saveAll(accessLevelEntries);

                List<ShareDto> logs = users.stream().map(user -> new ShareDto(user.getId(), fileId)).collect(Collectors.toList());
                result.addAll(logs);
            }

            if(file.getFileType().equals(FileType.FOLDER)) {
                // 4.2. Add new entries into 'access_level' table for the file and it's sub-files as well
                Set<FileRecord> descendants = Utils.append(fileService.getAllDescendants(file), file);
                List<AccessLevel> accessLevelEntries = descendants.stream()
                                                                    .flatMap(descendant -> users.stream().map(toAccessLevel(descendant)))
                                                                    .collect(Collectors.toList());

                accessLevelRepo.saveAll(accessLevelEntries);

                List<ShareDto> logs = descendants.stream()
                                                    .flatMap(descendant -> users.stream().map(user -> new ShareDto(user.getId(), descendant.getId())))
                                                    .collect(Collectors.toList());
                result.addAll(logs);
            }
        }

        return result;
    }

    // When the owner of the files decided that some users can no longer have access to given files
    // Returns list of successfully unshared files/folders
    @Transactional
    public List<ShareDto> unShare(User owner, List<User> users, List<String> fileIds) {

        // 1. Access users' root folders
        List<Tuple<User, FileRecord>> usersAndRootFolders = fileService.getRootFolders(users);
        List<FileRecord> rootFolders = usersAndRootFolders.stream().map(tuple -> tuple._t2).collect(Collectors.toList());
        List<ShareDto> result = new ArrayList<>();

        for(String fileId : fileIds) {

            // 3. Access file
            Optional<FileRecord> optFile = fileService.findById(fileId);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();
            if(!file.getOwner().equals(owner))
                continue;

            if(file.getFileType().equals(FileType.FILE)) {
                // delete all "shared" edges
                edgeRepo.deleteByAncestorInAndDescendantIn(new HashSet<>(rootFolders), Collections.singleton(file));
                // delete 'access level' entries
                accessLevelRepo.deleteByUserInAndFile(new HashSet<>(users), file);
                // log the action
                List<ShareDto> logs = users.stream().map(user -> new ShareDto(user.getId(), fileId)).collect(Collectors.toList());
                result.addAll(logs);
            }

            if(file.getFileType().equals(FileType.FOLDER)) {
                // access all of the descendants
                Set<FileRecord> descendants = Utils.append(fileService.getAllDescendants(file), file);
                // delete edges
                edgeRepo.deleteByAncestorInAndDescendantIn(new HashSet<>(rootFolders), Collections.singleton(file));
                // delete 'access_level' entries
                accessLevelRepo.deleteByUserInAndFileIn(new HashSet<>(users), descendants);
                // log the action
                List<ShareDto> logs = descendants.stream()
                                                .flatMap(descendant -> users.stream().map(user -> new ShareDto(user.getId(), descendant.getId())))
                                                .collect(Collectors.toList());
                result.addAll(logs);
            }
        }

        return result;
    }

    // When the receiving users don't want the shared-with-them files anymore
    // Returns list of successfully refused files/folders
    @Transactional
    public List<ShareDto> refuseShare(User user, List<String> fileIds) {

        // 1. Access user's root folders
        List<FileRecord> rootFolders = fileService.getRootFolders(Collections.singletonList(user)).stream()
                                                    .map(tuple -> tuple._t2)
                                                    .collect(Collectors.toList());
        List<ShareDto> result = new ArrayList<>();

        for(String fileId : fileIds) {

            // 3. Access file
            Optional<FileRecord> optFile = fileService.findById(fileId);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();

            if(file.getFileType().equals(FileType.FILE)) {
                // delete all "shared" edges
                edgeRepo.deleteByAncestorInAndDescendantIn(new HashSet<>(rootFolders), Collections.singleton(file));
                // delete 'access level' entries
                accessLevelRepo.deleteByUserInAndFile(Collections.singleton(user), file);
                // log the action
                result.add(new ShareDto(user.getId(), fileId));
            }

            if(file.getFileType().equals(FileType.FOLDER)) {
                // access all of the descendants
                Set<FileRecord> descendants = Utils.append(fileService.getAllDescendants(file), file);
                // delete edges
                edgeRepo.deleteByAncestorInAndDescendantIn(new HashSet<>(rootFolders), Collections.singleton(file));
                // delete 'access_level' entries
                accessLevelRepo.deleteByUserInAndFileIn(Collections.singleton(user), descendants);
                // log the action
                List<ShareDto> logs = descendants.stream()
                        .map(descendant -> new ShareDto(user.getId(), descendant.getId()))
                        .collect(Collectors.toList());
                result.addAll(logs);
            }
        }

        return result;
    }

    private Function<Tuple<User, FileRecord>, Edge> toSharedEdge(FileRecord descendant) {
        return tuple -> {
            Edge edge = new Edge();

            edge.setId(UUID.randomUUID().toString());
            edge.setEdgeType(EdgeType.SHARED);
            edge.setAncestor(tuple._t2);
            edge.setEdgeOwner(tuple._t1);
            edge.setDescendant(descendant);

            return edge;
        };
    }

    private Function<User, AccessLevel> toAccessLevel(FileRecord file) {
        return user -> {
            AccessLevel accessLevel = new AccessLevel();

            accessLevel.setId(UUID.randomUUID().toString());
            accessLevel.setFile(file);
            accessLevel.setUser(user);
            accessLevel.setLevel(AccessLevelType.READ_ONLY);

            return accessLevel;
        };
    }

}
