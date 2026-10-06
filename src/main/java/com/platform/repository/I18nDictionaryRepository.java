package com.platform.repository;

import com.platform.entity.I18nDictionary;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface I18nDictionaryRepository extends MongoRepository<I18nDictionary, String> {
    Optional<I18nDictionary> findByLocale(String locale);
}
