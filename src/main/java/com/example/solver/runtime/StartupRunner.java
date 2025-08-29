package com.example.solver.runtime;

import com.example.solver.config.AppProperties;
import com.example.solver.dto.GenerateWebhookRequest;
import com.example.solver.dto.GenerateWebhookResponse;
import com.example.solver.dto.SubmitRequest;
import com.example.solver.solve.SqlSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Component
public class StartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final AppProperties props;
    private final WebClient webClient;
    private final SqlSolver sqlSolver;

    public StartupRunner(AppProperties props) {
        this.props = props;
        this.webClient = WebClient.builder().build();
        this.sqlSolver = new SqlSolver();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting flow: generate webhook → solve SQL → submit");

        // 1) Generate webhook
        GenerateWebhookRequest req =
                new GenerateWebhookRequest(props.getName(), props.getRegNo(), props.getEmail());

        log.info("Calling generateWebhook: {}", props.getGenerateWebhookUrl());

        GenerateWebhookResponse resp = webClient.post()
                .uri(props.getGenerateWebhookUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(req))
                .retrieve()
                .bodyToMono(GenerateWebhookResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        if (resp == null) {
            throw new IllegalStateException("No response from generateWebhook API");
        }
        log.info("Received webhook: {}", resp.getWebhook());
        log.debug("Received accessToken (JWT): {}", resp.getAccessToken());

        // 2) Solve SQL question
        String questionUrl = isRegNoOdd(props.getRegNo())
                ? "https://drive.google.com/file/d/1IeSI6l6KoSQAFfRihIT9tEDICtoz-G/view?usp=sharing" // Q1
                : "https://drive.google.com/file/d/143MR5cLFrlNEuHzzWJ5RHnEWuijuM9X/view?usp=sharing"; // Q2
        log.info("RegNo: {} → Selected question: {}", props.getRegNo(), questionUrl);

        String injected = firstNonBlank(System.getenv("FINAL_QUERY"), props.getFinalQuery());
        String finalQuery = (injected != null) ? injected : sqlSolver.solve(questionUrl, props.getRegNo());

        if (finalQuery == null || finalQuery.isBlank()) {
            throw new IllegalStateException("Final SQL query is empty. Provide app.final-query or extend SqlSolver.");
        }
        log.info("Final SQL computed: {}", finalQuery);

        // Store result locally
        Path out = Path.of("build/solution.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, finalQuery);
        log.info("Saved final query to {}", out.toAbsolutePath());

        // 3) Submit to webhook
        String targetUrl = (resp.getWebhook() != null && !resp.getWebhook().isBlank())
                ? resp.getWebhook() : props.getFallbackSubmitUrl();

        String token = normalizeAuth(resp.getAccessToken());
        log.info("Submitting to: {}", targetUrl);

        String submitResponse = webClient.post()
                .uri(targetUrl)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new SubmitRequest(finalQuery)))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        log.info("Submission response: {}", submitResponse);
    }

    private static boolean isRegNoOdd(String regNo) {
        if (regNo == null || regNo.length() < 2) return true;
        char d1 = regNo.charAt(regNo.length() - 2);
        char d2 = regNo.charAt(regNo.length() - 1);
        int lastTwo;
        try {
            lastTwo = Integer.parseInt("" + d1 + d2);
        } catch (NumberFormatException e) {
            int last = Character.isDigit(d2) ? (d2 - '0') : 1;
            return (last % 2) == 1;
        }
        return (lastTwo % 2) == 1;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String normalizeAuth(String token) {
        if (token == null) return "";
        String t = token.trim();
        if (t.toLowerCase().startsWith("bearer ")) return t;
        return t;
    }
}
