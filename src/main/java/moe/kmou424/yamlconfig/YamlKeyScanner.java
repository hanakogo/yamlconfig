package moe.kmou424.yamlconfig;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

public class YamlKeyScanner {

    public static final char PATH_SEPARATOR = '/';

    private Map<String, List<ConfigEntry>> possibleKeysMap = new HashMap<>(); // maps paths to possible keys

    public YamlKeyScanner(String rootPath) {
        try {
            Files.walk(Paths.get(rootPath)).filter(Files::isRegularFile).forEach(file -> {
                indexFile(LocalFileSystem.getInstance().findFileByIoFile(file.toFile()));
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void contentsChanged(@NotNull VirtualFileEvent event) {
                indexFile(event.getFile());
            }

            @Override
            public void fileDeleted(@NotNull VirtualFileEvent event) {
                possibleKeysMap.remove(event.getFile().getPath());
            }

            @Override
            public void fileMoved(@NotNull VirtualFileMoveEvent event) {
                String oldPath = event.getOldParent().getPath() + PATH_SEPARATOR + event.getFileName();
                String newPath = event.getFile().getPath();
                possibleKeysMap.put(newPath, possibleKeysMap.remove(oldPath));
            }
        });
    }

    private List<ConfigEntry> expandSet(List<ConfigEntry> original) {
        List<ConfigEntry> expanded = new ArrayList<>();
        for (ConfigEntry configEntry : original) {
            expanded.add(configEntry);
            String newPath = configEntry.path;
            int idx = newPath.indexOf("/");
            while (idx > 0) {
                String newItem = newPath.substring(0, idx) + (configEntry.item == "" ? "" : ": " + configEntry.item.toString());
                expanded.add(new ConfigEntry(newPath.substring(idx + 1), newItem));
                idx = newPath.indexOf("/", idx + 1);
            }
        }
        return expanded;
    }

    public Collection<ConfigEntry> getYamlConfigKeys() {
        return combineKeys(expandSet(flattenMap()));
    }

    public Collection<ConfigEntry> combineKeys(List<ConfigEntry> entries) {
        Map<String, ConfigEntry> result = new HashMap<>();
        entries.forEach(e -> {
            e.path = e.path.replaceAll("/", ".");
            if (!result.containsKey(e.path)) {
                result.put(e.path, e);
            } else {
                ConfigEntry other = result.get(e.path);
                String newItem = other.item + (e.item.equals("") || other.item.equals("") ? "" : " | ") + e.item;
                result.put(e.path, new ConfigEntry(e.path, newItem));
            }
        });

        return result.values();
    }


    private List<ConfigEntry> flattenMap() {
        return possibleKeysMap.keySet().stream().flatMap(k -> possibleKeysMap.get(k).stream()).collect(Collectors.toList());
    }


    private void indexFile(VirtualFile file) {
        if (file == null) {
            return;
        }
        if (!FilenameUtils.isExtension(file.getName(), "yaml")) {
            return;
        }
        possibleKeysMap.put(file.getPath(), new ArrayList<>()); // Clear or create a new set (delete old values)
        List<ConfigEntry> possibleKeySet = possibleKeysMap.get(file.getPath());
        Yaml yaml = new Yaml();
        try {
            InputStream input = file.getInputStream();
            Map<String, Object> map = yaml.load(input);
            traverseKeyMap("", map, possibleKeySet);
        } catch (Exception e) {
            System.out.println("[Yamlconfig-Idea]: Unable to parse YAML file: ");
            e.printStackTrace();
        }
    }

    private void traverseKeyMap(String baseString, Map<String, Object> map, List<ConfigEntry> possibleKeySet) {
        try {
            for (Entry<String, Object> entry : map.entrySet()) {
                String str = entry.getKey();
                Object obj = entry.getValue();
                if (obj instanceof Map) {
                    String normalizedKey = normalizePathStandard(baseString + PATH_SEPARATOR + str);
                    possibleKeySet.add(new ConfigEntry(normalizedKey, ""));
                    traverseKeyMap(normalizedKey, (Map<String, Object>) obj, possibleKeySet);
                } else if (obj instanceof Number || obj instanceof String) {
                    String normalizedKey = normalizePathStandard(baseString + PATH_SEPARATOR + str);
                    possibleKeySet.add(new ConfigEntry(normalizedKey, obj.toString()));
                } else {
                    System.out.println("[Yamlconfig-Idea]: YAML contains object of unknown type: " + obj.getClass());
                }
            }
        } catch (NullPointerException ignored) {
        }
    }

    /**
     * @param path the path to normalize
     * @param withLeadingSlash whether or not the normalized key should begin with a leading slash
     * @return normalized key
     */
    public static String normalizePath(String path, boolean withLeadingSlash, boolean removeTrailingSlash) {
        if (path.equals("")) {
            return "";
        }

        String normalized;
        if (withLeadingSlash) {
            normalized = PATH_SEPARATOR + path;
        } else {
            normalized = path;
        }
        normalized = normalized.replaceAll(PATH_SEPARATOR + "{2,}", String.valueOf(PATH_SEPARATOR));

        if (!withLeadingSlash && normalized.charAt(0) == PATH_SEPARATOR) {
            normalized = normalized.substring(1);
        }

        if (removeTrailingSlash && normalized.charAt(normalized.length() - 1) == PATH_SEPARATOR) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static String normalizePathStandard(String path) {
        return normalizePath(path, false, true);
    }

    public class ConfigEntry {

        public String path;
        public String item;

        public ConfigEntry(String path, String item) {
            this.path = path;
            this.item = item;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ConfigEntry)) {
                return false;
            }
            ConfigEntry otherConfig = (ConfigEntry) other;
            return path.equals(otherConfig.path) && item.equals(otherConfig.item);
        }
    }
}
