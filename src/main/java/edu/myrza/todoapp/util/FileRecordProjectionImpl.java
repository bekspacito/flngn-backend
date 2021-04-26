package edu.myrza.todoapp.util;

public class FileRecordProjectionImpl implements FileRecordProjection {

    private final String id;
    private final String name;
    private final String fileType;

    public FileRecordProjectionImpl(String id, String name, String fileType) {
        this.id = id;
        this.name = name;
        this.fileType = fileType;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFiletype() {
        return fileType;
    }
}
