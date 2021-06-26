package edu.aidana.todoapp.util;

/*
*  Encapsulates all the interactions with an actual file system.
* */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class FileSystemUtil {

    @Value("${file.storage.dir}")
    private String root;

    @Value("/home/user/work/tmp")
    private String tmpDir;

    public void createUserRootFolder(String username) throws IOException {

        Path path = Paths.get(root, username);
        Files.createDirectories(path);
    }

    public List<MultipartFileDecorator> saveFile(String path, List<MultipartFileDecorator> files) {

        List<MultipartFileDecorator> savedFiles = new ArrayList<>();

        for(MultipartFileDecorator file : files) {
            try {
                Path dest = Files.createFile(Paths.get(root, path, file.getName()));
                file.getMultipartFile().transferTo(dest);
                savedFiles.add(file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return savedFiles;
    }

    public File serveFile(String path, String fileId, String extenstion) {

        Path src = Paths.get(root, path, fileId);
        if(!Files.exists(src)) {
            throw new RuntimeException("File not found, throw some appropriate exception here and handle it");
        }

        return src.toFile();
    }

    public File compressAndReturnFiles(List<TreeNode> nodes) throws IOException {

        byte[] buffer = new byte[8 * 1024 * 1024]; // 8 Mb
        String zipFileName = UUID.randomUUID().toString() + ".zip";
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(Paths.get(tmpDir, zipFileName).toString()));

        compressAndReturnFilesRec("", nodes, zos, buffer);

        zos.close();

        return Paths.get(tmpDir, zipFileName).toFile();
    }

    private void compressAndReturnFilesRec(String path, List<? extends TreeNode> nodes, ZipOutputStream zos, byte[] buffer) throws IOException {

        // If folder is empty and it isn't a "root" folder, then we create an empty folder
        if(nodes.isEmpty() && !path.isEmpty()) {
            ZipEntry zipEntry = new ZipEntry(path);
            zos.putNextEntry(zipEntry);
            zos.closeEntry();
            return;
        }

        for (TreeNode node : nodes) {
            if(node.getType().equals(TreeNode.Type.FILE)) {

                FileTreeNode fileTreeNode = (FileTreeNode) node;
                FileInputStream fis = new FileInputStream(Paths.get(root, fileTreeNode.getOwnerName(), fileTreeNode.getId()).toString());

                ZipEntry zipEntry = new ZipEntry(path + fileTreeNode.getName());
                zos.putNextEntry(zipEntry);

                int bytesRead;
                while((bytesRead = fis.read(buffer)) >= 0)
                    zos.write(buffer, 0, bytesRead);

                zos.closeEntry();
                fis.close();

            } else {
                FolderTreeNode folderTreeNode = (FolderTreeNode) node;
                compressAndReturnFilesRec(path + folderTreeNode.getName() + "/", folderTreeNode.getSubnodes(), zos, buffer);
            }
        }
    }

}
