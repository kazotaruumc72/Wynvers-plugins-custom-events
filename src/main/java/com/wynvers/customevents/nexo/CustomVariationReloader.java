package com.wynvers.customevents.nexo;

import com.wynvers.customevents.WynversCustomEvents;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Detects items that share the same {@code custom_variation} (under
 * {@code Mechanics.custom_block}) and reassigns the duplicates to free
 * variations. Files are rewritten line-by-line so YAML comments,
 * indentation and key ordering are preserved.
 */
public class CustomVariationReloader {

    // NOTEBLOCK custom_block type allows up to 1149 variations (one per
    // blockstate), so reassignments draw from the [0, 1149) pool.
    private static final int MAX_VARIATION = 1149;

    private final WynversCustomEvents plugin;

    public CustomVariationReloader(WynversCustomEvents plugin) {
        this.plugin = plugin;
    }

    public ReloadResult reload(File nexoItemsDir) {
        ReloadResult result = new ReloadResult();

        if (nexoItemsDir == null || !nexoItemsDir.isDirectory()) {
            result.error = "Nexo items directory not found: " +
                    (nexoItemsDir != null ? nexoItemsDir.getAbsolutePath() : "null");
            return result;
        }

        Map<Integer, List<ItemLocation>> usedVariations = new TreeMap<>();
        scanDirectory(nexoItemsDir, usedVariations);

        Set<Integer> usedSet = new HashSet<>(usedVariations.keySet());
        Deque<Integer> freePool = new ArrayDeque<>();
        for (int i = 0; i < MAX_VARIATION; i++) {
            if (!usedSet.contains(i)) freePool.add(i);
        }

        Map<File, List<Reassignment>> changesByFile = new HashMap<>();

        // Tiebreak: oldest file wins (its item is most likely already
        // placed on the live server, so keeping its variation avoids
        // having to rebuild existing builds). Within the same file,
        // earlier lines win.
        Comparator<ItemLocation> oldestFirst = Comparator
                .comparingLong((ItemLocation l) -> l.file.lastModified())
                .thenComparingInt(l -> l.lineIndex);

        for (Map.Entry<Integer, List<ItemLocation>> entry : usedVariations.entrySet()) {
            List<ItemLocation> items = entry.getValue();
            if (items.size() <= 1) continue;

            items.sort(oldestFirst);

            int oldVariation = entry.getKey();
            // Keep the oldest occurrence; reassign the rest.
            for (int i = 1; i < items.size(); i++) {
                if (freePool.isEmpty()) {
                    result.error = "Not enough free variations to fix all duplicates.";
                    return result;
                }
                int newVar = freePool.poll();
                ItemLocation loc = items.get(i);
                changesByFile.computeIfAbsent(loc.file, k -> new ArrayList<>())
                        .add(new Reassignment(loc, newVar));
                result.reassignments.add(new ReassignmentInfo(loc.itemId, oldVariation, newVar));
            }
        }

        for (Map.Entry<File, List<Reassignment>> e : changesByFile.entrySet()) {
            try {
                applyChanges(e.getKey(), e.getValue());
                result.filesModified++;
            } catch (IOException ex) {
                plugin.getLogger().warning("[CustomVariationReloader] Failed to write "
                        + e.getKey().getName() + ": " + ex.getMessage());
                result.error = "Failed to write " + e.getKey().getName() + ": " + ex.getMessage();
                return result;
            }
        }

        return result;
    }

    private void scanDirectory(File dir, Map<Integer, List<ItemLocation>> usedVariations) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                scanDirectory(child, usedVariations);
            } else {
                String name = child.getName().toLowerCase();
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    scanFile(child, usedVariations);
                }
            }
        }
    }

    private void scanFile(File file, Map<Integer, List<ItemLocation>> usedVariations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("[CustomVariationReloader] Failed to read "
                    + file.getName() + ": " + e.getMessage());
            return;
        }

        String currentItem = null;
        Deque<String> sectionStack = new ArrayDeque<>();
        Deque<Integer> indentStack = new ArrayDeque<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            int indent = countLeadingSpaces(line);

            while (!indentStack.isEmpty() && indentStack.peek() >= indent) {
                indentStack.pop();
                sectionStack.pop();
            }

            int colon = trimmed.indexOf(':');
            if (colon < 0) continue;

            String key = trimmed.substring(0, colon).trim();
            String rawAfter = trimmed.substring(colon + 1);
            String value = stripInlineComment(rawAfter).trim();

            if (indent == 0) {
                currentItem = key;
                sectionStack.clear();
                indentStack.clear();
                if (value.isEmpty()) continue;
                continue;
            }

            if (currentItem == null) continue;

            if (key.equals("custom_variation") && !value.isEmpty()
                    && isInsideCustomBlock(sectionStack)) {
                try {
                    int variation = Integer.parseInt(value);
                    usedVariations.computeIfAbsent(variation, k -> new ArrayList<>())
                            .add(new ItemLocation(file, currentItem, i));
                } catch (NumberFormatException ignored) {
                }
            }

            if (value.isEmpty()) {
                sectionStack.push(key);
                indentStack.push(indent);
            }
        }
    }

    private boolean isInsideCustomBlock(Deque<String> stack) {
        if (stack.size() < 2) return false;
        Iterator<String> it = stack.descendingIterator();
        String outer = it.next();
        String inner = it.next();
        return outer.equalsIgnoreCase("Mechanics") && inner.equalsIgnoreCase("custom_block");
    }

    private void applyChanges(File file, List<Reassignment> reassignments) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        for (Reassignment r : reassignments) {
            String oldLine = lines.get(r.location.lineIndex);
            int colon = oldLine.indexOf(':');
            if (colon < 0) continue;

            String beforeColon = oldLine.substring(0, colon + 1);
            String afterColon = oldLine.substring(colon + 1);

            int hashIdx = findCommentIndex(afterColon);
            String comment = hashIdx >= 0 ? afterColon.substring(hashIdx) : "";

            StringBuilder rebuilt = new StringBuilder();
            rebuilt.append(beforeColon).append(' ').append(r.newVariation);
            if (!comment.isEmpty()) {
                rebuilt.append("  ").append(comment);
            }
            lines.set(r.location.lineIndex, rebuilt.toString());
        }
        Files.write(file.toPath(), lines);
    }

    private static int countLeadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') n++;
        return n;
    }

    private static String stripInlineComment(String s) {
        int idx = findCommentIndex(s);
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    private static int findCommentIndex(String s) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '#' && !inSingle && !inDouble) {
                if (i == 0 || Character.isWhitespace(s.charAt(i - 1))) return i;
            }
        }
        return -1;
    }

    private static class ItemLocation {
        final File file;
        final String itemId;
        final int lineIndex;

        ItemLocation(File file, String itemId, int lineIndex) {
            this.file = file;
            this.itemId = itemId;
            this.lineIndex = lineIndex;
        }
    }

    private static class Reassignment {
        final ItemLocation location;
        final int newVariation;

        Reassignment(ItemLocation location, int newVariation) {
            this.location = location;
            this.newVariation = newVariation;
        }
    }

    public static class ReassignmentInfo {
        public final String itemId;
        public final int oldVariation;
        public final int newVariation;

        public ReassignmentInfo(String itemId, int oldVariation, int newVariation) {
            this.itemId = itemId;
            this.oldVariation = oldVariation;
            this.newVariation = newVariation;
        }
    }

    public static class ReloadResult {
        public final List<ReassignmentInfo> reassignments = new ArrayList<>();
        public int filesModified = 0;
        public String error = null;

        public boolean hasError() {
            return error != null;
        }
    }
}