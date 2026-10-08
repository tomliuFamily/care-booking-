package com.example.care.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.care.dto.Dtos.BookingView;

import net.sf.jasperreports.engine.JRException;

@Service
public class PdfService {

    private final BookingService bookings;

    public PdfService(BookingService bookings) {
        this.bookings = bookings;
    }

    public byte[] bookingPdf(
            Long userId,
            Long bookingId
    ) throws JRException {

        // 保留既有權限檢查：
        // 必須先確認這個使用者有權查看這筆預約。
        BookingView booking =
            bookings.detail(userId, bookingId);

        Map<String, Object> parameters = new HashMap<>();

        parameters.put(
            "BOOKING_ID",
            PdfReportSupport.text(booking.id)
        );

        parameters.put(
            "CUSTOMER",
            PdfReportSupport.text(booking.customerName)
        );

        parameters.put(
            "CAREGIVER",
            PdfReportSupport.text(booking.caregiverName)
        );

        parameters.put(
            "SERVICE",
            PdfReportSupport.text(booking.serviceName)
        );

        parameters.put(
            "START_AT",
            PdfReportSupport.dateTime(booking.startAt)
        );

        parameters.put(
            "END_AT",
            PdfReportSupport.dateTime(booking.endAt)
        );

        parameters.put(
            "STATUS",
            statusName(booking.status)
        );

        parameters.put(
            "PRICE",
            PdfReportSupport.money(booking.price)
        );

        parameters.put(
            "ADDRESS",
            PdfReportSupport.text(booking.address)
        );

        parameters.put(
            "NOTE",
            PdfReportSupport.text(booking.note)
        );

        return PdfReportSupport.render(
            "reports/booking-confirmation.jrxml",
            parameters
        );
    }

    private String statusName(String status) {

        if (status == null) {
            return "未設定";
        }

        return switch (status) {
            case "PENDING" -> "待確認";
            case "CONFIRMED" -> "已確認";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }
}