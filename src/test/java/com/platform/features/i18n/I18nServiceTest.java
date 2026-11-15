package com.platform.features.i18n;

import com.platform.core.BaseDomainCore.DomainException;
import com.platform.features.i18n.I18nFeature.ClinicDatabaseInitializer;
import com.platform.features.i18n.I18nFeature.I18nController;
import com.platform.features.i18n.I18nFeature.I18nDictionary;
import com.platform.features.i18n.I18nFeature.I18nDictionaryDto;
import com.platform.features.i18n.I18nFeature.I18nDictionaryRepository;
import com.platform.features.i18n.I18nFeature.I18nService;
import com.platform.features.i18n.I18nFeature.TranslationUpdateRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class I18nServiceTest {

    private I18nDictionaryRepository repository;
    private I18nService i18nService;
    private I18nController i18nController;

    @BeforeEach
    public void setUp() {
        repository = Mockito.mock(I18nDictionaryRepository.class);
        i18nService = new I18nService(repository);
        i18nController = new I18nController(i18nService);
    }

    @Test
    public void testGetDictionaryAndControllerEndpoints() {
        Map<String, Object> translations = new HashMap<>();
        translations.put("hello", "hej");
        I18nDictionary dict = new I18nDictionary("da", translations);

        when(repository.findByLocale("da")).thenReturn(Optional.of(dict));

        Optional<I18nDictionary> result = i18nService.getDictionary("da");
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals("hej", result.get().getTranslations().get("hello"));
        verify(repository).findByLocale("da");

        ResponseEntity<Map<String, Object>> okResp = i18nController.getDictionary("da");
        Assertions.assertEquals(200, okResp.getStatusCode().value());
        Assertions.assertEquals("hej", okResp.getBody().get("hello"));

        // Fallback to en
        when(repository.findByLocale("de")).thenReturn(Optional.empty());
        I18nDictionary enDict = new I18nDictionary("en", Map.of("hello", "world"));
        when(repository.findByLocale("en")).thenReturn(Optional.of(enDict));

        ResponseEntity<Map<String, Object>> fallbackResp = i18nController.getDictionary("de");
        Assertions.assertEquals(200, fallbackResp.getStatusCode().value());
        Assertions.assertEquals("en", fallbackResp.getHeaders().getFirst("X-Locale-Fallback"));
        Assertions.assertEquals("world", fallbackResp.getBody().get("hello"));

        // NotFound when both missing
        when(repository.findByLocale("en")).thenReturn(Optional.empty());
        Assertions.assertEquals(404, i18nController.getDictionary("de").getStatusCode().value());

        // updateTranslation via Controller
        when(repository.save(any(I18nDictionary.class))).thenAnswer(i -> i.getArguments()[0]);
        TranslationUpdateRequest req = new TranslationUpdateRequest("new_val");
        Assertions.assertEquals("new_val", req.getValue());
        Assertions.assertNull(req.id());
        ResponseEntity<Void> putResp = i18nController.updateTranslation("da", "my_key", req);
        Assertions.assertEquals(200, putResp.getStatusCode().value());
        Assertions.assertEquals("new_val", dict.getTranslations().get("my_key"));
    }

    @Test
    public void testUpdateTranslationNewAndExisting() {
        when(repository.save(any(I18nDictionary.class))).thenAnswer(i -> i.getArguments()[0]);

        Map<String, Object> translations = new HashMap<>();
        translations.put("key1", "val1");
        I18nDictionary dict = new I18nDictionary("da", translations);
        when(repository.findByLocale("da")).thenReturn(Optional.of(dict));

        I18nDictionary updated = i18nService.updateTranslation("da", "key2", "val2");
        Assertions.assertEquals("val2", updated.getTranslations().get("key2"));
        Assertions.assertEquals("val1", updated.getTranslations().get("key1"));

        when(repository.findByLocale("es")).thenReturn(Optional.empty());
        I18nDictionary newDict = i18nService.updateTranslation("es", "greeting", "hola");
        Assertions.assertEquals("es", newDict.getLocale());
        Assertions.assertEquals("hola", newDict.getTranslations().get("greeting"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testI18nServiceAndControllerFullBranches() throws Exception {
        when(repository.save(any(I18nDictionary.class))).thenAnswer(i -> i.getArguments()[0]);

        // 1. Nested dotted key updates & null translations map & null key
        I18nDictionary nullMapDict = new I18nDictionary("xx", null);
        when(repository.findByLocale("xx")).thenReturn(Optional.of(nullMapDict));
        i18nService.updateTranslation("xx", "portal.nav.home", "Home");
        I18nDictionary nested2 = i18nService.updateTranslation("xx", "portal.nav.about", "About");
        I18nDictionary nullKey = i18nService.updateTranslation("xx", null, "Ignored");
        Assertions.assertNotNull(nullKey.getTranslations());
        Map<String, Object> portalMap = (Map<String, Object>) nested2.getTranslations().get("portal");
        Map<String, Object> navMap = (Map<String, Object>) portalMap.get("nav");
        Assertions.assertEquals("Home", navMap.get("home"));
        Assertions.assertEquals("About", navMap.get("about"));

        // 2. deleteTranslationKey (nested, flat, non-map path, null key, not found)
        i18nService.deleteTranslationKey("xx", "portal.nav.home");
        i18nService.deleteTranslationKey("xx", "flatKey");
        i18nService.deleteTranslationKey("xx", "portal.nonexistent.leaf");
        i18nService.deleteTranslationKey("xx", null);
        Assertions.assertThrows(DomainException.class, () -> i18nService.deleteTranslationKey("missing", "k"));

        // 3. createOrUpdateLocale & cloneFromLocale with copyValues
        Map<String, Object> daTrans = new HashMap<>();
        daTrans.put("flat", "Hej");
        Map<String, Object> daNested = new HashMap<>();
        daNested.put("title", "Velkommen");
        daNested.put("sub", Map.of("item", "Værdi"));
        daTrans.put("section", daNested);
        I18nDictionary daDict = new I18nDictionary("da", "Dansk", "🇩🇰", true, daTrans);
        when(repository.findByLocale("da")).thenReturn(Optional.of(daDict));
        when(repository.findByLocale("sv")).thenReturn(Optional.empty());

        I18nDictionary svDict = i18nService.createOrUpdateLocale(" SV ", "Svenska", "🇸🇪", true, "da", true);
        Assertions.assertEquals("sv", svDict.getLocale());
        Assertions.assertEquals("Svenska", svDict.getDisplayName());
        Assertions.assertTrue(svDict.getPublished());

        when(repository.findByLocale("sv")).thenReturn(Optional.of(svDict));
        I18nDictionary svUpdated = i18nService.createOrUpdateLocale("sv", "  ", "", false, "da", true);
        Assertions.assertFalse(svUpdated.getPublished());

        I18nDictionary blankExisting = new I18nDictionary("zz", "", "", null, new HashMap<>());
        when(repository.findByLocale("zz")).thenReturn(Optional.empty()).thenReturn(Optional.of(blankExisting));
        i18nService.createOrUpdateLocale("zz", null, null, null, null, false);
        i18nService.createOrUpdateLocale("zz", "", "", null, "   ", true);

        // 4. replaceDictionaryTranslations & deleteLocale & getAllDictionaries
        when(repository.findByLocale("fr")).thenReturn(Optional.empty());
        I18nDictionary replaced = i18nService.replaceDictionaryTranslations("FR", Map.of("hello", "bonjour"));
        Assertions.assertEquals("bonjour", replaced.getTranslations().get("hello"));
        I18nDictionary replacedNull = i18nService.replaceDictionaryTranslations("FR", null);
        Assertions.assertTrue(replacedNull.getTranslations().isEmpty());

        when(repository.findByLocale("da")).thenReturn(Optional.of(daDict));
        i18nService.deleteLocale("DA");

        // 5. getLocaleSummaries (with reference locale, fallback to en, and empty reference)
        I18nDictionary partialDict = new I18nDictionary("no", "", "", null, Map.of("flat", "Hei", "section", Map.of("title", "   ")));
        I18nDictionary nullLocaleDict = new I18nDictionary(null, Map.of());
        when(repository.findAll()).thenReturn(new ArrayList<>(List.of(daDict, partialDict, nullLocaleDict)));
        Assertions.assertEquals(3, i18nService.getAllDictionaries().size());

        var summaries = i18nService.getLocaleSummaries("da");
        Assertions.assertEquals(2, summaries.size());

        when(repository.findByLocale("unknown")).thenReturn(Optional.empty());
        when(repository.findByLocale("en")).thenReturn(Optional.empty());
        Assertions.assertEquals(2, i18nService.getLocaleSummaries("unknown").size());
        Assertions.assertEquals(2, i18nService.getLocaleSummaries("   ").size());
        Assertions.assertEquals(2, i18nService.getLocaleSummaries(null).size());
        Assertions.assertTrue(I18nService.flattenMap(null, "").isEmpty());

        // 6. Direct I18nController endpoint tests
        Assertions.assertEquals(200, i18nController.getLocales("da").getStatusCode().value());
        Assertions.assertThrows(DomainException.class, () -> i18nController.createOrUpdateLocale(Map.of("locale", "   ")));
        Assertions.assertEquals(200, i18nController.createOrUpdateLocale(Map.of(
                "locale", "de",
                "displayName", "Deutsch",
                "flag", "🇩🇪",
                "published", true,
                "cloneFromLocale", "da",
                "copyValues", true
        )).getStatusCode().value());
        Assertions.assertEquals(200, i18nController.createOrUpdateLocale(Map.of("locale", "it")).getStatusCode().value());
        Assertions.assertEquals(200, i18nController.updateLocaleStatus("da", Map.of("published", false, "displayName", "Dansk", "flag", "🇩🇰")).getStatusCode().value());
        Assertions.assertEquals(200, i18nController.updateLocaleStatus("da", Map.of()).getStatusCode().value());
        Assertions.assertEquals(200, i18nController.deleteLocale("da").getStatusCode().value());
        Assertions.assertEquals(200, i18nController.getRawDocument("da").getStatusCode().value());
        Assertions.assertEquals(404, i18nController.getRawDocument("unknown").getStatusCode().value());
        Assertions.assertEquals(200, i18nController.replaceRawTranslations("da", Map.of("translations", new HashMap<>(Map.of("a", "b")))).getStatusCode().value());
        Assertions.assertEquals(200, i18nController.replaceRawTranslations("da", new HashMap<>(Map.of("a", "b"))).getStatusCode().value());
        Assertions.assertEquals(200, i18nController.deleteTranslationKey("da", "a").getStatusCode().value());

        // 7. Extra branches in I18nService, I18nDictionary, I18nDictionaryDto & ClinicDatabaseInitializer
        I18nDictionary emptyFlatDict = new I18nDictionary("fi", "Suomi", "🇫🇮", true, new HashMap<>());
        emptyFlatDict.setUpdatedAt(null);
        UUID fiUuid = UUID.randomUUID();
        emptyFlatDict.setId(fiUuid);
        emptyFlatDict.setLocale("fi");
        emptyFlatDict.setDisplayName("Suomi");
        emptyFlatDict.setFlag("🇫🇮");
        emptyFlatDict.setTranslations(new HashMap<>());
        I18nDictionaryDto dictDto = I18nDictionaryDto.fromEntity(emptyFlatDict);
        Assertions.assertEquals(fiUuid, dictDto.id());
        Assertions.assertEquals("fi", dictDto.locale());
        ReflectionTestUtils.setField(emptyFlatDict, "published", null);
        Assertions.assertTrue(emptyFlatDict.getPublished());
        emptyFlatDict.setPublished(false);
        Assertions.assertFalse(emptyFlatDict.getPublished());

        I18nDictionary nullNameDict = new I18nDictionary("pt", null, null, true, Map.of("k", "v"));
        when(repository.findAll()).thenReturn(new ArrayList<>(List.of(emptyFlatDict, nullNameDict)));
        i18nService.getLocaleSummaries("unknown");

        Map<String, Object> mapWithNullVal = new HashMap<>();
        mapWithNullVal.put("nullKey", null);
        mapWithNullVal.put("validKey", "val");
        Assertions.assertEquals(1, I18nService.flattenMap(mapWithNullVal, null).size());

        I18nDictionary dictNullTrans = new I18nDictionary("is", null);
        when(repository.findByLocale("is")).thenReturn(Optional.of(dictNullTrans));
        i18nService.deleteTranslationKey("is", "some.key");

        I18nDictionary dictWithMeta = new I18nDictionary("nl", "Nederlands", "🇳🇱", true, new HashMap<>());
        when(repository.findByLocale("nl")).thenReturn(Optional.of(dictWithMeta));
        i18nService.createOrUpdateLocale("nl", null, null, null, "da", false);
        Assertions.assertEquals(200, i18nController.createOrUpdateLocale(Map.of("locale", "nl", "copyValues", false)).getStatusCode().value());
        Assertions.assertEquals(200, i18nController.replaceRawTranslations("nl", Map.of("translations", "not-a-map")).getStatusCode().value());

        I18nDictionary existingNonBlankDict = new I18nDictionary("fr", "Français", "🇫🇷", true, new LinkedHashMap<>(Map.of("k1", "   ")));
        I18nDictionary sourceCloneDict = new I18nDictionary("en", "English", "🇬🇧", true, new LinkedHashMap<>(Map.of("k1", "Val1")));
        when(repository.findByLocale("fr")).thenReturn(Optional.of(existingNonBlankDict));
        when(repository.findByLocale("en")).thenReturn(Optional.of(sourceCloneDict));
        i18nService.createOrUpdateLocale("fr", null, null, null, "en", true);

        I18nDictionary existingNullNameFlagDict = new I18nDictionary("it", null, null, true, new LinkedHashMap<>());
        when(repository.findByLocale("it")).thenReturn(Optional.of(existingNullNameFlagDict));
        i18nService.createOrUpdateLocale("it", null, null, null, null, false);

        // 8. ClinicDatabaseInitializer full coverage
        I18nDictionaryRepository initRepo = Mockito.mock(I18nDictionaryRepository.class);
        I18nDictionary dictWithNullMap = new I18nDictionary("en", null);
        when(initRepo.findByLocale("en")).thenReturn(Optional.of(dictWithNullMap));
        when(initRepo.findByLocale("da")).thenReturn(Optional.empty());
        when(initRepo.findByLocale("fr")).thenReturn(Optional.empty());
        when(initRepo.findByLocale("es")).thenReturn(Optional.empty());
        when(initRepo.findByLocale("de"))
                .thenReturn(Optional.empty())
                .thenThrow(new RuntimeException("Simulated Mongo timeout"));

        ClinicDatabaseInitializer initializer = new ClinicDatabaseInitializer(initRepo);
        initializer.run();
        initializer.run();

        I18nDictionary fullDict = new I18nDictionary("en", "English Custom", "🇬🇧", false, new HashMap<>());
        I18nDictionary blankMetaDict = new I18nDictionary("da", "   ", "   ", null, new HashMap<>());
        when(initRepo.findByLocale(any())).thenReturn(Optional.of(fullDict));
        when(initRepo.findByLocale("da")).thenReturn(Optional.of(blankMetaDict));
        new ClinicDatabaseInitializer(initRepo).run();
    }
}
