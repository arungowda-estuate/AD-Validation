package com.ad_validation.service.table;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SqlBlockValidator {
  public List<String> validateSqlBlock(String block, String tableName) {
    List<String> messages = new ArrayList<>();
    String trimmedBlock = block.trim();

    // Check if block matches the format: SQL //...//
    Pattern sqlPattern = Pattern.compile("^SQL\\s+//(.+?)//$", Pattern.CASE_INSENSITIVE);
    Matcher matcher = sqlPattern.matcher(trimmedBlock);

    if (!matcher.matches()) {
      messages.add(
          "Invalid SQL block format in table '"
              + tableName
              + "'. Expected format: SQL //whereclause//");
    } else {
      String sqlClause = matcher.group(1).trim();

      // Optional: Check if SQL clause is not empty
      if (sqlClause.isEmpty()) {
        messages.add("SQL clause is empty in SQL block of table '" + tableName + "'.");
      }
    }
    return messages;
  }
}
