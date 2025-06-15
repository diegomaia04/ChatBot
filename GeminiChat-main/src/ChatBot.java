import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatBot {

    private static final String API_KEY = "API_KEY";
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:streamGenerateContent";

    private static final String SYSTEM_PROMPT = """
            Você é um assistente de banco, com especialidade em emprestimos.
            Seu nome é "ChatBot".
            Seja sempre educado, prestativo e responda de forma concisa.
            Não responda assuntos que não estejam ligados com emprestimos e banco.
            Todas as respostas devem ser em texto simples (plain text).
            """;

    private static record Message(String role, String text) {}
    private static final List<Message> history = new ArrayList<>();
    private static final int HISTORY_CAPACITY = 20; 

    public static void main(String[] args) throws IOException, InterruptedException {
        if (API_KEY == null || API_KEY.isBlank()) {
            System.err.println("Erro: A variável de ambiente GEMINI_API_KEY não foi definida.");
            System.exit(1);
        }

        try (var scanner = new Scanner(System.in)) {
            System.out.println("ChatBot: Olá! Sou o ChatBot, seu assistente de emprestimos. Como posso ajudar?");
            while (true) {
                System.out.print("Nome do usuario: ");
                var userInput = scanner.nextLine();

                if (userInput.equalsIgnoreCase("sair")) {
                    System.out.println("ChatBot: Obrigado por usar nosso Atendimento");
                    break; 
                }
                if (userInput.isBlank()) {
                    System.out.println("ChatBot: Por favor, digite sua dúvida.");
                    System.out.println(); 
                    continue;
                }

                String aiResponse = sendRequest(userInput);
                updateHistory(userInput, aiResponse);
            }
        }
    }

    private static String sendRequest(String prompt) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        String jsonRequest = buildJsonRequest(prompt);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(URL + "?alt=sse&key=" + API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return processResponse(response);
    }

    
    private static String buildJsonRequest(String newPrompt) {
        
        String historyJson = history.stream()
                .map(msg -> String.format("{\"role\": \"%s\", \"parts\": [{\"text\": \"%s\"}]}", msg.role(), escapeJson(msg.text())))
                .collect(Collectors.joining(","));
        
        
        String userPromptJson = String.format("{\"role\": \"user\", \"parts\": [{\"text\": \"%s\"}]}", escapeJson(newPrompt));

        
        String contents = historyJson.isEmpty() ? userPromptJson : historyJson + "," + userPromptJson;

        
        return String.format("""
                {
                  "system_instruction": {
                    "parts": [{"text": "%s"}]
                  },
                  "contents": [%s]
                }
                """, escapeJson(SYSTEM_PROMPT), contents);
    }
    
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static String processResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) {
            System.err.println("Erro na API: " + response.statusCode() + " - " + response.body());
            return "ChatBot: Desculpe, estou com um problema técnico no momento. Tente novamente mais tarde.";
        }

        var pattern = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");
        var answer = new StringBuilder();

        try (var reader = new BufferedReader(new StringReader(response.body()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        
                        String textChunk = matcher.group(1).translateEscapes();
                        answer.append(textChunk);
                        System.out.print(textChunk); 
                    }
                }
            }
        }

        System.out.println(); 
        String reposta = "ChatBot: " + answer.toString(); 
        // System.out.println(reposta);
        
        return reposta;
    }

   
    private static void updateHistory(String userPrompt, String modelResponse) {
        
        if (history.size() >= HISTORY_CAPACITY) {
            history.remove(0);
            history.remove(0); 
        }
        history.add(new Message("user", userPrompt));
        history.add(new Message("model", modelResponse));
    }
}

