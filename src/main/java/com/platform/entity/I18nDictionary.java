package com.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

<<<<<<< HEAD
import java.time.Instant;
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
import java.util.Map;

@Document(collection = "i18n_dictionaries")
public class I18nDictionary {

    @Id
    private String id;
<<<<<<< HEAD

    private String locale;

    private String displayName;

    private String flag;

    private Boolean published = true;

    private Instant updatedAt;

=======
    
    private String locale;
    
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    private Map<String, Object> translations;

    public I18nDictionary() {}

    public I18nDictionary(String locale, Map<String, Object> translations) {
        this.locale = locale;
        this.translations = translations;
<<<<<<< HEAD
        this.published = true;
        this.updatedAt = Instant.now();
    }

    public I18nDictionary(String locale, String displayName, String flag, Boolean published, Map<String, Object> translations) {
        this.locale = locale;
        this.displayName = displayName;
        this.flag = flag;
        this.published = published != null ? published : false;
        this.translations = translations;
        this.updatedAt = Instant.now();
=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
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

<<<<<<< HEAD
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public Boolean getPublished() {
        return published != null ? published : true;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
    public Map<String, Object> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, Object> translations) {
        this.translations = translations;
    }
}
<<<<<<< HEAD

=======
>>>>>>> 3d3aa53f18dac79b5509f233401d317d3db0787d
