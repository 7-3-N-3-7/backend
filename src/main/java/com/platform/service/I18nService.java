package com.platform.service;

import com.platform.entity.I18nDictionary;
import com.platform.repository.I18nDictionaryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class I18nService {

    private final I18nDictionaryRepository repository;

    public I18nService(I18nDictionaryRepository repository) {
        this.repository = repository;
    }

    public Optional<I18nDictionary> getDictionary(String locale) {
        return repository.findByLocale(locale);
    }

    public I18nDictionary updateTranslation(String locale, String key, String value) {
        I18nDictionary dict = repository.findByLocale(locale)
                .orElse(new I18nDictionary(locale, new java.util.HashMap<>()));
        dict.getTranslations().put(key, value);
        return repository.save(dict);
    }
}
