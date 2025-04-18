package com.ad_validation.service;

import com.ad_validation.service.archacts.ArchactsBlockScriptValidation;
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
  @Autowired private ArchactsBlockScriptValidation archactsBlockScriptValidation;

  private static final ExecutorService executor = Executors.newFixedThreadPool(20);

  private List<String> validateScript(String script) {
    List<String> messages = new ArrayList<>();

    // 1. Semicolon check
    List<String> semicolonCheck = checkScriptEndsWithSemicolon(script);
    if (!semicolonCheck.isEmpty()) return semicolonCheck;

    // 2. Normalize whitespace
    String normalizedScript = script.replaceAll("\\s+", " ").trim();
    String upperScript = normalizedScript.toUpperCase();

    // 3. Extract PRIMARY block
    int createAdIndex = upperScript.indexOf("CREATE AD");
    int tableIndex = upperScript.indexOf("TABLE");

    String primaryBlock;
    int primaryEnd = -1;

    if (createAdIndex != -1 && tableIndex != -1 && tableIndex > createAdIndex) {
      primaryBlock = normalizedScript.substring(createAdIndex, tableIndex).trim();
      primaryEnd = tableIndex;
    } else {
      primaryBlock = "";
      messages.add(
          "❌ Invalid script: Missing TABLE block — script must contain at least one TABLE block.");
      return messages;
    }

    // 4. Extract REL blocks (optional)
    List<String> relBlocks = new ArrayList<>();
    int relStartIndex = -1;
    Pattern relPattern =
        Pattern.compile("REL\\s*\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    Matcher relMatcher = relPattern.matcher(normalizedScript);
    while (relMatcher.find()) {
      String rel = relMatcher.group().trim();
      relBlocks.add(rel);
      if (relStartIndex == -1) {
        relStartIndex = relMatcher.start();
      }
    }
    String relBlockScript = String.join(" ", relBlocks);

    // 5. Extract ARCHACTS blocks (optional)
    List<String> archactsBlocks = new ArrayList<>();
    int archactsStartIndex = -1;
    Pattern archPattern =
        Pattern.compile(
            "ARCHACTS\\s*\\(.*?SQL\\s*//.*?//.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    Matcher archMatcher = archPattern.matcher(normalizedScript);
    while (archMatcher.find()) {
      String arch = archMatcher.group().trim();
      archactsBlocks.add(arch);
      if (archactsStartIndex == -1) {
        archactsStartIndex = archMatcher.start();
      }
    }
    String archactsBlockScript = String.join(" ", archactsBlocks);

    // 6. Extract TABLE block(s): from PRIMARY end up to REL, or ARCHACTS, or semicolon
    int tableEnd;
    if (relStartIndex != -1) {
      tableEnd = relStartIndex;
    } else if (archactsStartIndex != -1) {
      tableEnd = archactsStartIndex;
    } else {
      int semicolonIndex = normalizedScript.indexOf(";", primaryEnd);
      tableEnd = (semicolonIndex != -1) ? semicolonIndex + 1 : normalizedScript.length();
    }
    String tableBlock = normalizedScript.substring(primaryEnd, tableEnd).trim();

    // 7. Extract and combine each TABLE(...) block
    List<String> tableBlocks = new ArrayList<>();
    Pattern tablePattern =
        Pattern.compile(
            "TABLE\\s*\\(.*?EXTRROWID\\s+[YN]\\s*\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    Matcher tableMatcher = tablePattern.matcher(tableBlock);
    while (tableMatcher.find()) {
      tableBlocks.add(tableMatcher.group().trim());
    }
    String combinedTableBlock = String.join(" ", tableBlocks);

    // 7. Structural validation
    if (primaryBlock.isEmpty()) {
      messages.add("Invalid script: Missing PRIMARY block (CREATE AD ... )");
      return messages;
    }

    if (tableBlock.isEmpty() && (!relBlocks.isEmpty() || !archactsBlocks.isEmpty())) {
      messages.add("Invalid script: TABLE block is missing.");
      return messages;
    }

    // 8. Submit validation tasks
    List<Future<List<String>>> futures = new ArrayList<>();

    futures.add(executor.submit(() -> primaryScriptValidation.validatePrimaryScript(primaryBlock)));

    if (!tableBlock.isEmpty()) {
      futures.add(
          executor.submit(() -> tableScriptValidation.validateTableFullScript(combinedTableBlock)));
    }

    if (!archactsBlocks.isEmpty()) {
      futures.add(
          executor.submit(
              () -> archactsBlockScriptValidation.validateArchactsBlock(archactsBlockScript)));
    }

    if (!relBlocks.isEmpty()) {
      futures.add(executor.submit(() -> relScriptValidation.validateRelScript(relBlockScript)));
    }

    // 9. Collect results
    for (Future<List<String>> future : futures) {
      try {
        messages.addAll(future.get());
      } catch (InterruptedException | ExecutionException e) {
        messages.add("Error during script validation: " + e.getMessage());
        return messages;
      }
    }

    return messages;
  }

  private List<String> checkScriptEndsWithSemicolon(String script) {
    List<String> messages = new ArrayList<>();
    String trimmed = script.trim();
    if (trimmed.isEmpty()) {
      messages.add("Script is empty — cannot validate semicolon.");
      return messages;
    } else if (!trimmed.endsWith(";")) {
      messages.add("Script must end with a semicolon ';'.");
      return messages;
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
        resultMap.put("Validation error ", Collections.singletonList("Invalid Script."));
      }
    }
    return resultMap;
  }
}
