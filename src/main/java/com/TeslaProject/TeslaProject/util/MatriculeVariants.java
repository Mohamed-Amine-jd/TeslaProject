package com.TeslaProject.TeslaProject.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tunisian-style plates are often stored as {@code NNNtuMMM} or {@code MMMtuNNN}; OCR / users may send either form.
 */
public final class MatriculeVariants {

    private static final Pattern PLATE = Pattern.compile("^(\\d+)([a-z]+)(\\d+)$");

    private MatriculeVariants() {}

    /** Normalized plate plus numeric swap (e.g. 190tun765 and 765tun190) for Mongo lookup. */
    public static List<String> aliases(String normalized) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (normalized != null && !normalized.isBlank()) {
            set.add(normalized.trim());
            Matcher m = PLATE.matcher(normalized.trim());
            if (m.matches()) {
                String swapped = m.group(3) + m.group(2) + m.group(1);
                set.add(swapped);
            }
        }
        return new ArrayList<>(set);
    }
}
