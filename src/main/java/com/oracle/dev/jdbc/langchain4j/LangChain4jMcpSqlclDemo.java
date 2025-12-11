package com.oracle.dev.jdbc.langchain4j;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

public class LangChain4jMcpSqlclDemo {

  // SQLCL path
  private final static String SQLCL_PATH = "C:\\sqlcl\\bin\\sql.exe";

  public static void main(String[] args) {

    System.out.println("=== LangChain4J AI Assistant + SQLcl MCP Server Demo ===");

    try {
      // Test database connection
      testDatabaseConnection();

      // Test SQLCL availability
      testSqlclAvailability();

      // Run the SQLcl MCP Server interactions
      runMcpDemo();

    } catch (Exception e) {
      System.err.println("Error running demo: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void runMcpDemo() throws Exception {
    System.out.println("\n--- Starting the interaction with the SQLcl MCP Server and its tools ---");

    // Create MCP transport for SQLcl using stdio in MCP mode
    McpTransport transport = new StdioMcpTransport.Builder().command(List.of(SQLCL_PATH, "-mcp")).logEvents(true)
        .build();

    // Create MCP client
    McpClient mcpClient = new DefaultMcpClient.Builder().key("SQLCL-MCP-Client").transport(transport).build();

    // Run a health check
    mcpClient.checkHealth();

    // Create MCP tool provider
    McpToolProvider toolProvider = McpToolProvider.builder().mcpClients(mcpClient).build();

    // Tool discovery
    List<ToolSpecification> tools = mcpClient.listTools();
    System.out.println("MCP tools discovered:");
    tools.forEach(t -> System.out.println(" - " + t.name()));

    // Create a simple chat model
    ChatModel chatModel = OpenAiChatModel.builder().apiKey(System.getenv("OPENAI_API_KEY")).modelName(GPT_4_O_MINI)
        .build();

    // Create chat memory
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(1000);

    // Create assistant service with the SQLcl MCP Server tools
    DatabaseAssistant assistant = AiServices.builder(DatabaseAssistant.class).chatModel(chatModel)
        .chatMemory(chatMemory).toolProvider(toolProvider).build();

    // First task
    System.out.println("\n--- List all tables in the schema ---");
    String response = assistant.executeTask("""
        Connect to the database my_mcp_connection and list all tables in the schema.
        """);
    System.out.println("Response 1: " + response);

    // Second task
    System.out.println("\n--- Disconnect from the database, and finish this session ---");
    String responseTwo = assistant.executeTask("""
        Disconnect from the database, and finish this session.
        """);
    System.out.println("Response 2: " + responseTwo);

    // Release resources
    mcpClient.close();
    transport.close();
    System.exit(0);

  }

  private static void testDatabaseConnection() throws Exception {
    System.out.println("\n--- Testing Database Connection ---");
    try (Connection conn = OracleDbUtils.getConnectionFromPooledDataSource()) {
      System.out.println("Database connection successful");

      String sql = "SELECT SYSDATE FROM DUAL";
      try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

        if (rs.next()) {
          System.out.println("Current database time: " + rs.getTimestamp(1));
        }
      }
    }
  }

  private static void testSqlclAvailability() {

    System.out.println("\n--- Testing SQLcl Availability ---");
    ProcessBuilder pb = new ProcessBuilder(SQLCL_PATH, "-V");

    try {
      Process process = pb.start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
          BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }

        StringBuilder errors = new StringBuilder();
        while ((line = errorReader.readLine()) != null) {
          errors.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
          System.out.println("SQLcl is available");
          System.out.println("SQLcl version info: " + output.toString().trim());
        } else {
          System.out.println("✗ SQLcl returned exit code: " + exitCode);
          if (errors.length() > 0) {
            System.out.println("Error output: " + errors.toString().trim());
          }
        }
      } finally {
        process.destroy();
      }

    } catch (IOException | InterruptedException e) {
      System.err.println("✗ Error testing SQLcl: " + e.getMessage());
      Thread.currentThread().interrupt();
    }
  }

}
