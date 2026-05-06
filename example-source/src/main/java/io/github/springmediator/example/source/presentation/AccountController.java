package io.github.springmediator.example.source.presentation;

import io.github.springmediator.example.source.application.account.commands.CloseAccountCommand;
import io.github.springmediator.example.source.application.account.commands.DepositMoneyCommand;
import io.github.springmediator.example.source.application.account.commands.OpenAccountCommand;
import io.github.springmediator.example.source.application.account.commands.WithdrawMoneyCommand;
import io.github.springmediator.example.source.application.account.queries.GetAccountQuery;
import io.github.springmediator.example.source.domain.entities.BankAccount;
import io.github.springmediator.mediator.bus.MediatorBus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/")
public class AccountController {

    private final MediatorBus mediatorBus;

    public AccountController(MediatorBus mediatorBus) {
        this.mediatorBus = mediatorBus;
    }

    @GetMapping
    public Map<String, String> index() {
        return Map.of(
                "mode", "event-sourcing",
                "description", "Spring Mediator Example — Event Sourcing Mode"
        );
    }

    @PostMapping("/accounts")
    public ResponseEntity<Map<String, String>> openAccount(@RequestBody OpenAccountRequest request) {
        mediatorBus.send(new OpenAccountCommand(request.ownerName(), request.initialDeposit()));
        return ResponseEntity.ok(Map.of("status", "account_opened"));
    }

    @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<Map<String, String>> deposit(
            @PathVariable String id, @RequestBody AmountRequest request) {
        mediatorBus.send(new DepositMoneyCommand(id, request.amount()));
        return ResponseEntity.ok(Map.of("status", "deposited"));
    }

    @PostMapping("/accounts/{id}/withdraw")
    public ResponseEntity<Map<String, String>> withdraw(
            @PathVariable String id, @RequestBody AmountRequest request) {
        mediatorBus.send(new WithdrawMoneyCommand(id, request.amount()));
        return ResponseEntity.ok(Map.of("status", "withdrawn"));
    }

    @PostMapping("/accounts/{id}/close")
    public ResponseEntity<Map<String, String>> closeAccount(
            @PathVariable String id, @RequestBody(required = false) CloseRequest request) {
        String reason = request != null ? request.reason() : "No reason provided";
        mediatorBus.send(new CloseAccountCommand(id, reason));
        return ResponseEntity.ok(Map.of("status", "account_closed"));
    }

    @GetMapping("/accounts/{id}")
    public Map<String, Object> getAccount(@PathVariable String id) {
        BankAccount account = mediatorBus.query(new GetAccountQuery(id));
        return Map.of(
                "accountId", account.getId(),
                "ownerName", account.getOwnerName(),
                "balance", account.getBalance(),
                "closed", account.isClosed(),
                "version", account.getVersion()
        );
    }

    // --- Request DTOs ---

    public record OpenAccountRequest(String ownerName, BigDecimal initialDeposit) {}
    public record AmountRequest(BigDecimal amount) {}
    public record CloseRequest(String reason) {}
}
