package edu.aidana.todoapp.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {

    public static <T> List<T> append(List<T> list, T ... vals) {

        if(list == null)
            return Arrays.asList(vals);

        list.addAll(Arrays.asList(vals));
        return list;
    }

    public static <T> Set<T> append(Set<T> set, T ... vals) {

        if(set == null) {
            return new HashSet<>(Arrays.asList(vals));
        }

        set.addAll(Arrays.asList(vals));
        return set;
    }

}
