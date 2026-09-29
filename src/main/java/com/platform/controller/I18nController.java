package com.platform.controller;

import com.platform.service.I18nService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/i18n")
@CrossOrigin(origins = "*")
public class I18nController {

    private final I18nService i18nService;

    public I18nController(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @GetMapping("/{locale}")
    public ResponseEntity<Map<String, String>> getDictionary(@PathVariable String locale) {
        return i18nService.getDictionary(locale)
                .map(dict -> ResponseEntity.ok(dict.getTranslations()))
                .orElseGet(() -> i18nService.getDictionary("en")
                        .map(fallback -> ResponseEntity.ok()
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
}
