package edu.aidana.todoapp.service;

import edu.aidana.todoapp.exceptions.SystemException;
import edu.aidana.todoapp.model.dto.files.FolderContentDto;
import edu.aidana.todoapp.model.dto.search.NavDto;
import edu.aidana.todoapp.model.entity.*;
import edu.aidana.todoapp.model.enums.AccessLevelType;
import edu.aidana.todoapp.repos.StatusRepository;
import edu.aidana.todoapp.util.*;
import edu.aidana.todoapp.model.dto.files.FileRecordDetailsDto;
import edu.aidana.todoapp.model.dto.files.FileRecordDto;
import edu.aidana.todoapp.model.dto.user.UserDto;
import edu.aidana.todoapp.model.enums.EdgeType;
import edu.aidana.todoapp.model.enums.FileType;
import edu.aidana.todoapp.repos.AccessLevelRepository;
import edu.aidana.todoapp.repos.EdgeRepository;
import edu.aidana.todoapp.repos.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileService {

    private final FileSystemUtil fileSystemUtil;

    private final FileSearchService fileSearchService;

    private final StatusRepository statusRepo;
    private final FileRepository fileRepo;
    private final EdgeRepository edgeRepo;
    private final AccessLevelRepository accessLevelRepo;

    @Autowired
    public FileService(
            FileSystemUtil fileSystemUtil,
            FileSearchService fileSearchService,
            StatusRepository statusRepo,
            FileRepository fileRepo,
            EdgeRepository edgeRepo,
            AccessLevelRepository accessLevelRepo)
    {
        this.fileSystemUtil = fileSystemUtil;
        this.fileSearchService = fileSearchService;
        this.statusRepo = statusRepo;
        this.fileRepo = fileRepo;
        this.edgeRepo = edgeRepo;
        this.accessLevelRepo = accessLevelRepo;
    }

    // USER RELATED OPERATIONS

    // TODO : Make sure usernames are unique. (data invariant)
    @Transactional
    public FileRecordDto prepareUserRootFolder(User user) {

        try {
            // Create an actual folder/directory in fyle_system
            String rootFolderName = user.getUsername();
            fileSystemUtil.createUserRootFolder(rootFolderName);

            Status enabled = statusRepo.findByCode(Status.Code.ENABLED);

            // Save a record about the created root folder in db
            FileRecord rootFolderRecord = FileRecord.createFolder(rootFolderName, rootFolderName, user, enabled);

            return toDtoByOwner(fileRepo.save(rootFolderRecord));
        } catch (IOException ex) {
            throw new SystemException(ex, "Error creating a root folder for a user [" + user.getUsername() + "]");
        }
    }

    // FOLDER/FILE OPERATIONS

    public Optional<FileRecordDetailsDto> getDetails(String fileId) {
        return fileRepo.findById(fileId).map(this::toDetailsDto);
    }

    @Transactional
    public void deleteFiles(User user, List<String> ids) {

        for(String id : ids) {
            Optional<FileRecord> optFile = fileRepo.findById(id);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();
            if(file.getOwner().equals(user)) {
                deleteAsOwner(file);
            } else {
                deleteAsShared(Collections.singletonList(user), Collections.singletonList(file));
            }
        }

    }

    private void deleteAsShared(List<User> users, List<FileRecord> files) {
        // 1. Access users' root folders
        List<Tuple<User, FileRecord>> usersAndRootFolders = getRootFolders(users);
        List<FileRecord> rootFolders = usersAndRootFolders.stream().map(tuple -> tuple._t2).collect(Collectors.toList());

        for(FileRecord file : files) {

            if(file.getFileType().equals(FileType.FILE)) {
                // delete all "shared" edges
                edgeRepo.deleteByAncestorInAndDescendantIn(new HashSet<>(rootFolders), Collections.singleton(file));
                // delete 'access level' entries
                accessLevelRepo.deleteByUserInAndFile(new HashSet<>(users), file);
            }

            if(file.getFileType().equals(FileType.FOLDER)) {
                // access all of the descendants
                Set<FileRecord> descendants = Utils.append(getAllDescendants(file), file);
                // delete edges
                edgeRepo.deleteByAncestorInAndDescendantIn(new HashSet<>(rootFolders), Collections.singleton(file));
                // delete 'access_level' entries
                accessLevelRepo.deleteByUserInAndFileIn(new HashSet<>(users), descendants);
            }
        }
    }

    private void deleteAsOwner(FileRecord file) {

        Status deleted = statusRepo.findByCode(Status.Code.DELETED);

        // mark the file as 'deleted'
        file.setStatus(deleted);

        // if the file is 'file' then just save it and continue
        if (!file.getFileType().equals(FileType.FOLDER)) {
            fileRepo.save(file);
            return;
        }

        // if the file is a folder then mark it's sub folders/files as 'deleted'
        Set<FileRecord> descendants = edgeRepo.serveAllDescendants(file.getId());
        for (FileRecord descendant : descendants) {
            descendant.setStatus(deleted);
        }

        descendants.add(file);
        fileRepo.saveAll(descendants);
    }

    @Transactional
    public Optional<FileRecordDto> renameFile(User user, String fileId, String newName) {
        Optional<FileRecord> optFileRecord = fileRepo.findById(fileId);
        if(!optFileRecord.isPresent())
            return Optional.empty();

        FileRecord file = optFileRecord.get();
        if(!file.getOwner().equals(user))
            return Optional.empty();

        file.setName(newName);
        fileRepo.save(file);
        return Optional.of(toDtoByOwner(file));
    }

    // Download multiple files
    @Transactional(readOnly = true)
    public Optional<Resource> downloadFiles(User user, List<String> ids) throws IOException {

        // Access files to download via their ids and filter out those files that cannot be downloaded (has no access, deleted etc)
        List<FileRecord> filesToDownload = fileRepo.findAllById(ids).stream()
                                                                .filter(checkFileAccess(user))
                                                                .collect(Collectors.toList());
        if(filesToDownload.isEmpty()) return Optional.empty();

        // create a tree of files/folders you are gonna send back
        List<TreeNode> nodes = buildTree(filesToDownload);

        // use the tree to create appropriate .zip file
        File compressedFile = fileSystemUtil.compressAndReturnFiles(nodes);

        // turn the .zip file into resource
        return Optional.of(new FileSystemResource(compressedFile));
    }

    @Transactional
    public List<FileRecordDto> moveFiles(User user, String srcId, String destId, List<String> filesToMove) {

        // Here, we make sure all the entries are unique and are not equal to either source folder or destination folder
        filesToMove = filesToMove.stream().distinct().filter(notSrcAndDest(srcId, destId)).collect(Collectors.toList());

        List<FileRecordDto> result = new ArrayList<>();

        // Prepare source folder data
        Optional<FileRecord> optSrc = fileRepo.findById(srcId);
        if(!optSrc.isPresent())
            return Collections.emptyList();

        FileRecord src = optSrc.get();
        if(!checkFileAccess(user).test(src))
            return Collections.emptyList();

        Set<FileRecord> srcAncestors = Utils.append(edgeRepo.serveOwnedAncestors(srcId), src);
        Set<User> srcUsers = accessLevelRepo.findAllByFileIn(srcAncestors).stream()
                                                .map(AccessLevel::getUser)
                                                .collect(Collectors.toSet());

        // Prepare destination folder data
        Optional<FileRecord> optDestFolder = fileRepo.findById(destId);
        if(!optDestFolder.isPresent())
            return result;

        FileRecord destFolder = optDestFolder.get();
        if(!checkFileAccess(user).test(src))
            return Collections.emptyList();

        Set<FileRecord> newAncestors = Utils.append(edgeRepo.serveOwnedAncestors(destFolder.getId()), destFolder);
        List<AccessLevel> destUsersAccessLevels = accessLevelRepo.findAllByFileIn(newAncestors);

        // Prepare newly created edges and newly created access levels holders
        List<Edge> allNewEdges = new ArrayList<>();
        List<AccessLevel> allNewAccessLevels = new ArrayList<>();

        //
        for(String fileId : filesToMove) {

            Optional<FileRecord> optFile = fileRepo.findById(fileId);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();

            if(file.getFileType().equals(FileType.FILE)) {
                // 1.
                edgeRepo.deleteByDescendant(file);
                // 2.
                List<Edge> newEdges = newAncestors.stream().map(newAncestor -> {
                    Edge newEdge = new Edge();
                    newEdge.setId(UUID.randomUUID().toString());
                    newEdge.setEdgeOwner(user);
                    newEdge.setAncestor(newAncestor);
                    newEdge.setDescendant(file);

                    if(newAncestor.equals(destFolder))
                        newEdge.setEdgeType(EdgeType.DIRECT);
                    else
                        newEdge.setEdgeType(EdgeType.INDIRECT);

                    return newEdge;
                }).collect(Collectors.toList());

                // new edges
                allNewEdges.addAll(newEdges);

                // new access levels
                    // 1. delete previous access level entries
                accessLevelRepo.deleteByUserInAndFile(srcUsers, file);
                    // 2. set new access level entries
                List<AccessLevel> newAccessLevels = destUsersAccessLevels.stream().map(toAccessLevel(file)).collect(Collectors.toList());
                allNewAccessLevels.addAll(newAccessLevels);

                result.add(toDtoByOwner(file));
                continue;
            }

            if(file.getFileType().equals(FileType.FOLDER)) {

                // 1.1 Fetch current ancestors
                Set<FileRecord> currentAncestors = edgeRepo.serveOwnedAncestors(file.getId());

                // 1.2 Fetch all of the descendants + add the folder itself
                Set<FileRecord> descendants = Utils.append(edgeRepo.serveAllDescendants(file.getId()), file);

                // 1.3 Delete every edge between 'currentAncestors' and 'descendants'
                edgeRepo.deleteByAncestorInAndDescendantIn(currentAncestors, descendants);

                // 2.1
                List<Edge> newEdges = newAncestors.stream()
                                                  .flatMap(newAncestor -> descendants.stream().map(descendant -> {
                                                      Edge newEdge = new Edge();
                                                      newEdge.setId(UUID.randomUUID().toString());
                                                      newEdge.setEdgeOwner(user);
                                                      newEdge.setAncestor(newAncestor);
                                                      newEdge.setDescendant(descendant);

                                                      if(newAncestor.equals(destFolder) && descendant.equals(file))
                                                          newEdge.setEdgeType(EdgeType.DIRECT);
                                                      else
                                                          newEdge.setEdgeType(EdgeType.INDIRECT);

                                                      return newEdge;
                                                  }))
                                                  .collect(Collectors.toList());

                // 2.2
                allNewEdges.addAll(newEdges);

                // new access levels
                    // 1. delete previous access level entries
                accessLevelRepo.deleteByUserInAndFileIn(srcUsers, descendants);
                    // 2. set new access level entries
                List<AccessLevel> newAccessLevels = descendants.stream()
                                                                .flatMap(descFile -> destUsersAccessLevels.stream().map(toAccessLevel(descFile)))
                                                                .collect(Collectors.toList());

                allNewAccessLevels.addAll(newAccessLevels);
            }

            result.add(toDtoByOwner(file));
        }

        if(!allNewEdges.isEmpty()) {
            edgeRepo.saveAll(allNewEdges);
        }

        if(!allNewAccessLevels.isEmpty()) {
            accessLevelRepo.saveAll(allNewAccessLevels);
        }

        return result;
    }

    // FOLDER OPERATIONS

    // TODO : Test the method !!!
    @Transactional(readOnly = true)
    public FolderTreeNode buildFileSystemTree(User user) {

        Queue<FolderTreeNode> queue = new ArrayDeque<>();

        FolderTreeNode root = new FolderTreeNode(user.getUsername(), user.getUsername());

        queue.add(root);
        while(!queue.isEmpty()) {
            FolderTreeNode currentNode = queue.poll();
            // Direct sub nodes to 'currentNode'
            List<Edge> edges = edgeRepo.serveEdges(currentNode.getId(), FileType.FOLDER, Collections.singletonList(EdgeType.DIRECT));
            List<FolderTreeNode> directSubNodes = edges.stream()
                                                         .map(e -> new FolderTreeNode(e.getDescendant().getId(), e.getDescendant().getName()))
                                                         .peek(queue::add)
                                                         .collect(Collectors.toList());
            currentNode.setSubnodes(directSubNodes);
        }

        return root;
    }

    @Transactional
    public FileRecordDto createFolder(User user, String parentId, String folderName) {

        Status enabled = statusRepo.findByCode(Status.Code.ENABLED);

        // First we create folderRecord
        FileRecord folderRecord = FileRecord.createFolder(UUID.randomUUID().toString(), folderName, user, enabled);

        FileRecord savedFolderRecord = fileRepo.save(folderRecord);

        // Then we create edges
        // access all of the ancestors of 'parent' folderRecord and save new edges
        Set<Edge> ancestorsEdges = edgeRepo.serveAncestors(parentId).stream()
                .map(ancestor -> new Edge(UUID.randomUUID().toString(), ancestor, savedFolderRecord, EdgeType.INDIRECT, user))
                .collect(Collectors.toSet());

        // access 'parent' folder
        Optional<FileRecord> optParent = fileRepo.findById(parentId);
        Edge parentEdge = optParent.map(parent -> new Edge(UUID.randomUUID().toString(), parent, savedFolderRecord, EdgeType.DIRECT, user))
                                    .orElseThrow(() -> new RuntimeException("No folderRecord with id [" + parentId + "] is found"));

        ancestorsEdges.add(parentEdge);

        edgeRepo.saveAll(ancestorsEdges);

        // Assign an access level the parent folder has to new files
        List<FileRecord> finalFileRecords = Collections.singletonList(savedFolderRecord);
        List<AccessLevel> readOnlyAccessLevel = accessLevelRepo.findAllByFile(optParent.get()).stream()
                                                                .flatMap(parentAccessLevel -> finalFileRecords.stream().map(toAccessLevel(parentAccessLevel)))
                                                                .collect(Collectors.toList());
        accessLevelRepo.saveAll(readOnlyAccessLevel);

        return toDtoByOwner(savedFolderRecord);
    }

    @Transactional(readOnly = true)
    public FolderContentDto serveFolderContent(User user, String folderId) {
        // prepare content
        Optional<FileRecord> optFolder = fileRepo.findById(folderId).filter(checkFileAccess(user));
        if(!optFolder.isPresent())
            return new FolderContentDto();

        List<FileRecordDto> content = edgeRepo.serveDescendants(folderId, Arrays.asList(EdgeType.DIRECT, EdgeType.SHARED)).stream()
                                                .filter(checkFileAccess(user))
                                                .map(toDtoByUser(user))
                                                .collect(Collectors.toList());

        // prepare navigation info
        List<NavDto> navigation = fileSearchService.buildNavigation(user, folderId);

        return new FolderContentDto(navigation, content);
    }

    public FolderContentDto serveInFolder(User user, String fileId) {

        List<NavDto> navigation = fileSearchService.buildNavigation(user, fileId);
        if(navigation.size() < 2)
            return new FolderContentDto();

        NavDto parentFolder = navigation.get(navigation.size() - 2);

        List<FileRecordDto> content = edgeRepo.serveDescendants(parentFolder.getId(), Arrays.asList(EdgeType.DIRECT, EdgeType.SHARED))
                                                .stream()
                                                .filter(checkFileAccess(user))
                                                .map(toDtoByUser(user))
                                                .collect(Collectors.toList());

        return new FolderContentDto(navigation.subList(0, navigation.size() - 1), content);
    }

    // FILE OPERATIONS

    @Transactional
    public List<FileRecordDto> uploadFiles(User user, String folderId, MultipartFile[] files) {

        // Save files in disk
        List<MultipartFileDecorator> fileDecorators = Stream.of(files)
                .map(mf -> new MultipartFileDecorator(mf, UUID.randomUUID().toString()))
                .collect(Collectors.toList());

        List<MultipartFileDecorator> savedFiles = fileSystemUtil.saveFile(user.getUsername(), fileDecorators);

        // Save records about files
        List<FileRecord> fileRecords = new ArrayList<>();
        for(MultipartFileDecorator savedFile : savedFiles) {
            fileRecords.add(toFile(user, savedFile));
        }
        fileRecords = fileRepo.saveAll(fileRecords);

        // Create and save edges/connection from all of the ancestors to the files (The Closure table)
        FileRecord parent = fileRepo.getOne(folderId);
        Set<FileRecord> ancestors = Utils.append(edgeRepo.serveOwnedAncestors(folderId), parent);

        List<Edge> edges = fileRecords.stream()
                                        .flatMap(descendant -> ancestors.stream().map(ancestor -> toEdge(user,parent,ancestor,descendant)))
                                        .collect(Collectors.toList());

        edgeRepo.saveAll(edges);

        // Assign an access level the parent folder has to new files
        List<FileRecord> finalFileRecords = fileRecords;
        List<AccessLevel> readOnlyAccessLevel = accessLevelRepo.findAllByFile(parent).stream()
                                                                .flatMap(parentAccessLevel -> finalFileRecords.stream().map(toAccessLevel(parentAccessLevel)))
                                                                .collect(Collectors.toList());
        accessLevelRepo.saveAll(readOnlyAccessLevel);

        return fileRecords.stream().map(this::toDtoByOwner).collect(Collectors.toList());
    }

    // Download single file
    @Transactional
    public Optional<ResourceDecorator> downloadFile(User user, String fileId) {

        Optional<FileRecord> optFile = fileRepo.findById(fileId).filter(checkFileAccess(user));
        if(!optFile.isPresent())
            return Optional.empty();

        FileRecord fileRecord = optFile.get();
        ResourceDecorator resourceDecorator = new ResourceDecorator();

        File file = fileSystemUtil.serveFile(fileRecord.getOwner().getUsername(), fileId, fileRecord.getExtension());
        resourceDecorator.setResource(new FileSystemResource(file));
        resourceDecorator.setOriginalName(fileRecord.getName());

        return Optional.of(resourceDecorator);
    }

    // HELPER OPERATIONS

    Optional<FileRecord> findById(String id) {
        return fileRepo.findById(id);
    }

    @Transactional
    List<Tuple<User, FileRecord>> getRootFolders(List<User> users) {
        List<String> ids = users.stream().map(User::getUsername).collect(Collectors.toList());
        List<FileRecord> rootFolders = fileRepo.findAllById(ids);

        return users.stream().map(user -> {
                        return new Tuple<>(user, rootFolders.stream().filter(rf -> rf.getOwner().equals(user)).findAny());
                    }).filter(tuple -> {
                        return tuple._t2.isPresent();
                    })
                    .map(tuple -> {
                        return new Tuple<>(tuple._t1, tuple._t2.get());
                    })
                    .collect(Collectors.toList());
    }

    Set<FileRecord> getAllDescendants(FileRecord folder) {
        return edgeRepo.serveAllDescendants(folder.getId());
    }

    void saveEdges(List<Edge> edges) {
        edgeRepo.saveAll(edges);
    }

    private List<TreeNode> buildTree(List<FileRecord> files) {
        List<TreeNode> nodes = new ArrayList<>();

        for(FileRecord file : files) {

            if(file.getFileType().equals(FileType.FILE)) {
                FileTreeNode fileTreeNode = new FileTreeNode();
                fileTreeNode.setId(file.getId());
                fileTreeNode.setType(TreeNode.Type.FILE);
                fileTreeNode.setName(file.getName());
                fileTreeNode.setOwnerName(file.getOwner().getUsername());
                nodes.add(fileTreeNode);
                continue;
            }

            if(file.getFileType().equals(FileType.FOLDER)) {
                FolderTreeNode folderTreeNode = new FolderTreeNode();
                folderTreeNode.setId(file.getId());
                folderTreeNode.setName(file.getName());
                folderTreeNode.setType(TreeNode.Type.FOLDER);
                folderTreeNode.setOwnerName(file.getOwner().getUsername());
                List<FileRecord> subFiles = edgeRepo.serveDescendants(file.getId(), EdgeType.DIRECT);

                folderTreeNode.setSubnodes(buildTree(subFiles));
                nodes.add(folderTreeNode);
            }
        }

        return nodes;
    }

    private Predicate<FileRecord> checkFileAccess(User currentUser) {
        return file -> {
            // file has been deleted
            if(file.getStatus().getCode().equals(Status.Code.DELETED)) return false;
            // the current user is not an owner and has no access level for downloading
            return file.getOwner().equals(currentUser) || accessLevelRepo.hasReadOnlyLevel(currentUser, file);
        };
    }

    private String extractExt(String fileOriginalName) {

        if(fileOriginalName == null || fileOriginalName.isEmpty()) return "";

        int lastIndexOfDot = fileOriginalName.lastIndexOf('.');
        if(lastIndexOfDot < 0 || lastIndexOfDot == fileOriginalName.length() - 1) return "";

        return fileOriginalName.substring(lastIndexOfDot);
    }

    private FileRecord toFile(User owner, MultipartFileDecorator savedFile) {
        MultipartFile mFile = savedFile.getMultipartFile();

        Status enabled = statusRepo.findByCode(Status.Code.ENABLED);

        return FileRecord.createFile(
                savedFile.getName(),
                mFile.getOriginalFilename(),
                extractExt(mFile.getOriginalFilename()),
                mFile.getSize(),
                owner, enabled
        );
    }

    // Mapping to dto is done by a file's owner
    private FileRecordDto toDtoByOwner(FileRecord fileRecord) {
        FileRecordDto dto = new FileRecordDto();
        dto.setId(fileRecord.getId());
        dto.setName(fileRecord.getName());
        dto.setType(fileRecord.getFileType());
        dto.setSize(fileRecord.getSize());
        dto.setLastUpdate(fileRecord.getUpdatedAt());
        dto.setAccessLevel(AccessLevelType.READ_WRITE);
        return dto;
    }

    // Mapping to dto is done by some user (might not be a file's owner)
    private Function<FileRecord,FileRecordDto> toDtoByUser(User currentUser) {
        return fileRecord -> {
            FileRecordDto dto = new FileRecordDto();
            dto.setId(fileRecord.getId());
            dto.setName(fileRecord.getName());
            dto.setType(fileRecord.getFileType());
            dto.setSize(fileRecord.getSize());
            dto.setLastUpdate(fileRecord.getUpdatedAt());
            if (!fileRecord.getOwner().equals(currentUser))
                dto.setAccessLevel(AccessLevelType.READ_ONLY);
            else
                dto.setAccessLevel(AccessLevelType.READ_WRITE);
            return dto;
        };
    }

    private Edge toEdge(User owner, FileRecord parent, FileRecord ancestor, FileRecord descendant) {
        Edge edge = new Edge();
        edge.setId(UUID.randomUUID().toString());
        edge.setAncestor(ancestor);
        edge.setDescendant(descendant);
        edge.setEdgeOwner(owner);
        if (ancestor.equals(parent)) {
            edge.setEdgeType(EdgeType.DIRECT);
        } else {
            edge.setEdgeType(EdgeType.INDIRECT);
        }
        return edge;
    }

    private Function<FileRecord, AccessLevel> toAccessLevel(AccessLevel parentAccessLevel) {
        return file -> {
            AccessLevel accessLevel = new AccessLevel();
            accessLevel.setId(UUID.randomUUID().toString());
            accessLevel.setLevel(parentAccessLevel.getLevel());
            accessLevel.setUser(parentAccessLevel.getUser());
            accessLevel.setFile(file);
            return accessLevel;
        };
    }

    private Function<AccessLevel, AccessLevel> toAccessLevel(FileRecord file) {
        return parentAccessLevel -> {
            AccessLevel accessLevel = new AccessLevel();
            accessLevel.setId(UUID.randomUUID().toString());
            accessLevel.setLevel(parentAccessLevel.getLevel());
            accessLevel.setUser(parentAccessLevel.getUser());
            accessLevel.setFile(file);
            return accessLevel;
        };
    }

    private Predicate<String> notSrcAndDest(String srcId, String destId) {
        return fileId -> !srcId.equals(fileId) && !destId.equals(fileId);
    }

    private FileRecordDetailsDto toDetailsDto(FileRecord fileRecord) {
        FileRecordDetailsDto detailsDto = new FileRecordDetailsDto();

        detailsDto.setName(fileRecord.getName());
        detailsDto.setExt(fileRecord.getExtension());
        detailsDto.setSize(fileRecord.getSize());
        detailsDto.setCreatedAt(fileRecord.getCreatedAt());
        detailsDto.setUpdatedAt(fileRecord.getUpdatedAt());
        detailsDto.setOwner(toUserDto(fileRecord.getOwner()));

        return detailsDto;
    }

    private UserDto toUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setUsername(user.getUsername());
        return dto;
    }
}
