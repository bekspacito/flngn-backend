package edu.myrza.todoapp.util;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class TreeNode {

    public enum Type { FILE, FOLDER };

    private Type type;
    private String ownerName;

}
