package edu.aidana.todoapp.util;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class FolderTreeNode extends TreeNode {

    private String id;
    private String name;
    private List<? extends TreeNode> subnodes = new ArrayList<>();

    public FolderTreeNode(String id, String name) {
        setType(Type.FOLDER);
        this.id = id;
        this.name = name;
    }
}
