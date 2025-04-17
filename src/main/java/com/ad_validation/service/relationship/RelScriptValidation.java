package com.ad_validation.service.relationship;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RelScriptValidation {

  public List<String> validateRelScript(String script) {
    List<String> messages = new ArrayList<>();

    // Find each REL block
    Pattern relBlockPattern = Pattern.compile("REL\\s*\\(//.*?//.*?\\)", Pattern.DOTALL);
    Matcher matcher = relBlockPattern.matcher(script);

    int blockCount = 0;
    while (matcher.find()) {
      String relBlock = matcher.group();
      blockCount++;
      List<String> blockMessages = validateSingleRelBlock(relBlock, blockCount);
      messages.addAll(blockMessages);
    }

    if (blockCount == 0) {
      messages.add("No REL blocks found in the script.");
    }

    // 🧹 Remove all REL blocks from the script
    String cleanedScript = relBlockPattern.matcher(script).replaceAll("").trim();

    // ❌ Check for any non-empty remaining content outside REL blocks
    if (!cleanedScript.replaceAll("[\\s;]", "").isEmpty()) {
      messages.add(
          "Script contains extra content outside REL blocks: '" + cleanedScript.trim() + "'");
    }

    // ✅ Final semicolon check (only if REL is present)
    if (blockCount > 0 && !script.trim().endsWith(";")) {
      messages.add("Script must end with a semicolon (;).");
    }

    return messages;
  }

  private static List<String> validateSingleRelBlock(String block, int blockNumber) {
    List<String> errors = new ArrayList<>();
    String header = "REL Block #" + blockNumber + ": ";

    try {
      Pattern structurePattern =
          Pattern.compile("REL\\s*\\(//\\s*'?([^']+)'?\\s*//\\s*(.*?)\\)", Pattern.DOTALL);
      Matcher structureMatcher = structurePattern.matcher(block);

      if (!structureMatcher.find()) {
        errors.add(header + "Invalid REL block structure.");
        return errors;
      }

      String content = structureMatcher.group(2).trim();
      String[] tokens = content.split("\\s+");
      int i = 0;

      // Required: STATUS
      if (i + 1 >= tokens.length || !tokens[i].equals("STATUS") || !isValidStatus(tokens[i + 1])) {
        errors.add(header + "Invalid or missing STATUS. Found: " + safeToken(tokens, i));
        return errors;
      }
      i += 2;

      // Required: USAGE
      if (i + 1 >= tokens.length || !tokens[i].equals("USAGE") || !isValidUsage(tokens[i + 1])) {
        errors.add(header + "Invalid or missing USAGE. Found: " + safeToken(tokens, i));
        return errors;
      }
      i += 2;

      // Required: Q1
      if (i + 1 >= tokens.length || !tokens[i].equals("Q1") || !isYesOrNo(tokens[i + 1])) {
        errors.add(header + "Invalid or missing Q1. Found: " + safeToken(tokens, i));
        return errors;
      }
      i += 2;

      // Required: Q2
      if (i + 1 >= tokens.length || !tokens[i].equals("Q2") || !isYesOrNo(tokens[i + 1])) {
        errors.add(header + "Invalid or missing Q2. Found: " + safeToken(tokens, i));
        return errors;
      }
      i += 2;

      // Required: LIMIT
      if (i + 1 >= tokens.length || !tokens[i].equals("LIMIT") || !isNumeric(tokens[i + 1])) {
        errors.add(header + "Invalid or missing LIMIT. Found: " + safeToken(tokens, i));
        return errors;
      }
      i += 2;

      // Required: TYPE
      if (i + 1 >= tokens.length || !tokens[i].equals("TYPE") || !isValidType(tokens[i + 1])) {
        errors.add(header + "Invalid or missing TYPE. Found: " + safeToken(tokens, i));
        return errors;
      }
      i += 2;

      // Required: PAR
      if (i + 1 >= tokens.length || !tokens[i].equals("PAR")) {
        errors.add(header + "Missing PAR block.");
        return errors;
      }
      i += 2;

      // Optional: PAR_ACCESS
      if (i < tokens.length && tokens[i].equals("PAR_ACCESS")) {
        if (i + 1 >= tokens.length || !isValidAccess(tokens[i + 1])) {
          errors.add(header + "Invalid or missing PAR_ACCESS value.");
          return errors;
        }
        i += 2;
      }

      // Optional: PAR_KEYLIMIT
      if (i < tokens.length && tokens[i].equals("PAR_KEYLIMIT")) {
        if (i + 1 >= tokens.length || !isNumeric(tokens[i + 1])) {
          errors.add(header + "Invalid or missing PAR_KEYLIMIT value.");
          return errors;
        }
        i += 2;
      }

      // Required: CHI
      if (i + 1 >= tokens.length || !tokens[i].equals("CHI")) {
        errors.add(header + "Missing CHI block.");
        return errors;
      }
      i += 2;

      // Optional: CHI_ACCESS
      if (i < tokens.length && tokens[i].equals("CHI_ACCESS")) {
        if (i + 1 >= tokens.length || !isValidAccess(tokens[i + 1])) {
          errors.add(header + "Invalid or missing CHI_ACCESS value.");
          return errors;
        }
        i += 2;
      }

      // Optional: CHI_KEYLIMIT
      if (i < tokens.length && tokens[i].equals("CHI_KEYLIMIT")) {
        if (i + 1 >= tokens.length || !isNumeric(tokens[i + 1])) {
          errors.add(header + "Invalid or missing CHI_KEYLIMIT value.");
          return errors;
        }
        i += 2;
      }

      // Extra tokens
      if (i < tokens.length) {
        errors.add(header + "Unexpected extra tokens starting from: " + tokens[i]);
        return errors;
      }

    } catch (Exception e) {
      errors.add(header + "Exception occurred: " + e.getMessage());
    }

    return errors;
  }

  // Helpers
  private static String safeToken(String[] tokens, int index) {
    return index < tokens.length ? tokens[index] : "[END OF INPUT]";
  }

  private static boolean isValidStatus(String s) {
    return s.matches("K|NEWK|UNK|NEWUNK");
  }

  private static boolean isValidUsage(String s) {
    return s.matches("D|I|S");
  }

  private static boolean isYesOrNo(String s) {
    return s.equals("Y") || s.equals("N");
  }

  private static boolean isNumeric(String s) {
    return s.matches("\\d+");
  }

  private static boolean isValidType(String s) {
    return s.matches("(?i)Oracle|PST|PST_GENERIC|UNK");
  }

  private static boolean isValidAccess(String s) {
    return s.matches("D|K|S");
  }
}
