package com.platform.service;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class JournalService {

    private static final String INTERNAL_NOTES_MARKER = "<!-- INTERNAL_MEDICAL_NOTES -->";
    private static final Pattern INTERNAL_NOTES_PATTERN =
        Pattern.compile(
            INTERNAL_NOTES_MARKER + "[\\s\\S]*?(?=(\\r?\\n## |\\r?\\n" + INTERNAL_NOTES_MARKER + "|$))"
        );

    public String getJournalContent(String rawMarkdown, boolean isTherapist) {
        if (rawMarkdown == null) {
            return "";
        }
        if (isTherapist) {
            return rawMarkdown;
        }
        return INTERNAL_NOTES_PATTERN.matcher(rawMarkdown)
                .replaceAll("")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
