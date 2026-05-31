package com.ledger.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.gateway.domain.EventRecord;
import com.ledger.gateway.domain.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    public EventController(EventService eventService, ObjectMapper objectMapper) {
        this.eventService = eventService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EventDtos.EventResponse> submit(
            @Valid @RequestBody EventDtos.SubmitEventRequest request) {

        String type = request.type().toUpperCase();
        if (!type.equals("CREDIT") && !type.equals("DEBIT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "type must be CREDIT or DEBIT, was: " + request.type());
        }

        String metadataJson = writeMetadata(request.metadata());

        EventService.SubmitResult result = eventService.submit(
                request.eventId(), request.accountId(), type, request.amount(),
                request.currency(), request.eventTimestamp(), metadataJson);

        // 200 on duplicate (return original), 201 on newly created.
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(toResponse(result.event(), result.duplicate()));
    }

    @GetMapping("/{id}")
    public EventDtos.EventResponse getById(@PathVariable String id) {
        EventRecord record = eventService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + id));
        return toResponse(record, false);
    }

    /**
     * List events for an account, chronologically by event timestamp.
     * Depends only on local Gateway data, so it works even when the Account
     * Service is down.
     */
    @GetMapping(params = "account")
    public List<EventDtos.EventResponse> listByAccount(@RequestParam("account") String accountId) {
        return eventService.findByAccount(accountId).stream()
                .map(r -> toResponse(r, false))
                .toList();
    }

    @GetMapping("/balance")
    public EventDtos.BalanceResponse balance(@RequestParam("account") String accountId) {
        BigDecimal balance = eventService.balanceOf(accountId);
        return new EventDtos.BalanceResponse(accountId, balance);
    }

    // --- helpers -----------------------------------------------------------

    private String writeMetadata(com.fasterxml.jackson.databind.JsonNode metadata) {
        if (metadata == null || metadata.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid metadata");
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private EventDtos.EventResponse toResponse(EventRecord r, boolean duplicate) {
        return new EventDtos.EventResponse(
                r.getEventId(), r.getAccountId(), r.getType(), r.getAmount(), r.getCurrency(),
                r.getEventTimestamp(), readMetadata(r.getMetadataJson()),
                r.getStatus().name(), r.getReceivedAt(), duplicate);
    }
}
