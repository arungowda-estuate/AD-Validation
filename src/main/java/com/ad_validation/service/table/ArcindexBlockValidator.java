package com.ad_validation.service.table;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ArcindexBlockValidator {

  public List<String> validateArcIdxBlock(String blockContent, String tableName) {
    List<String> messages = new ArrayList<>();

    String content = blockContent.trim();

    Pattern arcIdxPattern =
        Pattern.compile("ARCIDX\\s*\\(\\s*\\w+\\s*,\\s*A\\w+\\s*\\)", Pattern.CASE_INSENSITIVE);

    if (!arcIdxPattern.matcher(content).matches()) {
      messages.add("Invalid ARCIDX block in table " + tableName + ": " + content);
    }

    return messages;
  }
}
