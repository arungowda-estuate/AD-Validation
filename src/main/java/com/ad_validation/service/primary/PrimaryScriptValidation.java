package com.ad_validation.service.primary;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PrimaryScriptValidation {
  private static final List<String> keywordOrder =
      Arrays.asList(
          "CREATE AD",
          "DESC", // Optional
          "SRCQUAL",
          "START",
          "ADDTBLS",
          "MODCRIT",
          "ADCHGS",
          "USENEW",
          "USEFM",
          "PNSSTATE",
          "SOLUTION");

  private static final Map<String, String> keywordPatterns = new HashMap<>();

  static {
    keywordPatterns.put("CREATE AD", "CREATE AD\\s+\\S+");
    keywordPatterns.put("DESC", "DESC\\s+//.*?//");
    keywordPatterns.put("SRCQUAL", "SRCQUAL\\s+\\S+");
    keywordPatterns.put("START", "START\\s+\\S+");
    keywordPatterns.put("ADDTBLS", "ADDTBLS\\s+[YN]");
    keywordPatterns.put("MODCRIT", "MODCRIT\\s+[YN]");
    keywordPatterns.put("ADCHGS", "ADCHGS\\s+[YN]");
    keywordPatterns.put("USENEW", "USENEW\\s+[YN]");
    keywordPatterns.put("USEFM", "USEFM\\s+[YN]");
    keywordPatterns.put(
        "PNSSTATE", "PNSSTATE\\s+(N|L(\\s+LOCALRL\\s+//[^/]+//)?|F\\s+ROWLIST\\s+//[^/]+//)");

    keywordPatterns.put("SOLUTION", "SOLUTION\\s+\\d+");
  }

  public List<String> validatePrimaryScript(String script) {
    List<String> messages = new ArrayList<>();

    // Normalize spacing
    script = script.replaceAll("\\s+", " ").trim();

    int lastMatchEnd = -1;
    boolean isValid = true;

    for (String keyword : keywordOrder) {
      String patternStr = keywordPatterns.get(keyword);
      if (patternStr == null) continue;

      Pattern pattern = Pattern.compile(patternStr);
      Matcher matcher = pattern.matcher(script);

      if (matcher.find()) {
        int matchStart = matcher.start();

        // Order validation
        if (matchStart < lastMatchEnd) {
          messages.add("Keyword '" + keyword + "' is out of order.");
          isValid = false;
        }
        lastMatchEnd = matcher.end();

      } else {
        if (!keyword.equals("DESC")) {
          messages.add("Missing or invalid value for required keyword: " + keyword);
          isValid = false;
        }
      }
    }

    return messages;
  }
}
