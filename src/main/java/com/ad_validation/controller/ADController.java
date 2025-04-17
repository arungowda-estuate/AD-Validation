package com.ad_validation.controller;

import com.ad_validation.model.SyntaxValidation;
import com.ad_validation.service.ADSyntaxValidation;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class ADController {

  @Autowired private ADSyntaxValidation adSyntaxValidation;

  @PostMapping("/validate")
  public ResponseEntity<SyntaxValidation> validateAdScript(@RequestBody String script) {

    List<String> messages = adSyntaxValidation.validateScript(script);
    if (messages.isEmpty()) {
      return new ResponseEntity<>(
          new SyntaxValidation("Validation Passed", List.of("No Errors")), HttpStatus.ACCEPTED);
    }
    return new ResponseEntity<>(
        new SyntaxValidation("Validation Failed", messages), HttpStatus.BAD_REQUEST);
  }
}
