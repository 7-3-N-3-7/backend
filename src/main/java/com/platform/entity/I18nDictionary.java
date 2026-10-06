package com.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document(collection = "i18n_dictionaries")
public class I18nDictionary {

    @Id
    private String id;
    
    private String locale;
    
    private Map<String, Object> translations;

    public I18nDictionary() {}

    public I18nDictionary(String locale, Map<String, Object> translations) {
        this.locale = locale;
        this.translations = translations;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Map<String, Object> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, Object> translations) {
        this.translations = translations;
    }
}
