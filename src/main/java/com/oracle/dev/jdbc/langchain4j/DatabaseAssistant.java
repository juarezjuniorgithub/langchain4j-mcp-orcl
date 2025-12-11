package com.oracle.dev.jdbc.langchain4j;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface DatabaseAssistant {

  @SystemMessage("You are a helpful Oracle Database Assistant who uses the Oracle SQLcl MCP Server and its MCP tools to execute database operations.")
  String executeTask(@UserMessage String taskInstructions) throws Exception;

}