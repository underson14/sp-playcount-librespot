package xyz.gianlu.librespot;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UrlParse {
    public Map<String, List<String>> parse(final String query) {
        return Arrays.asList(query.split("&")).stream().map(p -> p.split("=")).collect(Collectors.toMap(s -> decode(index(s, 0)), s -> Arrays.asList(decode(index(s, 1))), this::mergeLists));
    }

    private <T> List<T> mergeLists(final List<T> l1, final List<T> l2) {
        List<T> list = new ArrayList<>();
        list.addAll(l1);
        list.addAll(l2);
        return list;
    }

    private <T> T index(final T[] array, final int index) {
        return index >= array.length ? null : array[index];
    }

    private String decode(final String encoded) {
        try {
            return encoded == null ? null : URLDecoder.decode(encoded, "UTF-8");
        } catch(final UnsupportedEncodingException e) {
            throw new RuntimeException("Impossible: UTF-8 is a required encoding", e);
        }
    }

}
