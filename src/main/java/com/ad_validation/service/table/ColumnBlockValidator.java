package com.ad_validation.service.table;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ColumnBlockValidator {
  public List<String> validateColumnBlock(String script, String tableName) {

    List<String> messages = new ArrayList<>();
    Pattern columnPattern = Pattern.compile("COLUMN\\s*\\((.*?)\\)", Pattern.DOTALL);
    Matcher matcher = columnPattern.matcher(script);

    List<String> errorMessages = new ArrayList<>();
    int blockIndex = 1;
    while (matcher.find()) {
      String block = matcher.group(1).trim();
      String[] tokens = block.split("\\s+");
      String blockTrimmed = block.replaceAll("\\s+", " ");

      int i = 0;
      if (tokens.length < 11) {
        errorMessages.add(
            "COLUMN Block "
                + blockIndex
                + ": Invalid structure or missing keywords in ."
                + tableName);
      } else {
        // Extract values
        String columnName = tokens[i++];
        if (!"DISP".equalsIgnoreCase(tokens[i])) {
          errorMessages.add(
              "COLUMN Block "
                  + blockIndex
                  + ": Missing or misordered 'DISP' keyword in ."
                  + tableName);
        } else {
          i++;
          String dispVal = tokens[i++];
          if (!dispVal.matches("[YN]")) {
            errorMessages.add(
                "COLUMN Block " + blockIndex + ": DISP must be 'Y' or 'N' in ." + tableName);
          }
        }

        if (!"ACCESS".equalsIgnoreCase(tokens[i])) {
          errorMessages.add(
              "COLUMN Block " + blockIndex + ": 'ACCESS' must come after 'DISP' in ." + tableName);
        } else {
          i++;
          String accessVal = tokens[i++];
          if (!accessVal.equals("U")) {
            errorMessages.add(
                "COLUMN Block " + blockIndex + ": ACCESS must be 'U' in ." + tableName);
          }
        }

        if (!"HEADING".equalsIgnoreCase(tokens[i])) {
          errorMessages.add(
              "COLUMN Block "
                  + blockIndex
                  + ": 'HEADING' must come after 'ACCESS' in ."
                  + tableName);
        } else {
          i++;
          String headingVal = tokens[i++];
          if (!headingVal.matches("[NL][CLR]")) {
            errorMessages.add(
                "COLUMN Block "
                    + blockIndex
                    + ": HEADING must be two letters - first from [N|L], second from [C|L|R] in ."
                    + tableName);
          }
        }

        if (!"NATIVELOB".equalsIgnoreCase(tokens[i])) {
          errorMessages.add(
              "COLUMN Block "
                  + blockIndex
                  + ": 'NATIVELOB' must come after 'HEADING' in ."
                  + tableName);
        } else {
          i++;
          String lobVal = tokens[i++];
          if (!lobVal.matches("[YN]")) {
            errorMessages.add(
                "COLUMN Block " + blockIndex + ": NATIVELOB must be 'Y' or 'N' in ." + tableName);
          }
        }

        if (!"EXTRACT".equalsIgnoreCase(tokens[i])) {
          errorMessages.add(
              "COLUMN Block "
                  + blockIndex
                  + ": 'EXTRACT' must come after 'NATIVELOB' in ."
                  + tableName);
        } else {
          i++;
          String extractVal = tokens[i++];
          if (!extractVal.matches("[YN]")) {
            errorMessages.add(
                "COLUMN Block " + blockIndex + ": EXTRACT must be 'Y' or 'N' in ." + tableName);
          }
        }

        // Optional PRED
        if (i < tokens.length) {
          if (!"PRED".equalsIgnoreCase(tokens[i])) {
            errorMessages.add(
                "COLUMN Block "
                    + blockIndex
                    + ": Unexpected token after EXTRACT. Only optional 'PRED' is allowed at the end in "
                    + tableName
                    + ".");
          } else {
            i++;
            if (i >= tokens.length) {
              errorMessages.add(
                  "COLUMN Block "
                      + blockIndex
                      + ": PRED must be followed by a value wrapped in //...//.");
            } else {
              String predValue = tokens[i];

              // Unescape any escaped double quotes inside the value
              predValue = predValue.replace("\\\"", "\"").trim();

              // Validate that the value is wrapped in double slashes
              if (!(predValue.startsWith("//") && predValue.endsWith("//"))) {
                errorMessages.add(
                    "COLUMN Block "
                        + blockIndex
                        + ": PRED value must be wrapped in double slashes (e.g., //='VAL'//).");
              }
            }
          }
        }
      }

      blockIndex++;
    }
    if (!errorMessages.isEmpty()) {
      messages.addAll(errorMessages);
    }
    if (blockIndex == 1) {
      messages.add("⚠️ No COLUMN blocks found in script.");
    }
    return messages;
  }
}
