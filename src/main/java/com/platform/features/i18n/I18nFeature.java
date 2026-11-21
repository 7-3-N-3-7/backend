package com.platform.features.i18n;

import com.platform.core.BaseDomainCore.ErrorCode;
import com.platform.core.BaseDomainCore.HasId;
import com.platform.core.BaseDomainCore.Identifiable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * ARKITEKTONISK VISITKORT: I18nFeature (Vertical Feature Slice — Polyglot Persistence)
 * ============================================================================
 * @Spejler: frontend/src/features/i18n/I18nFeature.tsx
 *
 * FORMÅL (Til Censor, Vejleder & Nykommende Udviklere):
 * Demonstrerer Polyglot Persistence i platformen ved at lagre hierarkiske
 * JSON-sprogtræer i MongoDB (`i18n_dictionaries`), mens relationelle kerneentiteter
 * lagres i PostgreSQL.
 *
 * ARKITEKTONISK OVERBLIK (5 TRIN):
 *   1. [Record/DTO: I18nDictionaryDto] & [Record/DTO: TranslationUpdateRequest]
 *      Java 17 `record` kontrakter til udveksling af sprogordbøger og nøgleopdateringer.
 *   2. [Entity: I18nDictionary]
 *      MongoDB `@Document` i samlingen `i18n_dictionaries`, der implementerer [Interface: Identifiable].
 *   3. [Repository: I18nDictionaryRepository]
 *      Package-private Spring Data [Interface: MongoRepository].
 *   4. [Service: I18nService]
 *      Domæneservice med prik-separeret nøgleopdatering (`a.b.c`), #flattenMap(nestedMap, keyPrefix),
 *      dækningsgradsberegning mod dansk referencesprog (`da`) og sprogkloning.
 *   5. [Controller: I18nController] & [Class: ClinicDatabaseInitializer]
 *      REST-controller under `/api/v1/i18n` samt opstarts-seeder til standardoversættelser.
 * ============================================================================
 */
public final class I18nFeature {

    public static final String DEFAULT_REFERENCE_LOCALE = "da";
    public static final String FALLBACK_LOCALE = "en";

    private I18nFeature() {}

    // =========================================================================
    // 1. DTO CONTRACTS (Public Java Records implementing HasId)
    // =========================================================================

    /**
     * [Record/DTO: I18nDictionaryDto]
     * Uforanderlig datakontrakt for et komplet sprogdokument.
     */
    public record I18nDictionaryDto(
            UUID id,
            String locale,
            String displayName,
            String flag,
            Boolean published,
            Instant updatedAt,
            Map<String, Object> translations
    ) implements HasId {
        public static I18nDictionaryDto fromEntity(I18nDictionary dictionaryEntity) {
            return new I18nDictionaryDto(
                    dictionaryEntity.getId(),
                    dictionaryEntity.getLocale(),
                    dictionaryEntity.getDisplayName(),
                    dictionaryEntity.getFlag(),
                    dictionaryEntity.getPublished(),
                    dictionaryEntity.getUpdatedAt(),
                    dictionaryEntity.getTranslations()
            );
        }
    }

    /**
     * [Record/DTO: TranslationUpdateRequest]
     * Payload til opdatering af en enkelt oversættelsesnøgle (`PUT /api/v1/i18n/{locale}/{key}`).
     */
    public record TranslationUpdateRequest(
            UUID id,
            String value
    ) implements HasId {

        public TranslationUpdateRequest(String value) {
            this(null, value);
        }

        public String getValue() { return value; }
    }

    // =========================================================================
    // 2. JPA / MONGO ENTITIES (Implements Identifiable)
    // =========================================================================

    /**
     * [Entity: I18nDictionary]
     * MongoDB-dokument (`@Document(collection = "i18n_dictionaries")`) med indlejret
     * `translations`-træ, så et helt sprog kan hentes i ét enkelt dokument-opslag uden SQL-joins.
     */
    @Document(collection = "i18n_dictionaries")
    public static class I18nDictionary implements Identifiable {

        @Id
        private UUID id = UUID.randomUUID();
        private String locale;
        private String displayName;
        private String flag;
        private Boolean published = true;
        private Instant updatedAt;
        private Map<String, Object> translations;

        public I18nDictionary() {}

        public I18nDictionary(String locale, Map<String, Object> translations) {
            this.id = UUID.randomUUID();
            this.locale = locale;
            this.translations = translations;
            this.published = true;
            this.updatedAt = Instant.now();
        }

        public I18nDictionary(String locale, String displayName, String flag, Boolean published, Map<String, Object> translations) {
            this.id = UUID.randomUUID();
            this.locale = locale;
            this.displayName = displayName;
            this.flag = flag;
            this.published = published != null ? published : false;
            this.translations = translations;
            this.updatedAt = Instant.now();
        }

        @Override public UUID getId() { return id; }
        @Override public void setId(UUID id) { this.id = id; }

        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getFlag() { return flag; }
        public void setFlag(String flag) { this.flag = flag; }

        public Boolean getPublished() { return published != null ? published : true; }
        public void setPublished(Boolean published) { this.published = published; }

        public Instant getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

        public Map<String, Object> getTranslations() { return translations; }
        public void setTranslations(Map<String, Object> translations) { this.translations = translations; }
    }

    // =========================================================================
    // 3. REPOSITORIES & PORTS (Package-Private)
    // =========================================================================

    /**
     * [Repository: I18nDictionaryRepository]
     * Package-private MongoDB-repository til opslag på `locale`.
     */
    @Repository
    interface I18nDictionaryRepository extends MongoRepository<I18nDictionary, UUID> {
        Optional<I18nDictionary> findByLocale(String locale);
    }

    // =========================================================================
    // 4. DOMAIN SERVICES (Package-Private)
    // =========================================================================

    /**
     * [Service: I18nService]
     * Domæneservice for hierarkisk nøgleopdatering, sprogkloning og dækningsgradsberegning.
     */
    @Service
    static class I18nService {

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

        private final I18nDictionaryRepository repository;

        I18nService(I18nDictionaryRepository repository) {
            this.repository = repository;
        }

        public Optional<I18nDictionary> getDictionary(String locale) {
            return repository.findByLocale(locale);
        }

        public List<I18nDictionary> getAllDictionaries() {
            return repository.findAll();
        }

        /**
         * #updateTranslation(locale, key, value) — Opdaterer en prik-separeret nøgle (f.eks. `portal.brandTitle`)
         * i MongoDB-dokumentet for `locale`.
         */
        @SuppressWarnings("unchecked")
        public I18nDictionary updateTranslation(String locale, String key, String value) {
            // Trin 1: Hent eksisterende [Entity: I18nDictionary] eller opret et nyt dokument med standardmetadata
            I18nDictionary dictionaryEntity = repository.findByLocale(locale)
                    .orElseGet(() -> {
                        String[] meta = DEFAULT_LOCALE_META.getOrDefault(locale, new String[]{locale.toUpperCase(), "🌐"});
                        return new I18nDictionary(locale, meta[0], meta[1], true, new HashMap<>());
                    });

            if (dictionaryEntity.getTranslations() == null) {
                dictionaryEntity.setTranslations(new HashMap<>());
            }

            // Trin 2: Naviger ned gennem prik-separerede nøglesegmenter (`a.b.c`) og indsæt `value`
            Map<String, Object> rootMap = dictionaryEntity.getTranslations();
            if (key != null && key.contains(".")) {
                rootMap.remove(key);
                String[] keySegments = key.split("\\.");
                Map<String, Object> currentNode = rootMap;
                for (int i = 0; i < keySegments.length - 1; i++) {
                    String keySegment = keySegments[i];
                    Object nextNode = currentNode.get(keySegment);
                    if (!(nextNode instanceof Map)) {
                        Map<String, Object> childMap = new LinkedHashMap<>();
                        currentNode.put(keySegment, childMap);
                        currentNode = childMap;
                    } else {
                        currentNode = (Map<String, Object>) nextNode;
                    }
                }
                currentNode.put(keySegments[keySegments.length - 1], value);
            } else if (key != null) {
                rootMap.put(key, value);
            }

            // Trin 3: Opdater `updatedAt`-tidsstempel og gem i MongoDB
            dictionaryEntity.setUpdatedAt(Instant.now());
            return repository.save(dictionaryEntity);
        }

        public I18nDictionary deleteTranslationKey(String locale, String key) {
            I18nDictionary dictionaryEntity = repository.findByLocale(locale)
                    .orElseThrow(ErrorCode.NOT_FOUND.supply("Locale not found: " + locale));
            if (dictionaryEntity.getTranslations() != null && key != null) {
                removeNestedKey(dictionaryEntity.getTranslations(), key);
            }
            dictionaryEntity.setUpdatedAt(Instant.now());
            return repository.save(dictionaryEntity);
        }

        @SuppressWarnings("unchecked")
        private void removeNestedKey(Map<String, Object> rootMap, String key) {
            rootMap.remove(key);
            if (!key.contains(".")) {
                return;
            }
            String[] keySegments = key.split("\\.");
            Map<String, Object> currentNode = rootMap;
            for (int i = 0; i < keySegments.length - 1; i++) {
                Object nextNode = currentNode.get(keySegments[i]);
                if (nextNode instanceof Map) {
                    currentNode = (Map<String, Object>) nextNode;
                } else {
                    return;
                }
            }
            currentNode.remove(keySegments[keySegments.length - 1]);
        }

        public I18nDictionary createOrUpdateLocale(
                String locale,
                String displayName,
                String flag,
                Boolean published,
                String cloneFromLocale,
                boolean copyValues
        ) {
            String localeCode = locale.trim().toLowerCase();
            String[] defaultMeta = DEFAULT_LOCALE_META.getOrDefault(localeCode, new String[]{localeCode.toUpperCase(), "🌐"});

            I18nDictionary dictionaryEntity = repository.findByLocale(localeCode)
                    .orElseGet(() -> new I18nDictionary(
                            localeCode,
                            (displayName != null && !displayName.isBlank()) ? displayName.trim() : defaultMeta[0],
                            (flag != null && !flag.isBlank()) ? flag.trim() : defaultMeta[1],
                            published != null ? published : false,
                            new LinkedHashMap<>()
                    ));

            if (displayName != null && !displayName.isBlank()) {
                dictionaryEntity.setDisplayName(displayName.trim());
            } else if (dictionaryEntity.getDisplayName() == null || dictionaryEntity.getDisplayName().isBlank()) {
                dictionaryEntity.setDisplayName(defaultMeta[0]);
            }

            if (flag != null && !flag.isBlank()) {
                dictionaryEntity.setFlag(flag.trim());
            } else if (dictionaryEntity.getFlag() == null || dictionaryEntity.getFlag().isBlank()) {
                dictionaryEntity.setFlag(defaultMeta[1]);
            }

            if (published != null) {
                dictionaryEntity.setPublished(published);
            }

            if (cloneFromLocale != null && !cloneFromLocale.isBlank() && copyValues) {
                repository.findByLocale(cloneFromLocale.trim().toLowerCase()).ifPresent(sourceDict -> {
                    Map<String, String> sourceFlat = flattenMap(sourceDict.getTranslations(), "");
                    Map<String, String> targetFlat = flattenMap(dictionaryEntity.getTranslations(), "");
                    for (Map.Entry<String, String> entry : sourceFlat.entrySet()) {
                        String existingTranslation = targetFlat.get(entry.getKey());
                        if (existingTranslation == null || existingTranslation.isBlank()) {
                            updateNestedMap(dictionaryEntity.getTranslations(), entry.getKey(), entry.getValue());
                        }
                    }
                });
            }

            dictionaryEntity.setUpdatedAt(Instant.now());
            return repository.save(dictionaryEntity);
        }

        public I18nDictionary replaceDictionaryTranslations(String locale, Map<String, Object> translations) {
            String localeCode = locale.trim().toLowerCase();
            String[] defaultMeta = DEFAULT_LOCALE_META.getOrDefault(localeCode, new String[]{localeCode.toUpperCase(), "🌐"});
            I18nDictionary dictionaryEntity = repository.findByLocale(localeCode)
                    .orElseGet(() -> new I18nDictionary(localeCode, defaultMeta[0], defaultMeta[1], false, new LinkedHashMap<>()));
            dictionaryEntity.setTranslations(translations != null ? translations : new LinkedHashMap<>());
            dictionaryEntity.setUpdatedAt(Instant.now());
            return repository.save(dictionaryEntity);
        }

        public void deleteLocale(String locale) {
            repository.findByLocale(locale.trim().toLowerCase()).ifPresent(repository::delete);
        }

        public List<Map<String, Object>> getAllTranslationEntries() {
            List<I18nDictionary> dicts = repository.findAll();
            dicts.sort(Comparator.comparing(d -> Objects.requireNonNullElse(d.getLocale(), "")));
            List<Map<String, Object>> entries = new ArrayList<>();
            for (I18nDictionary dict : dicts) {
                String locale = dict.getLocale();
                if (locale == null) continue;
                Map<String, String> flat = flattenMap(dict.getTranslations(), "");
                for (Map.Entry<String, String> e : flat.entrySet()) {
                    entries.add(toEntryMap(locale, e.getKey(), e.getValue(), dict.getUpdatedAt()));
                }
            }
            return entries;
        }

        public Map<String, Object> createTranslationEntry(Map<String, Object> payload) {
            String locale = Objects.toString(payload.get("locale"), "").trim().toLowerCase();
            String key = Objects.toString(payload.get("translationKey"), "").trim();
            String value = Objects.toString(payload.get("translationValue"), "").trim();
            ErrorCode.BAD_REQUEST.require(
                    !locale.isEmpty() && !key.isEmpty() && !value.isEmpty(),
                    "locale, translationKey and translationValue are required"
            );
            I18nDictionary saved = updateTranslation(locale, key, value);
            return toEntryMap(locale, key, value, saved.getUpdatedAt());
        }

        public void deleteTranslationEntryById(String entryId) {
            int sep = entryId != null ? entryId.indexOf("::") : -1;
            ErrorCode.BAD_REQUEST.require(sep > 0 && sep + 2 < entryId.length(), "Invalid translation entry id");
            deleteTranslationKey(entryId.substring(0, sep), entryId.substring(sep + 2));
        }

        private Map<String, Object> toEntryMap(String locale, String key, String value, Instant updatedAt) {
            int dot = key.indexOf('.');
            String namespace = dot > 0 ? key.substring(0, dot) : "Common";
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", locale + "::" + key);
            entry.put("locale", locale);
            entry.put("namespace", namespace);
            entry.put("translationKey", key);
            entry.put("translationValue", value);
            entry.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
            return entry;
        }

        /**
         * #getLocaleSummaries(referenceLocale) — Beregner oversættelsesdækning (`completionPct`)
         * for samtlige sprog målt mod referencesproget (`da` eller fallback `en`).
         */
        public List<Map<String, Object>> getLocaleSummaries(String referenceLocale) {
            String referenceCode = (referenceLocale != null && !referenceLocale.isBlank())
                    ? referenceLocale.trim().toLowerCase()
                    : DEFAULT_REFERENCE_LOCALE;
            Optional<I18nDictionary> referenceDictOpt = repository.findByLocale(referenceCode);
            if (referenceDictOpt.isEmpty()) {
                referenceDictOpt = repository.findByLocale(FALLBACK_LOCALE);
            }
            Map<String, String> referenceFlatMap = referenceDictOpt
                    .map(dict -> flattenMap(dict.getTranslations(), ""))
                    .orElseGet(Collections::emptyMap);
            int totalReferenceKeys = referenceFlatMap.size();

            List<I18nDictionary> allDictionaries = repository.findAll();
            allDictionaries.sort(Comparator.comparing(dict -> dict.getLocale() != null ? dict.getLocale() : ""));

            List<Map<String, Object>> summaries = new ArrayList<>();
            for (I18nDictionary dictionaryEntity : allDictionaries) {
                String localeCode = dictionaryEntity.getLocale();
                if (localeCode == null) continue;
                String[] defaultMeta = DEFAULT_LOCALE_META.getOrDefault(localeCode, new String[]{localeCode.toUpperCase(), "🌐"});
                Map<String, String> targetFlatMap = flattenMap(dictionaryEntity.getTranslations(), "");

                int translatedRefCount = 0;
                List<String> missingKeys = new ArrayList<>();
                for (String referenceKey : referenceFlatMap.keySet()) {
                    String translatedValue = targetFlatMap.get(referenceKey);
                    if (translatedValue != null && !translatedValue.trim().isEmpty()) {
                        translatedRefCount++;
                    } else {
                        missingKeys.add(referenceKey);
                    }
                }

                int completionPct = totalReferenceKeys > 0
                        ? Math.min(100, (int) Math.round((translatedRefCount * 100.0) / totalReferenceKeys))
                        : (targetFlatMap.isEmpty() ? 0 : 100);

                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", dictionaryEntity.getId());
                summary.put("locale", localeCode);
                summary.put("displayName", (dictionaryEntity.getDisplayName() != null && !dictionaryEntity.getDisplayName().isBlank()) ? dictionaryEntity.getDisplayName() : defaultMeta[0]);
                summary.put("flag", (dictionaryEntity.getFlag() != null && !dictionaryEntity.getFlag().isBlank()) ? dictionaryEntity.getFlag() : defaultMeta[1]);
                summary.put("published", dictionaryEntity.getPublished());
                summary.put("updatedAt", dictionaryEntity.getUpdatedAt() != null ? dictionaryEntity.getUpdatedAt().toString() : null);
                summary.put("translatedCount", translatedRefCount);
                summary.put("totalReferenceKeys", totalReferenceKeys);
                summary.put("totalKeysInDoc", targetFlatMap.size());
                summary.put("missingCount", missingKeys.size());
                summary.put("completionPct", completionPct);
                summaries.add(summary);
            }
            return summaries;
        }

        /**
         * #flattenMap(nestedMap, keyPrefix) — Konverterer et indlejret JSON-træ til et fladt
         * `Map<String, String>` med prik-separerede nøgler (`portal.brandTitle`).
         */
        @SuppressWarnings("unchecked")
        public static Map<String, String> flattenMap(Map<String, Object> nestedMap, String keyPrefix) {
            Map<String, String> flattenedResult = new LinkedHashMap<>();
            if (nestedMap == null) return flattenedResult;
            for (Map.Entry<String, Object> entry : nestedMap.entrySet()) {
                String keySegment = entry.getKey();
                Object nodeValue = entry.getValue();
                String dotSeparatedKey = (keyPrefix == null || keyPrefix.isEmpty())
                        ? keySegment
                        : keyPrefix + "." + keySegment;
                if (nodeValue instanceof Map) {
                    flattenedResult.putAll(flattenMap((Map<String, Object>) nodeValue, dotSeparatedKey));
                } else if (nodeValue != null) {
                    flattenedResult.put(dotSeparatedKey, String.valueOf(nodeValue));
                }
            }
            return flattenedResult;
        }

        @SuppressWarnings("unchecked")
        private void updateNestedMap(Map<String, Object> rootMap, String dotSeparatedKey, String value) {
            if (!dotSeparatedKey.contains(".")) {
                rootMap.put(dotSeparatedKey, value);
                return;
            }
            String[] keySegments = dotSeparatedKey.split("\\.");
            Map<String, Object> currentNode = rootMap;
            for (int i = 0; i < keySegments.length - 1; i++) {
                Object nextNode = currentNode.get(keySegments[i]);
                if (!(nextNode instanceof Map)) {
                    Map<String, Object> childMap = new LinkedHashMap<>();
                    currentNode.put(keySegments[i], childMap);
                    currentNode = childMap;
                } else {
                    currentNode = (Map<String, Object>) nextNode;
                }
            }
            currentNode.put(keySegments[keySegments.length - 1], value);
        }
    }

    // =========================================================================
    // 5. REST CONTROLLERS (Package-Private)
    // =========================================================================

    /**
     * [Controller: I18nController]
     * REST-controller for `/api/v1/i18n` med HTTP `Cache-Control` headers og fallback-håndtering.
     */
    @RestController
    @RequestMapping("/api/v1/i18n")
    @CrossOrigin(origins = "*")
    static class I18nController {

        private final I18nService i18nService;

        I18nController(I18nService i18nService) {
            this.i18nService = i18nService;
        }

        @GetMapping
        public ResponseEntity<List<Map<String, Object>>> getAllEntries() {
            return ResponseEntity.ok(i18nService.getAllTranslationEntries());
        }

        @PostMapping
        public ResponseEntity<Map<String, Object>> createEntry(@RequestBody Map<String, Object> payload) {
            return ResponseEntity.status(HttpStatus.CREATED).body(i18nService.createTranslationEntry(payload));
        }

        @DeleteMapping("/{entryId}")
        public ResponseEntity<Void> deleteEntry(@PathVariable String entryId) {
            i18nService.deleteTranslationEntryById(entryId);
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/locales")
        public ResponseEntity<List<Map<String, Object>>> getLocales(
                @RequestParam(value = "ref", defaultValue = DEFAULT_REFERENCE_LOCALE) String ref
        ) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                    .body(i18nService.getLocaleSummaries(ref));
        }

        @PostMapping("/locales")
        public ResponseEntity<I18nDictionary> createOrUpdateLocale(@RequestBody Map<String, Object> payload) {
            String locale = String.valueOf(payload.getOrDefault("locale", "")).trim().toLowerCase();
            ErrorCode.BAD_REQUEST.require(!locale.isEmpty(), "Locale is required");
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
                    .orElseGet(() -> i18nService.getDictionary(FALLBACK_LOCALE)
                            .map(fallback -> ResponseEntity.ok()
                                    .cacheControl(cacheHeader)
                                    .header("X-Locale-Fallback", FALLBACK_LOCALE)
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

        @DeleteMapping("/{locale}/{key}")
        public ResponseEntity<Void> deleteTranslationKey(
                @PathVariable String locale,
                @PathVariable String key
        ) {
            i18nService.deleteTranslationKey(locale, key);
            return ResponseEntity.ok().build();
        }
    }

    // =========================================================================
    // 6. INITIALIZER (Package-Private Component)
    // =========================================================================

    /**
     * [Class: ClinicDatabaseInitializer]
     * Seeder MongoDB med basale portal-oversættelser (`en`, `da`, `fr`, `es`, `de`) ved opstart.
     */
    @Configuration(proxyBeanMethods = false)
    @Profile("!test")
    @EnableMongoRepositories(basePackages = "com.platform.features.i18n", considerNestedRepositories = true)
    static class ClinicDatabaseInitializer implements CommandLineRunner {

        private static final Logger log = LoggerFactory.getLogger(ClinicDatabaseInitializer.class);
        private final I18nDictionaryRepository i18nDictionaryRepository;

        ClinicDatabaseInitializer(I18nDictionaryRepository i18nDictionaryRepository) {
            this.i18nDictionaryRepository = i18nDictionaryRepository;
        }

        @Override
        public void run(String... args) {
            seedI18nDictionaries();
        }

        private void seedI18nDictionaries() {
            Map<String, String[]> defaultMeta = Map.of(
                    "da", new String[]{"Dansk", "🇩🇰"},
                    "en", new String[]{"English", "🇬🇧"},
                    "fr", new String[]{"Français", "🇫🇷"},
                    "es", new String[]{"Español", "🇪🇸"},
                    "de", new String[]{"Deutsch", "🇩🇪"}
            );
            String[] supportedLocales = {"en", "da", "fr", "es", "de"};
            for (String localeCode : supportedLocales) {
                try {
                    I18nDictionary dictionaryEntity = i18nDictionaryRepository.findByLocale(localeCode)
                            .orElseGet(() -> new I18nDictionary(localeCode, new HashMap<>()));
                    String[] meta = defaultMeta.getOrDefault(localeCode, new String[]{localeCode.toUpperCase(), "🌐"});
                    if (dictionaryEntity.getDisplayName() == null || dictionaryEntity.getDisplayName().isBlank()) {
                        dictionaryEntity.setDisplayName(meta[0]);
                    }
                    if (dictionaryEntity.getFlag() == null || dictionaryEntity.getFlag().isBlank()) {
                        dictionaryEntity.setFlag(meta[1]);
                    }
                    Map<String, Object> rootTranslations = dictionaryEntity.getTranslations();
                    if (rootTranslations == null) {
                        rootTranslations = new HashMap<>();
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> existingPortal = rootTranslations.get("portal") instanceof Map
                            ? new LinkedHashMap<>((Map<String, Object>) rootTranslations.get("portal"))
                            : new LinkedHashMap<>();
                    Map<String, Object> defaultPortalStrings = buildPortalTranslations(localeCode);
                    for (Map.Entry<String, Object> entry : defaultPortalStrings.entrySet()) {
                        existingPortal.put(entry.getKey(), entry.getValue());
                    }
                    rootTranslations.put("portal", existingPortal);
                    dictionaryEntity.setTranslations(rootTranslations);
                    i18nDictionaryRepository.save(dictionaryEntity);
                } catch (Exception e) {
                    log.warn("Could not seed MongoDB i18n dictionary for {}: {}", localeCode, e.getMessage());
                }
            }
            log.info("MongoDB i18n_dictionaries enriched with portal translations for en, da, fr, es, de.");
        }

        private Map<String, Object> buildPortalTranslations(String localeCode) {
            Map<String, Object> portalMap = new LinkedHashMap<>();
            portalMap.put("brandTitle", "da".equals(localeCode) ? "EduJournal Klinik" : "EduJournal Clinic");
            portalMap.put("available", "da".equals(localeCode) ? "Tilgængelig" : "Available");
            portalMap.put("unavailable", "da".equals(localeCode) ? "Utilgængelig" : "Unavailable");
            return portalMap;
        }
    }
}
