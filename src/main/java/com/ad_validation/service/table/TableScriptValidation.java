package com.ad_validation.service.table;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TableScriptValidation {

  @Autowired private ColumnBlockValidator columnBlockValidator;

  @Autowired private SqlBlockValidator sqlBlockValidator;

  @Autowired private SortBlockValidator sortBlockValidator;

  @Autowired private ArcindexBlockValidator arcindexBlockValidator;

  private static final List<String> ALLOWED_VARDELIM_VALUES =
      Arrays.asList("~", "!", "@", "$", ":", "%", "+", "?");

  public List<String> validate(String script) {
    List<String> messages = new ArrayList<>();

    // Normalize the script: remove line breaks and extra spaces
    String normalizedScript = script.replaceAll("\\s+", " ").trim();

    // Track parentheses and block validity
    int openParens = 0;
    int closeParens = 0;
    boolean hasValidMainBlock = false;
    boolean allBlocksValid = true;

    int index = 0;
    while (index < normalizedScript.length()) {
      int nextParen = normalizedScript.indexOf("(", index);
      if (nextParen == -1) break;

      String beforeParen = normalizedScript.substring(index, nextParen).trim();
      String[] tokens = beforeParen.split("\\s+");
      String blockName = tokens.length > 0 ? tokens[tokens.length - 1].toUpperCase() : "";

      if (!blockName.equals("TABLE") && !blockName.equals("ARCHACTS")) {
        messages.add(
            "Invalid block found "
                + blockName
                + "(...)' — , only TABLE(...) and ARCHACTS(...) are allowed.");
        allBlocksValid = false;
      } else {
        hasValidMainBlock = true;
      }

      openParens++;
      index = nextParen + 1;
      int parenDepth = 1;
      while (index < normalizedScript.length() && parenDepth > 0) {
        char c = normalizedScript.charAt(index);
        if (c == '(') {
          parenDepth++;
          openParens++;
        } else if (c == ')') {
          parenDepth--;
          closeParens++;
        }
        index++;
      }

      if (parenDepth != 0) {
        messages.add("Unbalanced parentheses in block starting at index " + nextParen);
        allBlocksValid = false;
      }
    }

    if (openParens != closeParens) {
      messages.add("Script has unmatched parentheses — check for missing '(' or ')' characters.");
      allBlocksValid = false;
    }

    if (!hasValidMainBlock) {
      messages.add("Script must contain at least one valid TABLE(...) or ARCHACTS(...) block.");
    }

    // ✅ NEW: Check for extra characters outside valid blocks
    if (messages.isEmpty() && allBlocksValid) {
      List<String> extraCheckMessages = checkForExtraCharacters(script);
      if (!extraCheckMessages.isEmpty()) {
        return extraCheckMessages; // return early if invalid content found
      }
      return validateTableScript(script);
    }
    return messages;
  }

  private List<String> checkForExtraCharacters(String script) {
    List<String> messages = new ArrayList<>();
    String normalizedScript = script.replaceAll("\\s+", " ").trim();

    List<String> validBlocks = new ArrayList<>();
    int index = 0;

    while (index < normalizedScript.length()) {
      int tableIndex = normalizedScript.indexOf("TABLE", index);
      int archactsIndex = normalizedScript.indexOf("ARCHACTS", index);

      int nextBlockIndex = -1;
      String blockType = null;

      if (tableIndex != -1 && (archactsIndex == -1 || tableIndex < archactsIndex)) {
        nextBlockIndex = tableIndex;
        blockType = "TABLE";
      } else if (archactsIndex != -1) {
        nextBlockIndex = archactsIndex;
        blockType = "ARCHACTS";
      }

      if (nextBlockIndex == -1) break;

      int parenStart = normalizedScript.indexOf("(", nextBlockIndex);
      if (parenStart == -1) {
        messages.add("Missing opening parenthesis after " + blockType + " block.");
        break;
      }

      int parenDepth = 1;
      int i = parenStart + 1;

      while (i < normalizedScript.length() && parenDepth > 0) {
        char c = normalizedScript.charAt(i);
        if (c == '(') parenDepth++;
        else if (c == ')') parenDepth--;
        i++;
      }

      if (parenDepth == 0) {
        // Include everything from block start to the closing ')'
        String fullBlock = normalizedScript.substring(nextBlockIndex, i).trim();
        validBlocks.add(fullBlock);
        index = i;
      } else {
        messages.add(
            "Unbalanced parentheses in "
                + blockType
                + " block starting at index "
                + nextBlockIndex);
        break;
      }
    }

    // Remove all valid blocks from the original script
    String cleanedScript = normalizedScript;
    for (String block : validBlocks) {
      cleanedScript = cleanedScript.replace(block, "").trim();
    }
    if (cleanedScript.equals(";")) {
      cleanedScript = "";
    }

    if (!cleanedScript.isEmpty()) {
      messages.add(
          "Invalid content found outside TABLE(...) or ARCHACTS(...) blocks: \""
              + cleanedScript
              + "\"");
    }

    return messages;
  }

  public List<String> validateTableScript(String script) {

    List<String> messages = new ArrayList<>();
    int index = 0;
    boolean foundValidTable = false;

    while (index < script.length()) {
      int tableStart = script.indexOf("TABLE", index);
      if (tableStart == -1) break;

      int openParen = script.indexOf("(", tableStart);
      if (openParen == -1) {
        messages.add("'TABLE' found but missing opening '('");
        break;
      }

      int closeParen = findMatchingClosingParen(script, openParen);
      if (closeParen == -1) {
        messages.add("Unmatched parentheses in TABLE block starting at index " + tableStart);
        break;
      }

      String fullBlock = script.substring(tableStart, closeParen + 1);

      if (!fullBlock.matches("(?s).*EXTRROWID\\s+[YN]\\s*\\)\\s*$")) {
        messages.add("TABLE block found but missing required 'EXTRROWID Y/N' before closing ')'");
      } else {
        String contentInside = script.substring(openParen + 1, closeParen).trim();

        messages.addAll(validateWrappedTableBlock(contentInside));
        foundValidTable = true;
      }

      index = closeParen + 1;
    }

    if (!foundValidTable) {
      messages.add("No valid TABLE blocks found.");
    }

    return messages;
  }

  private int findMatchingClosingParen(String str, int openIndex) {
    int depth = 1;
    for (int i = openIndex + 1; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c == '(') depth++;
      else if (c == ')') depth--;
      if (depth == 0) return i;
    }
    return -1;
  }

  private List<String> validateWrappedTableBlock(String tableContent) {
    List<String> messages = new ArrayList<>();

    try {
      messages.addAll(validateSingleTableBlock(tableContent));
    } catch (Exception e) {

      messages.add("Error while validating TABLE block: " + tableContent);
    }
    return messages;
  }

  private List<String> validateSingleTableBlock(String content) {
    List<String> messages = new ArrayList<>();
    String originalContent = content;

    try {
      // Normalize whitespace
      content = content.replaceAll("\\s+", " ").trim();

      // Capture the mandatory fixed keywords in strict order
      Pattern fixedPattern =
          Pattern.compile(
              "^([A-Z0-9_]+) ACCESS SUID REF ([YN]) DAA ([YN]) UR ([YN]) DBCIP ([YN]) PREDOP ([AO])"
                  + "( VARDELIM ([~!@:$%+?]))? COLFLAG ([YN])(.*)EXTRROWID ([YN])$",
              Pattern.CASE_INSENSITIVE);

      Matcher matcher = fixedPattern.matcher(content);

      if (!matcher.find()) {
        messages.add("TABLE block format is invalid or out of order: " + content);
        return messages;
      }

      String tableName = matcher.group(1);
      String ref = matcher.group(2);
      String daa = matcher.group(3);
      String ur = matcher.group(4);
      String dbcip = matcher.group(5);
      String predop = matcher.group(6);
      String vdelim = matcher.group(8); // Optional
      String colflag = matcher.group(9);
      String optionalContent = matcher.group(10).trim();
      String extrrowid = matcher.group(11);

      // ✅ Validate VARDELIM
      if (vdelim != null && !ALLOWED_VARDELIM_VALUES.contains(vdelim)) {
        messages.add("VARDELIM must be one of ~, !, @, $, :, %, +, ?, found: " + vdelim);
      }

      // ✅ Validate optional content if present
      if (!optionalContent.isEmpty()) {
        // 🚨 Strip all known optional blocks — anything left is invalid
        String leftover = removeOptionalBlocks(optionalContent);
        if (!leftover.isEmpty()) {
          messages.add("Invalid characters or statements found after COLFLAG: '" + leftover + "'");
          return messages;
        }

        // ✅ Pre-check syntax of optional blocks
        List<String> preCheck = preValidateOptionalBlocks(optionalContent, tableName);
        if (!preCheck.isEmpty()) {
          messages.addAll(preCheck);
          return messages;
        }

        // ✅ Deep validation of optional blocks
        messages.addAll(validateOptionalBlocks(optionalContent, tableName));
      }

    } catch (Exception e) {
      messages.add("Error while validating TABLE block: " + originalContent);
    }

    return messages;
  }

  // ✅ Add this helper at the bottom of your TableValidationService class
  private String removeOptionalBlocks(String content) {
    // Matches COLUMN(...), ARCIDX(...), etc.
    String pattern1 = "(COLUMN|ARCIDX|SORT|FILEATTACH|ARCHACTS)\\s*\\(.*?\\)";
    // Matches SQL //...//
    String pattern2 = "SQL\\s*//.*?//";

    Pattern combined = Pattern.compile(pattern1 + "|" + pattern2, Pattern.DOTALL);
    Matcher matcher = combined.matcher(content);

    String cleaned = matcher.replaceAll("").trim();
    return cleaned.replaceAll("\\s+", ""); // normalize whitespace
  }

  private List<String> validateOnlyAllowedOptionalBlocks(String optionalContent) {
    List<String> messages = new ArrayList<>();

    // Extract all blocks after COLFLAG using regex
    Pattern pattern = Pattern.compile("(\\b[A-Z]+)\\s*\\(");
    Matcher matcher = pattern.matcher(optionalContent);

    while (matcher.find()) {
      String blockName = matcher.group(1);
      if (!isAllowedOptionalBlock(blockName)) {
        messages.add(
            "Invalid block or content '"
                + blockName
                + "' found after COLFLAG — only COLUMN, ARCIDX, SQL, SORT, FILEATTACH, ARCHACTS are allowed.");
      }
    }

    // Also check for any non-block stray content (e.g., text or symbols not part of any block)
    // Remove all allowed blocks from content for stray check
    String cleaned = optionalContent;
    Pattern blockExtract = Pattern.compile("\\b[A-Z]+\\s*\\(.*?\\)", Pattern.DOTALL);
    Matcher blockMatcher = blockExtract.matcher(cleaned);
    while (blockMatcher.find()) {
      cleaned = cleaned.replace(blockMatcher.group(), "");
    }

    cleaned = cleaned.replaceAll("\\s+", "").trim();
    if (!cleaned.isEmpty()) {
      messages.add("Invalid characters or statements found after COLFLAG: '" + cleaned + "'");
    }

    return messages;
  }

  private List<String> preValidateOptionalBlocks(String tableBlockContent, String tableName) {
    List<String> errors = new ArrayList<>();

    // Allowed optional blocks
    Set<String> allowedBlocks = Set.of("COLUMN", "ARCIDX", "SQL", "SORT", "FILEATTACH", "ARCHACTS");

    // Track cleaned content
    StringBuilder cleanedContent = new StringBuilder(tableBlockContent);

    // Match optional blocks
    //        Pattern blockPattern = Pattern.compile("(\\w+)(\\s*\\((.*?)\\)|\\s*//.*?//)",
    // Pattern.DOTALL);
    Pattern blockPattern = Pattern.compile("(\\w+)(\\s*\\((.*?)\\)|\\s*//.*?//)", Pattern.DOTALL);

    Matcher matcher = blockPattern.matcher(tableBlockContent);

    while (matcher.find()) {
      String blockName = matcher.group(1).trim();
      String fullMatch = matcher.group(0);

      if (!allowedBlocks.contains(blockName)) {
        errors.add("Invalid optional block: " + blockName + " in table: " + tableName);
        continue;
      }

      // Special validation for SQL
      if (blockName.equals("SQL")) {
        if (!fullMatch.matches("SQL\\s*//.*?//")) {
          errors.add(
              "SQL block must be in format: SQL //your SQL statement// in table: " + tableName);
          continue;
        }
      }

      // Clean valid block
      cleanedContent = new StringBuilder(cleanedContent.toString().replace(fullMatch, ""));
    }

    // Check if anything is left after removing all valid optional blocks
    String remaining = cleanedContent.toString().replaceAll("[\\s;()]", "");

    if (!remaining.isEmpty()) {
      errors.add(
          "TABLE block contains invalid or extra content outside recognized optional blocks in table: "
              + tableName
              + " → '"
              + remaining
              + "'");
    }

    return errors;
  }

  private boolean isAllowedOptionalBlock(String name) {
    return Set.of("COLUMN", "ARCIDX", "SQL", "SORT", "FILEATTACH", "ARCHACTS")
        .contains(name.toUpperCase());
  }

  private List<String> validateOptionalBlocks(String content, String tableName) {

    List<String> messages = new ArrayList<>();
    content = content.trim();

    // Define the order of optional block types
    LinkedHashMap<String, String> blockTypes = new LinkedHashMap<>();
    blockTypes.put("COLUMN", "COLUMN\\s*\\((.*?)\\)");
    blockTypes.put("SQL", "SQL\\s*\\((.*?)\\)");
    blockTypes.put("SORT", "SORT\\s*\\((.*?)\\)");
    blockTypes.put("ARCIDX", "ARCIDX\\s*\\((.*?)\\)");

    int lastIndex = 0;
    for (Map.Entry<String, String> entry : blockTypes.entrySet()) {
      String type = entry.getKey();
      Pattern p = Pattern.compile(entry.getValue(), Pattern.DOTALL);
      Matcher m = p.matcher(content);

      while (m.find()) {
        String blockContent = m.group(0).trim();

        lastIndex = m.end();

        switch (type) {
          case "COLUMN":
            messages.addAll(columnBlockValidator.validateColumnBlock(blockContent, tableName));
            break;
          case "SQL":
            messages.addAll(sqlBlockValidator.validateSqlBlock(blockContent, tableName));
            break;
          case "ARCIDX":
            messages.addAll(arcindexBlockValidator.validateArcIdxBlock(blockContent, tableName));
            break;
          case "SORT":
            messages.addAll(sortBlockValidator.validateSortBlock(blockContent, tableName));
            break;
        }
      }
    }
    return messages;
  }
}
