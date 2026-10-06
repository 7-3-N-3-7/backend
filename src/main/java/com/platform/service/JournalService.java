package com.platform.service;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class JournalService {

    private static final String INTERNAL_NOTES_MARKER = "<!-- INTERNAL_MEDICAL_NOTES -->";
    private static final Pattern INTERNAL_NOTES_PATTERN = 
        Pattern.compile(INTERNAL_NOTES_MARKER + ".*", Pattern.DOTALL);

    public String getJournalContent(String rawMarkdown, boolean isTherapist) {
        if (isTherapist) {
            return rawMarkdown;
        }
        return INTERNAL_NOTES_PATTERN.matcher(rawMarkdown).replaceAll("").trim();
    }
}
