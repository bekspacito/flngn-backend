package edu.myrza.todoapp.service;

import edu.myrza.todoapp.exceptions.SystemException;
import edu.myrza.todoapp.model.dto.files.FileRecordDto;
import edu.myrza.todoapp.model.entity.*;
import edu.myrza.todoapp.model.enums.AccessLevelType;
import edu.myrza.todoapp.model.enums.EdgeType;
import edu.myrza.todoapp.model.enums.FileType;
import edu.myrza.todoapp.repos.AccessLevelRepository;
import edu.myrza.todoapp.repos.EdgeRepository;
import edu.myrza.todoapp.repos.FileRepository;
import edu.myrza.todoapp.repos.StatusRepository;
import edu.myrza.todoapp.util.*;
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
    private final StatusRepository statusRepository;
    private final FileRepository fileRepo;
    private final EdgeRepository edgeRepository;
    private final AccessLevelRepository accessLevelRepo;

    @Autowired
    public FileService(
            FileSystemUtil fileSystemUtil,
            StatusRepository statusRepository,
            FileRepository fileRepo,
            EdgeRepository edgeRepository,
            AccessLevelRepository accessLevelRepo)
    {
        this.fileSystemUtil = fileSystemUtil;
        this.statusRepository = statusRepository;
        this.fileRepo = fileRepo;
        this.edgeRepository = edgeRepository;
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

            Status enabled = statusRepository.findByCode(Status.Code.ENABLED);

            // Save a record about the created root folder in db
            FileRecord rootFolderRecord = FileRecord.createFolder(rootFolderName, rootFolderName, user, enabled);

            return toDtoByOwner(fileRepo.save(rootFolderRecord));
        } catch (IOException ex) {
            throw new SystemException(ex, "Error creating a root folder for a user [" + user.getUsername() + "]");
        }
    }

    // FOLDER/FILE OPERATIONS

    @Transactional
    public void deleteFiles(User user, List<String> ids) {

        Status deleted = statusRepository.findByCode(Status.Code.DELETED);

        for(String id : ids) {

            Optional<FileRecord> optFile = fileRepo.findById(id);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();

            // mark the file as 'deleted'
            file.setStatus(deleted);

            // if the file is 'file' then just save it and continue
            if(!file.getFileType().equals(FileType.FOLDER)) {
                fileRepo.save(file);
                continue;
            }

            // if the file is a folder then mark it's sub folders/files as 'deleted'
            Set<FileRecord> descendants = edgeRepository.serveAllDescendants(file.getId());
            for(FileRecord descendant : descendants) {
                descendant.setStatus(deleted);
            }

            descendants.add(file);
            fileRepo.saveAll(descendants);

        }

    }

    @Transactional
    public Optional<FileRecordDto> renameFile(User user, String fileId, String newName) {
        Optional<FileRecord> optFileRecord = fileRepo.findById(fileId);
        if(optFileRecord.isPresent()) {
            FileRecord fileRecord = optFileRecord.get();
            fileRecord.setName(newName);
            fileRepo.save(fileRecord);
            return Optional.of(toDtoByOwner(fileRecord));
        }

        return Optional.empty();
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
        List<TreeNode> nodes = buildTree(user, filesToDownload);

        // use the tree to create appropriate .zip file
        File compressedFile = fileSystemUtil.compressAndReturnFiles(user.getUsername(), nodes);

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

        Set<FileRecord> srcAncestors = Utils.append(edgeRepository.serveOwnedAncestors(srcId), src);
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

        Set<FileRecord> newAncestors = Utils.append(edgeRepository.serveOwnedAncestors(destFolder.getId()), destFolder);
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
                edgeRepository.deleteByDescendant(file);
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
                Set<FileRecord> currentAncestors = edgeRepository.serveOwnedAncestors(file.getId());

                // 1.2 Fetch all of the descendants + add the folder itself
                Set<FileRecord> descendants = Utils.append(edgeRepository.serveAllDescendants(file.getId()), file);

                // 1.3 Delete every edge between 'currentAncestors' and 'descendants'
                edgeRepository.deleteByAncestorInAndDescendantIn(currentAncestors, descendants);

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
            edgeRepository.saveAll(allNewEdges);
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

        List<Edge> edges = edgeRepository.serveEdges(user.getUsername(), FileType.FOLDER, Arrays.asList(EdgeType.DIRECT, EdgeType.INDIRECT));
        Queue<FolderTreeNode> queue = new ArrayDeque<>();

        FolderTreeNode root = new FolderTreeNode(user.getUsername(), user.getUsername());

        queue.add(root);
        while(!queue.isEmpty()) {
            FolderTreeNode currentNode = queue.poll();
            // Direct sub nodes to 'currentNode'
            List<FolderTreeNode> directSubNodes = edges.stream()
                                                         .filter(predicate(user.getUsername(), currentNode))
                                                         .map(e -> new FolderTreeNode(e.getDescendant().getId(), e.getDescendant().getName()))
                                                         .peek(queue::add)
                                                         .collect(Collectors.toList());
            currentNode.setSubnodes(directSubNodes);
        }

        return root;
    }

    private Predicate<Edge> predicate(String username, FolderTreeNode currentNode) {
        return edge -> {
            if(currentNode.getId().equals(username) // given current node is a root
                    && edge.getAncestor().getId().equals(currentNode.getId()) // given current node is ancestor
                    && edge.getEdgeType().equals(EdgeType.DIRECT)) // descendant is direct
                return true;
            return edge.getAncestor().getId().equals(currentNode.getId());
        };
    }

    @Transactional
    public FileRecordDto createFolder(User user, String parentId, String folderName) {

        Status enabled = statusRepository.findByCode(Status.Code.ENABLED);

        // First we create folderRecord
        FileRecord folderRecord = FileRecord.createFolder(UUID.randomUUID().toString(), folderName, user, enabled);

        FileRecord savedFolderRecord = fileRepo.save(folderRecord);

        // Then we create edges
        // access all of the ancestors of 'parent' folderRecord and save new edges
        Set<Edge> ancestorsEdges = edgeRepository.serveAncestors(parentId).stream()
                .map(ancestor -> new Edge(UUID.randomUUID().toString(), ancestor, savedFolderRecord, EdgeType.INDIRECT, user))
                .collect(Collectors.toSet());

        // access 'parent' folder
        Optional<FileRecord> optParent = fileRepo.findById(parentId);
        Edge parentEdge = optParent.map(parent -> new Edge(UUID.randomUUID().toString(), parent, savedFolderRecord, EdgeType.DIRECT, user))
                                    .orElseThrow(() -> new RuntimeException("No folderRecord with id [" + parentId + "] is found"));

        ancestorsEdges.add(parentEdge);

        edgeRepository.saveAll(ancestorsEdges);

        // Assign an access level the parent folder has to new files
        List<FileRecord> finalFileRecords = Collections.singletonList(savedFolderRecord);
        List<AccessLevel> readOnlyAccessLevel = accessLevelRepo.findAllByFile(optParent.get()).stream()
                                                                .flatMap(parentAccessLevel -> finalFileRecords.stream().map(toAccessLevel(parentAccessLevel)))
                                                                .collect(Collectors.toList());
        accessLevelRepo.saveAll(readOnlyAccessLevel);

        return toDtoByOwner(savedFolderRecord);
    }

    @Transactional(readOnly = true)
    public List<FileRecordDto> serveFolderContent(User user, String folderId) {

        Optional<FileRecord> optFolder = fileRepo.findById(folderId).filter(checkFileAccess(user));
        if(!optFolder.isPresent())
            return Collections.emptyList();

        return edgeRepository.serveDescendants(folderId, Arrays.asList(EdgeType.DIRECT, EdgeType.SHARED)).stream()
                             .filter(checkFileAccess(user))
                             .map(toDtoByUser(user))
                             .collect(Collectors.toList());
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
        Set<FileRecord> ancestors = Utils.append(edgeRepository.serveOwnedAncestors(folderId), parent);

        List<Edge> edges = fileRecords.stream()
                                        .flatMap(descendant -> ancestors.stream().map(ancestor -> toEdge(user,parent,ancestor,descendant)))
                                        .collect(Collectors.toList());

        edgeRepository.saveAll(edges);

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

        File file = fileSystemUtil.serveFile(user.getUsername(), fileId, fileRecord.getExtension());
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
        return edgeRepository.serveAllDescendants(folder.getId());
    }

    void saveEdges(List<Edge> edges) {
        edgeRepository.saveAll(edges);
    }

    private List<TreeNode> buildTree(User user, List<FileRecord> files) {
        List<TreeNode> nodes = new ArrayList<>();

        for(FileRecord file : files) {

            if(file.getFileType().equals(FileType.FILE)) {
                FileTreeNode treeNode = new FileTreeNode();
                treeNode.setId(file.getId());
                treeNode.setType(TreeNode.Type.FILE);
                treeNode.setName(file.getName());
                nodes.add(treeNode);
                continue;
            }

            if(file.getFileType().equals(FileType.FOLDER)) {
                FolderTreeNode folderTreeNode = new FolderTreeNode();
                folderTreeNode.setId(file.getId());
                folderTreeNode.setName(file.getName());
                folderTreeNode.setType(TreeNode.Type.FOLDER);

                List<FileRecord> subFiles = edgeRepository.serveDescendants(file.getId(), EdgeType.DIRECT);

                folderTreeNode.setSubnodes(buildTree(user, subFiles));
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

        Status enabled = statusRepository.findByCode(Status.Code.ENABLED);

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
}
