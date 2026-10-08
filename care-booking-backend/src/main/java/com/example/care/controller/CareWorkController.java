package com.example.care.controller;

import java.util.List;
import java.util.Map;

import com.example.care.dto.Dtos;
import com.example.care.dto.WorkDtos;
import com.example.care.service.CareWorkService;
import com.example.care.service.SettlementPdfService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.JRException;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work")
@RequiredArgsConstructor
public class CareWorkController {

    private final CareWorkService service;
    private final SettlementPdfService pdf;

    @GetMapping("/customers")
    public List<Dtos.UserView> customers(
            @AuthenticationPrincipal Long userId
    ) {
        return service.customers(userId);
    }

    @GetMapping("/cases")
    public List<WorkDtos.CaseView> cases(
            @AuthenticationPrincipal Long userId
    ) {
        return service.cases(userId);
    }

    @PostMapping("/cases")
    public ResponseEntity<Map<String, Long>> createCase(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkDtos.CaseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "id",
                service.saveCase(userId, null, request)
            ));
    }

    @PatchMapping("/cases/{id}")
    public Map<String, Long> updateCase(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody WorkDtos.CaseRequest request
    ) {
        return Map.of(
            "id",
            service.saveCase(userId, id, request)
        );
    }

    @PatchMapping("/cases/{id}/policy")
    public Map<String, String> policy(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody WorkDtos.PolicyRequest request
    ) {
        service.policy(userId, id, request);
        return ok();
    }

    @GetMapping("/flows")
    public List<WorkDtos.WorkView> flows(
            @AuthenticationPrincipal Long userId,
            @RequestParam String month
    ) {
        return service.list(userId, month);
    }

    @PostMapping("/bookings")
    public ResponseEntity<Map<String, Long>> createBooking(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WorkDtos.NewBookingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "id",
                service.createBooking(userId, request)
            ));
    }

    @PostMapping("/bookings/{id}/link")
    public Map<String, String> link(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody WorkDtos.LinkRequest request
    ) {
        service.link(userId, id, request);
        return ok();
    }

    @PostMapping("/bookings/{id}/record")
    public Map<String, String> record(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody WorkDtos.RecordRequest request
    ) {
        service.record(userId, id, request);
        return ok();
    }

    @PostMapping("/bookings/{id}/submit")
    public Map<String, String> submit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        service.submit(userId, id);
        return ok();
    }

    @PostMapping("/bookings/{id}/review")
    public Map<String, String> review(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody WorkDtos.ReviewRequest request
    ) {
        service.review(userId, id, request);
        return ok();
    }

    @PostMapping("/bookings/{id}/finance/{action}")
    public Map<String, String> finance(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable String action,
            @Valid @RequestBody WorkDtos.TextRequest request
    ) {
        service.financeAction(userId, id, action, request.text());
        return ok();
    }

    @GetMapping("/bookings/{id}/history")
    public List<WorkDtos.AuditView> history(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return service.history(userId, id);
    }

    @GetMapping(
        value = "/bookings/{id}/settlement.pdf",
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> settlement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) throws JRException {
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"settlement-" + id + ".pdf\""
            )
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf.generate(userId, id));
    }

    private Map<String, String> ok() {
        return Map.of("message", "操作完成");
    }
}