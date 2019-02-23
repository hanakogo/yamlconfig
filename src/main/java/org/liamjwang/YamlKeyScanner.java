package org.liamjwang;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

public class YamlKeyScanner {

    public static final char PATH_SEPARATOR = '/';

    private Map<String, Set<ConfigEntry>> possibleKeysMap = new HashMap<>(); // maps paths to possible keys

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

    public Set<ConfigEntry> getYamlConfigKeys() {
        return possibleKeysMap.keySet().stream().flatMap(k -> possibleKeysMap.get(k).stream()).collect(Collectors.toSet());
    }


    private void indexFile(VirtualFile file) {
        if (file == null) {
            return;
        }
        if (!FilenameUtils.isExtension(file.getName(), "yaml")) {
            return;
        }
        possibleKeysMap.put(file.getPath(), new HashSet<>()); // Clear or create a new set (delete old values)
        Set<ConfigEntry> possibleKeySet = possibleKeysMap.get(file.getPath());
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

    private void traverseKeyMap(String baseString, Map<String, Object> map, Set<ConfigEntry> possibleKeySet) {
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
                    possibleKeySet.add(new ConfigEntry(normalizedKey, obj));
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
        public Object item;

        public ConfigEntry(String path, Object item) {
            this.path = path;
            this.item = item;
        }
    }
}
