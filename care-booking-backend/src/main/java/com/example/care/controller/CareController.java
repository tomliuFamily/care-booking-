package com.example.care.controller;

import java.util.List;
import java.util.Map;

import com.example.care.dto.Dtos;
import com.example.care.service.BookingService;
import com.example.care.service.PdfService;

import jakarta.validation.Valid;

import net.sf.jasperreports.engine.JRException;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation
    .AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CareController {

    private final BookingService bookings;
    private final PdfService pdf;

    public CareController(
            BookingService bookings,
            PdfService pdf
    ) {
        this.bookings = bookings;
        this.pdf = pdf;
    }

    @GetMapping("/services")
    public List<Dtos.ServiceView> services() {
        return bookings.services();
    }

    @GetMapping("/caregivers")
    public List<Dtos.CaregiverView> caregivers() {
        return bookings.caregivers();
    }

    @GetMapping("/slots")
    public List<Dtos.SlotView> slots(
            @RequestParam String month
    ) {
        return bookings.availableSlots(month);
    }

    @PostMapping("/slots")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAREGIVER')")
    public ResponseEntity<Map<String, Long>> createSlot(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody
            Dtos.CreateSlotRequest request
    ) {
        Long id = bookings.createSlot(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("id", id));
    }

    @GetMapping("/bookings")
    public List<Dtos.BookingView> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam String month
    ) {
        return bookings.list(userId, month);
    }

    @GetMapping("/bookings/{id}")
    public Dtos.BookingView detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return bookings.detail(userId, id);
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, Long>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody
            Dtos.CreateBookingRequest request
    ) {
        Long id = bookings.create(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("id", id));
    }

    @PatchMapping("/bookings/{id}/status")
    public Map<String, String> changeStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody
            Dtos.ChangeStatusRequest request
    ) {
        bookings.changeStatus(
            userId, id, request.status()
        );

        return Map.of("message", "預約狀態已更新");
    }

    @GetMapping(
        value = "/bookings/{id}/pdf",
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> pdf(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) throws JRException {

        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"booking-" + id + ".pdf\""
            )
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf.bookingPdf(userId, id));
    }
}