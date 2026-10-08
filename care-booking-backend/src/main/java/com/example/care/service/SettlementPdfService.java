package com.example.care.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import net.sf.jasperreports.engine.JRException;

@Service
public class SettlementPdfService {

    private final CareWorkService workService;

    public SettlementPdfService(CareWorkService workService) {
        this.workService = workService;
    }

    public byte[] generate(
            Long userId,
            Long bookingId
    ) throws JRException {

        // 保留既有服務層的權限及結算資格檢查。
        var work = workService.settlement(userId, bookingId);
        var finance = work.finance();

        if (finance == null) {
            throw new JRException("這筆預約尚無結算資料。");
        }

        Map<String, Object> parameters = new HashMap<>();

        parameters.put(
            "BOOKING_ID",
            PdfReportSupport.text(work.bookingId())
        );

        parameters.put(
            "CASE_NAME",
            PdfReportSupport.text(work.caseName())
        );

        parameters.put(
            "CUSTOMER",
            PdfReportSupport.text(work.customerName())
        );

        parameters.put(
            "CAREGIVER",
            PdfReportSupport.text(work.caregiverName())
        );

        parameters.put(
            "SERVICE",
            PdfReportSupport.text(work.serviceName())
        );

        parameters.put(
            "ACTUAL_START",
            PdfReportSupport.dateTime(work.actualStart())
        );

        parameters.put(
            "ACTUAL_END",
            PdfReportSupport.dateTime(work.actualEnd())
        );

        parameters.put(
            "TOTAL",
            PdfReportSupport.money(work.price())
        );

        parameters.put(
            "SUBSIDY",
            PdfReportSupport.money(finance.approvedAmount())
        );

        parameters.put(
            "CUSTOMER_AMOUNT",
            PdfReportSupport.money(finance.customerAmount())
        );

        parameters.put(
            "PAYMENT_STATUS",
            paymentStatusName(finance.paymentStatus())
        );

        parameters.put(
            "PAYMENT_REFERENCE",
            PdfReportSupport.text(finance.paymentReference())
        );

        parameters.put(
            "PAID_AT",
            PdfReportSupport.dateTime(
                finance.paidAt(),
                "尚無確認收款紀錄"
            )
        );

        String subsidyReceivedText;

        if (finance.subsidyReceivedAt() != null) {
            subsidyReceivedText = PdfReportSupport.dateTime(
                finance.subsidyReceivedAt()
            );
        } else if (
            finance.approvedAmount() != null
            && finance.approvedAmount().signum() == 0
        ) {
            subsidyReceivedText = "無補助款需入帳";
        } else {
            subsidyReceivedText = "尚未登錄入帳";
        }

        parameters.put(
            "SUBSIDY_RECEIVED_AT",
            subsidyReceivedText
        );

        parameters.put(
            "SUBSIDY_REFERENCE",
            PdfReportSupport.text(finance.subsidyReference())
        );

        // 沿用目前流程：退款是全額退還顧客自付金額。
        // 退款申請中不算已退款，也不包含補助款。
        BigDecimal refundedAmount =
            "REFUNDED".equals(finance.paymentStatus())
                ? finance.customerAmount()
                : BigDecimal.ZERO;

        parameters.put(
            "REFUND_AMOUNT",
            PdfReportSupport.money(refundedAmount)
        );

        parameters.put(
            "REFUND_REFERENCE",
            PdfReportSupport.text(finance.refundReference())
        );

        parameters.put(
            "REFUNDED_AT",
            PdfReportSupport.dateTime(
                finance.refundedAt(),
                "尚未退款"
            )
        );

        return PdfReportSupport.render(
            "reports/settlement.jrxml",
            parameters
        );
    }

    private String paymentStatusName(String status) {

        if (status == null) {
            return "未設定";
        }

        return switch (status) {
            case "UNPAID" -> "未付款";
            case "REPORTED" -> "已登錄，待確認";
            case "PAID" -> "已確認收款";
            case "WAIVED" -> "免自付";
            case "REFUND_REQUESTED" -> "退款申請中";
            case "REFUNDED" -> "已全額退還顧客自付款";
            default -> status;
        };
    }
}