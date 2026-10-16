package com.platform.service;

import com.platform.entity.I18nDictionary;
import com.platform.repository.I18nDictionaryRepository;
import org.springframework.stereotype.Service;

<<<<<<< HEAD
<<<<<<< HEAD
import java.time.Instant;
import java.util.*;
=======
import java.util.Optional;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
import java.util.Optional;
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92

@Service
public class I18nService {

<<<<<<< HEAD
<<<<<<< HEAD
    private static final Map<String, String[]> DEFAULT_LOCALE_META = Map.of(
            "da", new String[]{"Dansk", "🇩🇰"},
            "en", new String[]{"English", "🇬🇧"},
            "de", new String[]{"Deutsch", "🇩🇪"},
            "es", new String[]{"Español", "🇪🇸"},
            "fr", new String[]{"Français", "🇫🇷"},
            "sv", new String[]{"Svenska", "🇸🇪"},
            "no", new String[]{"Norsk", "🇳🇴"},
            "nl", new String[]{"Nederlands", "🇳🇱"},
            "it", new String[]{"Italiano", "🇮🇹"},
            "pl", new String[]{"Polski", "🇵🇱"}
    );

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    private final I18nDictionaryRepository repository;

    public I18nService(I18nDictionaryRepository repository) {
        this.repository = repository;
    }

    public Optional<I18nDictionary> getDictionary(String locale) {
        return repository.findByLocale(locale);
    }

<<<<<<< HEAD
<<<<<<< HEAD
    public List<I18nDictionary> getAllDictionaries() {
        return repository.findAll();
    }

    @SuppressWarnings("unchecked")
    public I18nDictionary updateTranslation(String locale, String key, String value) {
        I18nDictionary dict = repository.findByLocale(locale)
                .orElseGet(() -> {
                    String[] meta = DEFAULT_LOCALE_META.getOrDefault(locale, new String[]{locale.toUpperCase(), "🌐"});
                    return new I18nDictionary(locale, meta[0], meta[1], true, new HashMap<>());
                });

        if (dict.getTranslations() == null) {
            dict.setTranslations(new HashMap<>());
        }

        Map<String, Object> root = dict.getTranslations();
        if (key != null && key.contains(".")) {
            // Also remove flat dotted key if it existed previously
            root.remove(key);
            String[] parts = key.split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                Object next = current.get(part);
                if (!(next instanceof Map)) {
                    Map<String, Object> child = new LinkedHashMap<>();
                    current.put(part, child);
                    current = child;
                } else {
                    current = (Map<String, Object>) next;
                }
            }
            current.put(parts[parts.length - 1], value);
        } else if (key != null) {
            root.put(key, value);
        }

        dict.setUpdatedAt(Instant.now());
        return repository.save(dict);
    }

    public I18nDictionary deleteTranslationKey(String locale, String key) {
        I18nDictionary dict = repository.findByLocale(locale)
                .orElseThrow(() -> new IllegalArgumentException("Locale not found: " + locale));
        if (dict.getTranslations() != null && key != null) {
            removeNestedKey(dict.getTranslations(), key);
        }
        dict.setUpdatedAt(Instant.now());
        return repository.save(dict);
    }

    @SuppressWarnings("unchecked")
    private void removeNestedKey(Map<String, Object> root, String key) {
        root.remove(key);
        if (!key.contains(".")) {
            return;
        }
        String[] parts = key.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return;
            }
        }
        current.remove(parts[parts.length - 1]);
    }

    public I18nDictionary createOrUpdateLocale(
            String locale,
            String displayName,
            String flag,
            Boolean published,
            String cloneFromLocale,
            boolean copyValues
    ) {
        String code = locale.trim().toLowerCase();
        String[] defaultMeta = DEFAULT_LOCALE_META.getOrDefault(code, new String[]{code.toUpperCase(), "🌐"});

        I18nDictionary dict = repository.findByLocale(code)
                .orElseGet(() -> new I18nDictionary(
                        code,
                        (displayName != null && !displayName.isBlank()) ? displayName.trim() : defaultMeta[0],
                        (flag != null && !flag.isBlank()) ? flag.trim() : defaultMeta[1],
                        published != null ? published : false,
                        new LinkedHashMap<>()
                ));

        if (displayName != null && !displayName.isBlank()) {
            dict.setDisplayName(displayName.trim());
        } else if (dict.getDisplayName() == null || dict.getDisplayName().isBlank()) {
            dict.setDisplayName(defaultMeta[0]);
        }

        if (flag != null && !flag.isBlank()) {
            dict.setFlag(flag.trim());
        } else if (dict.getFlag() == null || dict.getFlag().isBlank()) {
            dict.setFlag(defaultMeta[1]);
        }

        if (published != null) {
            dict.setPublished(published);
        }

        if (cloneFromLocale != null && !cloneFromLocale.isBlank() && copyValues) {
            repository.findByLocale(cloneFromLocale.trim().toLowerCase()).ifPresent(sourceDict -> {
                Map<String, String> sourceFlat = flattenMap(sourceDict.getTranslations(), "");
                Map<String, String> targetFlat = flattenMap(dict.getTranslations(), "");
                for (Map.Entry<String, String> entry : sourceFlat.entrySet()) {
                    String existing = targetFlat.get(entry.getKey());
                    if (existing == null || existing.isBlank()) {
                        updateNestedMap(dict.getTranslations(), entry.getKey(), entry.getValue());
                    }
                }
            });
        }

        dict.setUpdatedAt(Instant.now());
        return repository.save(dict);
    }

    public I18nDictionary replaceDictionaryTranslations(String locale, Map<String, Object> translations) {
        String code = locale.trim().toLowerCase();
        String[] defaultMeta = DEFAULT_LOCALE_META.getOrDefault(code, new String[]{code.toUpperCase(), "🌐"});
        I18nDictionary dict = repository.findByLocale(code)
                .orElseGet(() -> new I18nDictionary(code, defaultMeta[0], defaultMeta[1], false, new LinkedHashMap<>()));
        dict.setTranslations(translations != null ? translations : new LinkedHashMap<>());
        dict.setUpdatedAt(Instant.now());
        return repository.save(dict);
    }

    public void deleteLocale(String locale) {
        repository.findByLocale(locale.trim().toLowerCase()).ifPresent(repository::delete);
    }

    public List<Map<String, Object>> getLocaleSummaries(String referenceLocale) {
        String refCode = (referenceLocale != null && !referenceLocale.isBlank()) ? referenceLocale.trim().toLowerCase() : "da";
        Optional<I18nDictionary> refDictOpt = repository.findByLocale(refCode);
        if (refDictOpt.isEmpty()) {
            refDictOpt = repository.findByLocale("en");
        }
        Map<String, String> refFlat = refDictOpt
                .map(d -> flattenMap(d.getTranslations(), ""))
                .orElseGet(Collections::emptyMap);
        int totalReferenceKeys = refFlat.size();

        List<I18nDictionary> all = repository.findAll();
        all.sort(Comparator.comparing(d -> d.getLocale() != null ? d.getLocale() : ""));

        List<Map<String, Object>> result = new ArrayList<>();
        for (I18nDictionary dict : all) {
            String code = dict.getLocale();
            if (code == null) continue;
            String[] defaultMeta = DEFAULT_LOCALE_META.getOrDefault(code, new String[]{code.toUpperCase(), "🌐"});
            Map<String, String> flat = flattenMap(dict.getTranslations(), "");

            int translatedRefCount = 0;
            List<String> missingKeys = new ArrayList<>();
            for (String refKey : refFlat.keySet()) {
                String val = flat.get(refKey);
                if (val != null && !val.trim().isEmpty()) {
                    translatedRefCount++;
                } else {
                    missingKeys.add(refKey);
                }
            }

            int totalKeys = Math.max(totalReferenceKeys, flat.size());
            int completionPct = totalReferenceKeys > 0
                    ? Math.min(100, (int) Math.round((translatedRefCount * 100.0) / totalReferenceKeys))
                    : (flat.isEmpty() ? 0 : 100);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("id", dict.getId());
            summary.put("locale", code);
            summary.put("displayName", (dict.getDisplayName() != null && !dict.getDisplayName().isBlank()) ? dict.getDisplayName() : defaultMeta[0]);
            summary.put("flag", (dict.getFlag() != null && !dict.getFlag().isBlank()) ? dict.getFlag() : defaultMeta[1]);
            summary.put("published", dict.getPublished() != null ? dict.getPublished() : true);
            summary.put("updatedAt", dict.getUpdatedAt() != null ? dict.getUpdatedAt().toString() : null);
            summary.put("translatedCount", translatedRefCount);
            summary.put("totalReferenceKeys", totalReferenceKeys);
            summary.put("totalKeysInDoc", flat.size());
            summary.put("missingCount", missingKeys.size());
            summary.put("completionPct", completionPct);
            result.add(summary);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> flattenMap(Map<String, Object> map, String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        if (map == null) return out;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            String fullKey = (prefix == null || prefix.isEmpty()) ? k : prefix + "." + k;
            if (v instanceof Map) {
                out.putAll(flattenMap((Map<String, Object>) v, fullKey));
            } else if (v != null) {
                out.put(fullKey, String.valueOf(v));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void updateNestedMap(Map<String, Object> root, String key, String value) {
        if (!key.contains(".")) {
            root.put(key, value);
            return;
        }
        String[] parts = key.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                Map<String, Object> child = new LinkedHashMap<>();
                current.put(parts[i], child);
                current = child;
            } else {
                current = (Map<String, Object>) next;
            }
        }
        current.put(parts[parts.length - 1], value);
    }
}

=======
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
    public I18nDictionary updateTranslation(String locale, String key, String value) {
        I18nDictionary dict = repository.findByLocale(locale)
                .orElse(new I18nDictionary(locale, new java.util.HashMap<>()));
        dict.getTranslations().put(key, value);
        return repository.save(dict);
    }
}
<<<<<<< HEAD
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
