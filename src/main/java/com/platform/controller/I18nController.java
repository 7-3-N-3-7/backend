package com.platform.controller;

<<<<<<< HEAD
import com.platform.entity.I18nDictionary;
import com.platform.service.I18nService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
=======
import com.platform.service.I18nService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d

@RestController
@RequestMapping("/api/v1/i18n")
@CrossOrigin(origins = "*")
public class I18nController {

    private final I18nService i18nService;

    public I18nController(I18nService i18nService) {
        this.i18nService = i18nService;
    }

<<<<<<< HEAD
    @GetMapping("/locales")
    public ResponseEntity<List<Map<String, Object>>> getLocales(
            @RequestParam(value = "ref", defaultValue = "da") String ref
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(i18nService.getLocaleSummaries(ref));
    }

    @PostMapping("/locales")
    public ResponseEntity<I18nDictionary> createOrUpdateLocale(@RequestBody Map<String, Object> payload) {
        String locale = String.valueOf(payload.getOrDefault("locale", "")).trim().toLowerCase();
        if (locale.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String displayName = payload.get("displayName") != null ? String.valueOf(payload.get("displayName")) : null;
        String flag = payload.get("flag") != null ? String.valueOf(payload.get("flag")) : null;
        Boolean published = payload.get("published") != null
                ? Boolean.parseBoolean(String.valueOf(payload.get("published")))
                : false;
        String cloneFromLocale = payload.get("cloneFromLocale") != null
                ? String.valueOf(payload.get("cloneFromLocale"))
                : null;
        boolean copyValues = payload.get("copyValues") != null
                && Boolean.parseBoolean(String.valueOf(payload.get("copyValues")));

        I18nDictionary saved = i18nService.createOrUpdateLocale(
                locale,
                displayName,
                flag,
                published,
                cloneFromLocale,
                copyValues
        );
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/locales/{locale}/status")
    public ResponseEntity<I18nDictionary> updateLocaleStatus(
            @PathVariable String locale,
            @RequestBody Map<String, Object> payload
    ) {
        Boolean published = payload.get("published") != null
                ? Boolean.parseBoolean(String.valueOf(payload.get("published")))
                : true;
        String displayName = payload.get("displayName") != null ? String.valueOf(payload.get("displayName")) : null;
        String flag = payload.get("flag") != null ? String.valueOf(payload.get("flag")) : null;
        I18nDictionary updated = i18nService.createOrUpdateLocale(
                locale,
                displayName,
                flag,
                published,
                null,
                false
        );
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/locales/{locale}")
    public ResponseEntity<Map<String, Object>> deleteLocale(@PathVariable String locale) {
        i18nService.deleteLocale(locale);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deletedLocale", locale);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/document/{locale}")
    public ResponseEntity<I18nDictionary> getRawDocument(@PathVariable String locale) {
        return i18nService.getDictionary(locale)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/document/{locale}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<I18nDictionary> replaceRawTranslations(
            @PathVariable String locale,
            @RequestBody Map<String, Object> payload
    ) {
        Map<String, Object> translations = payload.containsKey("translations") && payload.get("translations") instanceof Map
                ? (Map<String, Object>) payload.get("translations")
                : payload;
        return ResponseEntity.ok(i18nService.replaceDictionaryTranslations(locale, translations));
    }

    @GetMapping("/{locale}")
    public ResponseEntity<Map<String, Object>> getDictionary(@PathVariable String locale) {
        CacheControl cacheHeader = CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic();
        return i18nService.getDictionary(locale)
                .map(dict -> ResponseEntity.ok()
                        .cacheControl(cacheHeader)
                        .body(dict.getTranslations()))
                .orElseGet(() -> i18nService.getDictionary("en")
                        .map(fallback -> ResponseEntity.ok()
                                .cacheControl(cacheHeader)
=======
    @GetMapping("/{locale}")
    public ResponseEntity<Map<String, Object>> getDictionary(@PathVariable String locale) {
        return i18nService.getDictionary(locale)
                .map(dict -> ResponseEntity.ok(dict.getTranslations()))
                .orElseGet(() -> i18nService.getDictionary("en")
                        .map(fallback -> ResponseEntity.ok()
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
                                .header("X-Locale-Fallback", "en")
                                .body(fallback.getTranslations()))
                        .orElse(ResponseEntity.notFound().build()));
    }

    @PutMapping("/{locale}/{key}")
    public ResponseEntity<Void> updateTranslation(@PathVariable String locale,
                                                  @PathVariable String key,
                                                  @RequestBody TranslationUpdateRequest request) {
        i18nService.updateTranslation(locale, key, request.getValue());
        return ResponseEntity.ok().build();
    }
<<<<<<< HEAD

    @DeleteMapping("/{locale}/{key}")
    public ResponseEntity<Void> deleteTranslationKey(
            @PathVariable String locale,
            @PathVariable String key
    ) {
        i18nService.deleteTranslationKey(locale, key);
        return ResponseEntity.ok().build();
    }
}

=======
}
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
