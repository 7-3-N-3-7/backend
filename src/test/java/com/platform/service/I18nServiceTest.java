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
        Map<String, String> translations = new HashMap<>();
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
        Map<String, String> translations = new HashMap<>();
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
}
