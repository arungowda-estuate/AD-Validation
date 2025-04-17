package com.ad_validation.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.context.annotation.ComponentScan;

@Data
@AllArgsConstructor
@ComponentScan
public class SyntaxValidation {
  private String status;
  private List<String> messages;

  @Override
  public String toString() {
    return "Status  : " + status + "\n" + "Message : " + messages;
  }
}
