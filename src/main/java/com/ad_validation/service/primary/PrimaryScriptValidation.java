package com.ad_validation.service.primary;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PrimaryScriptValidation {

  private static final List<String> KEYWORD_ORDER =
      Arrays.asList(
          "CREATE AD", // Required
          "DESC", // Optional
          "SRCQUAL", // Required
          "START", // Required
          "ADDTBLS", // Required
          "MODCRIT", // Required
          "ADCHGS", // Required
          "USENEW", // Required
          "USEFM", // Required
          "PNSSTATE", // Required
          "SOLUTION", // Required
          "GRPCOL", // Optional
          "VAR" // Optional
          );

  private static final Map<String, Pattern> KEYWORD_PATTERNS = new HashMap<>();

  static {
    KEYWORD_PATTERNS.put(
        "CREATE AD", Pattern.compile("CREATE AD\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("DESC", Pattern.compile("DESC\\s+//.*?//", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("SRCQUAL", Pattern.compile("SRCQUAL\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("START", Pattern.compile("START\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("ADDTBLS", Pattern.compile("ADDTBLS\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("MODCRIT", Pattern.compile("MODCRIT\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("ADCHGS", Pattern.compile("ADCHGS\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("USENEW", Pattern.compile("USENEW\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("USEFM", Pattern.compile("USEFM\\s+\\S+", Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put(
        "PNSSTATE",
        Pattern.compile(
            "PNSSTATE\\s+(N|L(\\s+LOCALRL\\s+//[^/]+//)?|F\\s+ROWLIST\\s+//[^/]+//)",
            Pattern.CASE_INSENSITIVE));
    KEYWORD_PATTERNS.put("SOLUTION", Pattern.compile("SOLUTION\\s+\\d+", Pattern.CASE_INSENSITIVE));

    // GRPCOL block (optional)
    KEYWORD_PATTERNS.put(
        "GRPCOL",
        Pattern.compile(
            "GRPCOL\\s+\\S+\\s+GRPROWS\\s+\\d+\\s+GRPVALS\\s+\\d+", Pattern.CASE_INSENSITIVE));

    // VAR block (optional with optional DFTL)
    KEYWORD_PATTERNS.put(
        "VAR",
        Pattern.compile(
            "VAR\\s*\\(\\s*\\S+\\s+PRMPT\\s+//.*?//(\\s+DFTL\\s+\\S+)?\\s*\\)",
            Pattern.CASE_INSENSITIVE));
  }

  public List<String> validatePrimaryScript(String primaryScript) {
    List<String> messages = new ArrayList<>();

    String normalizedScript = primaryScript.replaceAll("\\s+", " ").trim();
    int lastMatchEnd = -1;
    List<String> matchedSegments = new ArrayList<>();

    for (String keyword : KEYWORD_ORDER) {
      Pattern pattern = KEYWORD_PATTERNS.get(keyword);
      if (pattern == null) continue;

      Matcher matcher = pattern.matcher(normalizedScript);
      boolean foundAny = false;

      while (matcher.find()) {
        foundAny = true;
        int matchStart = matcher.start();

        // Order check using the first match
        if (matchStart < lastMatchEnd) {
          messages.add("Keyword '" + keyword + "' is out of order.");
          return messages;
        }

        // Boolean keyword value check
        if (Arrays.asList("ADDTBLS", "MODCRIT", "ADCHGS", "USENEW", "USEFM").contains(keyword)) {
          String[] parts = matcher.group().split("\\s+");
          if (parts.length < 2 || !(parts[1].equals("Y") || parts[1].equals("N"))) {
            messages.add(
                "Invalid boolean value for keyword: " + keyword + " (expected 'Y' or 'N').");
            return messages;
          }
        }

        matchedSegments.add(matcher.group());
        lastMatchEnd = Math.max(lastMatchEnd, matcher.end());
      }

      // Mark required keywords that are missing (excluding optional ones)
      if (!foundAny && !Arrays.asList("DESC", "GRPCOL", "VAR", "SOLUTION").contains(keyword)) {
        messages.add("Missing or invalid value for required keyword: " + keyword);
        return messages;
      }
    }

    // Remove all matched keyword parts from the normalized primaryScript
    String unmatchedScript = normalizedScript;
    for (String part : matchedSegments) {
      unmatchedScript = unmatchedScript.replace(part, "");
    }

    unmatchedScript = unmatchedScript.replaceAll("\\s+", " ").trim();

    if (!unmatchedScript.isEmpty() && !unmatchedScript.equals(";")) {
      messages.add("Invalid characters found : '" + unmatchedScript + "' primary block.");
      return messages;
    }

    return messages;
  }
}
