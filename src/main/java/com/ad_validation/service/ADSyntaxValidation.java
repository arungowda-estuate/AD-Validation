package com.ad_validation.service;

import com.ad_validation.service.primary.PrimaryScriptValidation;
import com.ad_validation.service.relationship.RelScriptValidation;
import com.ad_validation.service.table.TableScriptValidation;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ADSyntaxValidation {

  @Autowired private PrimaryScriptValidation primaryScriptValidation;
  @Autowired private TableScriptValidation tableScriptValidation;
  @Autowired private RelScriptValidation relScriptValidation;

  private static final ExecutorService executor = Executors.newFixedThreadPool(3);

  private List<String> validateScript(String script) {
    List<String> messages = new ArrayList<>();

    // 1. Semicolon check
    List<String> semicolonCheck = checkScriptEndsWithSemicolon(script);
    if (!semicolonCheck.isEmpty()) return semicolonCheck;

    // 3. Normalize whitespace
    String normalizedScript = script.replaceAll("\\s+", " ").trim();
    String upperScript = normalizedScript.toUpperCase();

    // 4. Extract Primary block from CREATE AD to first TABLE
    int createAdIndex = upperScript.indexOf("CREATE AD");
    int tableIndex = upperScript.indexOf("TABLE");

    String primaryBlock;
    int primaryEnd = -1;

    if (createAdIndex != -1 && tableIndex != -1 && tableIndex > createAdIndex) {
      primaryBlock = normalizedScript.substring(createAdIndex, tableIndex).trim();
      primaryEnd = tableIndex;
    } else {
      primaryBlock = "";
      messages.add("Invalid script: Missing TABLE block script must contain least one TABLE block");
      return messages;
    }

    // 5. Extract REL block
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

    // 6. Extract TABLE block (from primaryEnd to REL or end)
    int tableEnd = (relStartIndex != -1) ? relStartIndex : normalizedScript.length();
    String tableBlock = normalizedScript.substring(primaryEnd, tableEnd).trim();

    // 6. Validation logic
    if (primaryBlock.isEmpty()) {
      messages.add("Invalid script: Missing primary block (CREATE AD ... SOLUTION)");
      return messages;
    }

    if (tableBlock.isEmpty() && !relBlock.isEmpty()) {
      messages.add("Invalid script: REL block is present but TABLE block is missing.");
      return messages;
    }

    // 7. Submit validation tasks to executor
    List<Future<List<String>>> futures = new ArrayList<>();

    futures.add(executor.submit(() -> primaryScriptValidation.validatePrimaryScript(primaryBlock)));

    if (!tableBlock.isEmpty()) {
      futures.add(executor.submit(() -> tableScriptValidation.validate(tableBlock)));
    }

    if (!relBlock.isEmpty()) {
      futures.add(executor.submit(() -> relScriptValidation.validateRelScript(relBlock)));
    }

    // 8. Collect results from futures
    for (Future<List<String>> future : futures) {
      try {
        List<String> result = future.get(); // blocking until task is done
        messages.addAll(result);
      } catch (InterruptedException | ExecutionException e) {
        messages.add("Error during script validation: " + e.getMessage());
      }
    }

    return messages;
  }

  private List<String> checkScriptEndsWithSemicolon(String script) {
    List<String> messages = new ArrayList<>();
    String trimmed = script.trim();
    if (trimmed.isEmpty()) {
      messages.add("Script is empty — cannot validate semicolon.");
    } else if (!trimmed.endsWith(";")) {
      messages.add("Script must end with a semicolon ';'.");
    }
    return messages;
  }

  public Map<String, List<String>> extractAdScriptsByName(String fullScript) {
    Map<String, List<String>> resultMap = new LinkedHashMap<>();
    Map<String, String> adScripts = new LinkedHashMap<>();

    if (fullScript == null || fullScript.trim().isEmpty()) {
      resultMap.put("EmptyScript", Collections.singletonList("Script is Empty"));
      return resultMap;
    }

    String normalized = fullScript.replaceAll("\\s+", " ").trim();

    // Match all blocks starting with CREATE AD and ending with ;
    Pattern scriptPattern = Pattern.compile("(?i)(CREATE AD.*?;)");
    Matcher scriptMatcher = scriptPattern.matcher(normalized);

    while (scriptMatcher.find()) {
      String scriptBlock = scriptMatcher.group(1).trim();

      // Extract name between CREATE AD and DESC (or SRCQUAL if DESC not found)
      Pattern namePattern = Pattern.compile("(?i)CREATE AD\\s+(.*?)\\s+(DESC|SRCQUAL)");
      Matcher nameMatcher = namePattern.matcher(scriptBlock);

      String name = "UNKNOWN_AD_" + (adScripts.size() + 1); // fallback name
      if (nameMatcher.find()) {
        name = nameMatcher.group(1).trim();
      }

      adScripts.put(name, scriptBlock);
    }

    return validateAllScripts(adScripts);
  }

  private Map<String, List<String>> validateAllScripts(Map<String, String> scriptMap) {
    Map<String, List<String>> resultMap = new ConcurrentHashMap<>();
    List<Future<?>> futures = new ArrayList<>();

    for (Map.Entry<String, String> entry : scriptMap.entrySet()) {
      String scriptName = entry.getKey();
      String scriptContent = entry.getValue();

      futures.add(
          executor.submit(
              () -> {
                List<String> messages = validateScript(scriptContent);
                resultMap.put(scriptName, messages);
              }));
    }

    // Wait for all validations to finish
    for (Future<?> future : futures) {
      try {
        future.get(); // block until done
      } catch (InterruptedException | ExecutionException e) {
        // In case there's an error with a specific script, you might want to add it as an error map
        // entry
        resultMap.put(
            "ValidationError",
            Collections.singletonList("Error during validation: " + e.getMessage()));
      }
    }
    return resultMap;
  }
}
