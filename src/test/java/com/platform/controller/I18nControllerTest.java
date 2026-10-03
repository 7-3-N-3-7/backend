package com.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.entity.I18nDictionary;
import com.platform.service.I18nService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(I18nController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable spring security for the unit tests
public class I18nControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private I18nService i18nService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetDictionary_Success() throws Exception {
        Map<String, Object> translations = new HashMap<>();
        translations.put("key1", "val1");
        I18nDictionary dict = new I18nDictionary("da", translations);

        Mockito.when(i18nService.getDictionary("da")).thenReturn(Optional.of(dict));

        mockMvc.perform(get("/api/v1/i18n/da"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key1").value("val1"));
    }

    @Test
    public void testGetDictionary_Fallback() throws Exception {
        Mockito.when(i18nService.getDictionary("de")).thenReturn(Optional.empty());

        Map<String, Object> fallbackTranslations = new HashMap<>();
        fallbackTranslations.put("hello", "world");
        I18nDictionary fallbackDict = new I18nDictionary("en", fallbackTranslations);

        Mockito.when(i18nService.getDictionary("en")).thenReturn(Optional.of(fallbackDict));

        mockMvc.perform(get("/api/v1/i18n/de"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Locale-Fallback", "en"))
                .andExpect(jsonPath("$.hello").value("world"));
    }

    @Test
    public void testGetDictionary_NotFound() throws Exception {
        Mockito.when(i18nService.getDictionary("de")).thenReturn(Optional.empty());
        Mockito.when(i18nService.getDictionary("en")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/i18n/de"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUpdateTranslation() throws Exception {
        TranslationUpdateRequest req = new TranslationUpdateRequest();
        req.setValue("new_val");

        mockMvc.perform(put("/api/v1/i18n/da/my_key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Mockito.verify(i18nService).updateTranslation(eq("da"), eq("my_key"), eq("new_val"));
    }
}
