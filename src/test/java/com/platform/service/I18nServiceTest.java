package com.platform.service;

import com.platform.entity.I18nDictionary;
import com.platform.repository.I18nDictionaryRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class I18nServiceTest {

    private I18nDictionaryRepository repository;
    private I18nService i18nService;

    @BeforeEach
    public void setUp() {
        repository = Mockito.mock(I18nDictionaryRepository.class);
        i18nService = new I18nService(repository);
    }

    @Test
    public void testGetDictionary_Found() {
        Map<String, Object> translations = new HashMap<>();
        translations.put("hello", "hej");
        I18nDictionary dict = new I18nDictionary("da", translations);
        
        when(repository.findByLocale("da")).thenReturn(Optional.of(dict));
        
        Optional<I18nDictionary> result = i18nService.getDictionary("da");
        
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals("hej", result.get().getTranslations().get("hello"));
        verify(repository).findByLocale("da");
    }

    @Test
    public void testGetDictionary_NotFound() {
        when(repository.findByLocale("de")).thenReturn(Optional.empty());
        
        Optional<I18nDictionary> result = i18nService.getDictionary("de");
        
        Assertions.assertFalse(result.isPresent());
    }

    @Test
    public void testUpdateTranslation_ExistingDictionary() {
        Map<String, Object> translations = new HashMap<>();
        translations.put("key1", "val1");
        I18nDictionary dict = new I18nDictionary("da", translations);
        
        when(repository.findByLocale("da")).thenReturn(Optional.of(dict));
        when(repository.save(any(I18nDictionary.class))).thenAnswer(i -> i.getArguments()[0]);
        
        I18nDictionary updated = i18nService.updateTranslation("da", "key2", "val2");
        
        Assertions.assertEquals("val2", updated.getTranslations().get("key2"));
        Assertions.assertEquals("val1", updated.getTranslations().get("key1"));
        verify(repository).save(dict);
    }

    @Test
    public void testUpdateTranslation_NewDictionary() {
        when(repository.findByLocale("es")).thenReturn(Optional.empty());
        when(repository.save(any(I18nDictionary.class))).thenAnswer(i -> i.getArguments()[0]);
        
        I18nDictionary newDict = i18nService.updateTranslation("es", "greeting", "hola");
        
        Assertions.assertEquals("es", newDict.getLocale());
        Assertions.assertEquals("hola", newDict.getTranslations().get("greeting"));
        verify(repository).save(any(I18nDictionary.class));
    }
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715

    @Test
    @SuppressWarnings("unchecked")
    public void testI18nServiceAndControllerFullBranches() {
        when(repository.save(any(I18nDictionary.class))).thenAnswer(i -> i.getArguments()[0]);

        // 1. Nested dotted key updates & null translations map & null key
        I18nDictionary nullMapDict = new I18nDictionary("xx", null);
        when(repository.findByLocale("xx")).thenReturn(Optional.of(nullMapDict));
        I18nDictionary nested1 = i18nService.updateTranslation("xx", "portal.nav.home", "Home");
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
        Assertions.assertThrows(IllegalArgumentException.class, () -> i18nService.deleteTranslationKey("missing", "k"));

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

        // Calling createOrUpdateLocale again on existing svDict with blank displayName/flag & pre-populated key
        when(repository.findByLocale("sv")).thenReturn(Optional.of(svDict));
        I18nDictionary svUpdated = i18nService.createOrUpdateLocale("sv", "  ", "", false, "da", true);
        Assertions.assertFalse(svUpdated.getPublished());

        // Blank displayName/flag on new custom locale
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
        when(repository.findAll()).thenReturn(new java.util.ArrayList<>(java.util.List.of(daDict, partialDict, nullLocaleDict)));
        Assertions.assertEquals(3, i18nService.getAllDictionaries().size());

        var summaries = i18nService.getLocaleSummaries("da");
        Assertions.assertEquals(2, summaries.size());

        when(repository.findByLocale("unknown")).thenReturn(Optional.empty());
        when(repository.findByLocale("en")).thenReturn(Optional.empty());
        var summariesNoRef = i18nService.getLocaleSummaries("unknown");
        Assertions.assertEquals(2, summariesNoRef.size());
        var summariesBlankRef = i18nService.getLocaleSummaries("   ");
        Assertions.assertEquals(2, summariesBlankRef.size());

        Assertions.assertTrue(I18nService.flattenMap(null, "").isEmpty());

        // 6. Direct I18nController endpoint tests for all remaining branches
        com.platform.controller.I18nController ctrl = new com.platform.controller.I18nController(i18nService);
        Assertions.assertEquals(200, ctrl.getLocales("da").getStatusCode().value());
        Assertions.assertEquals(400, ctrl.createOrUpdateLocale(Map.of("locale", "   ")).getStatusCode().value());
        Assertions.assertEquals(200, ctrl.createOrUpdateLocale(Map.of(
                "locale", "de",
                "displayName", "Deutsch",
                "flag", "🇩🇪",
                "published", true,
                "cloneFromLocale", "da",
                "copyValues", true
        )).getStatusCode().value());
        Assertions.assertEquals(200, ctrl.createOrUpdateLocale(Map.of("locale", "it")).getStatusCode().value());
        Assertions.assertEquals(200, ctrl.updateLocaleStatus("da", Map.of("published", false, "displayName", "Dansk", "flag", "🇩🇰")).getStatusCode().value());
        Assertions.assertEquals(200, ctrl.updateLocaleStatus("da", Map.of()).getStatusCode().value());
        Assertions.assertEquals(200, ctrl.deleteLocale("da").getStatusCode().value());
        Assertions.assertEquals(200, ctrl.getRawDocument("da").getStatusCode().value());
        Assertions.assertEquals(404, ctrl.getRawDocument("unknown").getStatusCode().value());
        Assertions.assertEquals(200, ctrl.replaceRawTranslations("da", Map.of("translations", new HashMap<>(Map.of("a", "b")))).getStatusCode().value());
        Assertions.assertEquals(200, ctrl.replaceRawTranslations("da", new HashMap<>(Map.of("a", "b"))).getStatusCode().value());
        Assertions.assertEquals(200, ctrl.deleteTranslationKey("da", "a").getStatusCode().value());
    }
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
<<<<<<< HEAD
=======
>>>>>>> 266b19092e0a4e40a20f5cd2eaf04be642298e92
=======
>>>>>>> 16f2191da8c19882528cd7f0f6ff79480a370715
}
