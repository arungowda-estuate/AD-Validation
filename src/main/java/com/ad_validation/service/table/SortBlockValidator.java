package com.ad_validation.service.table;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SortBlockValidator {
  public List<String> validateSortBlock(String blockContent, String tableName) {
    List<String> messages = new ArrayList<>();

    // Trim and normalize
    String content = blockContent.trim();

    Pattern sortPattern =
        Pattern.compile(
            "^SORT \\([AD][a-zA-Z0-9_]+(?:,\\s*[AD][a-zA-Z0-9_]+)*\\)$", Pattern.CASE_INSENSITIVE);

    if (!sortPattern.matcher(content).matches()) {
      messages.add("Invalid SORT block in table " + tableName + ": " + content);
    }

    return messages;
  }
}
