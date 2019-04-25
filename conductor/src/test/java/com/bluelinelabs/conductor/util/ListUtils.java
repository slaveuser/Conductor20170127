package com.bluelinelabs.conductor.util;

import java.util.ArrayList;
import java.util.List;

public class ListUtils {

    public static <T> List<T> listOf(T... elements) {
        List<T> list = new ArrayList<>();
        for (T element : elements) {
            list.add(element);
        }
        return list;
    }

}
