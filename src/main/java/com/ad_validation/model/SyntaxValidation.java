package com.ad_validation.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.context.annotation.ComponentScan;

@Data
@AllArgsConstructor
@ComponentScan
public class SyntaxValidation {
  private String status;
  private Map<String, List<String>> messages;

  @Override
  public String toString() {
    return "Status  : " + status + "\n" + "Message : " + messages;
  }
}
