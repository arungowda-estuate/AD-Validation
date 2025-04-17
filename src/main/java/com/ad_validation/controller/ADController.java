package com.ad_validation.controller;

import com.ad_validation.model.SyntaxValidation;
import com.ad_validation.service.ADSyntaxValidation;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class ADController {

  @Autowired private ADSyntaxValidation adSyntaxValidation;

  @PostMapping("/validate")
  public ResponseEntity<SyntaxValidation> validateAdScript(@RequestBody String script) {

    Map<String, List<String>> validationResult = adSyntaxValidation.extractAdScriptsByName(script);

    boolean allScriptsValid = validationResult.values().stream().allMatch(List::isEmpty);

    if (allScriptsValid) {
      return ResponseEntity.ok(
          new SyntaxValidation(
              "Validation passed",
              Map.of("All Scripts", List.of("No Syntax validation errors found."))));
    } else {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new SyntaxValidation("Validation failed", validationResult));
    }
  }
}
