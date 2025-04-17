package com.ad_validation.service;

import com.ad_validation.service.primary.PrimaryScriptValidation;
import com.ad_validation.service.relationship.RelScriptValidation;
import com.ad_validation.service.table.TableScriptValidation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ADSyntaxValidation {

  @Autowired private PrimaryScriptValidation primaryScriptValidation;

  @Autowired private TableScriptValidation tableScriptValidation;

  @Autowired private RelScriptValidation relScriptValidation;

  public List<String> validateScript(String script) {
    List<String> messages = new ArrayList<>();

    // 1. Check if script ends with semicolon
    List<String> isEndWithSemicolonScript = checkScriptEndsWithSemicolon(script);
    if (!isEndWithSemicolonScript.isEmpty()) {
      messages.addAll(isEndWithSemicolonScript);
      return messages;
    }

    // 2. Normalize whitespace
    String normalizedScript = script.replaceAll("\\s+", " ").trim();

    // 3. Extract Primary block
    String primaryPattern = "(?i)(CREATE AD .*?SOLUTION\\s+\\d+)";
    Matcher matcherPrimary = Pattern.compile(primaryPattern).matcher(normalizedScript);
    String primaryBlock;
    int primaryEnd = -1;
    if (matcherPrimary.find()) {
      primaryBlock = matcherPrimary.group(1).trim();
      primaryEnd = matcherPrimary.end();
    } else {
      primaryBlock = "";
    }

    // 4. Extract REL block
    String relPattern = "(?i)(REL\\s*\\(.*)";
    Matcher matcherRel = Pattern.compile(relPattern).matcher(normalizedScript);
    String relBlock;
    int relStartIndex = -1;
    if (matcherRel.find()) {
      relBlock = matcherRel.group(1).trim();
      relStartIndex = matcherRel.start();
    } else {
      relBlock = "";
    }

    // 5. Extract TABLE block
    int tableEnd = (relStartIndex != -1) ? relStartIndex : normalizedScript.length();
    String tableBlock =
        (primaryEnd != -1 && primaryEnd < tableEnd)
            ? normalizedScript.substring(primaryEnd, tableEnd).trim()
            : "";

    // 6. Logic validation
    if (primaryBlock.isEmpty()) {
      messages.add("Invalid script: Missing primary block (CREATE AD ... SOLUTION)");
      return messages;
    }

    // Case: Primary + REL, but no Table → Invalid
    if (tableBlock.isEmpty() && !relBlock.isEmpty()) {
      messages.add("Invalid script: REL block is present but TABLE block is missing.");
      return messages;
    }

    // Prepare threads
    List<Thread> threads = new ArrayList<>();
    List<String> primaryMessages = new ArrayList<>();
    List<String> tableMessages = new ArrayList<>();
    List<String> relMessages = new ArrayList<>();

    Thread primaryThread =
        new Thread(
            () -> {
              primaryMessages.addAll(primaryScriptValidation.validatePrimaryScript(primaryBlock));
            });
    threads.add(primaryThread);
    primaryThread.start();

    if (!tableBlock.isEmpty()) {
      Thread tableThread =
          new Thread(
              () -> {
                tableMessages.addAll(tableScriptValidation.validate(tableBlock));
              });
      threads.add(tableThread);
      tableThread.start();
    }

    if (!relBlock.isEmpty()) {
      Thread relThread =
          new Thread(
              () -> {
                relMessages.addAll(relScriptValidation.validateRelScript(relBlock));
              });
      threads.add(relThread);
      relThread.start();
    }

    // Wait for all threads to complete
    for (Thread t : threads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        messages.add("Error occurred while validating: " + e.getMessage());
      }
    }

    // Merge all results
    messages.addAll(primaryMessages);
    messages.addAll(tableMessages);
    messages.addAll(relMessages);

    return messages;
  }

  private List<String> checkScriptEndsWithSemicolon(String script) {
    List<String> messages = new ArrayList<>();

    String trimmed = script.trim();

    if (trimmed.isEmpty()) {
      messages.add("Script is empty — cannot validate semicolon.");
      return messages;
    }

    if (!trimmed.endsWith(";")) {
      messages.add("Script must end with a semicolon ';'.");
    }

    return messages;
  }
}
