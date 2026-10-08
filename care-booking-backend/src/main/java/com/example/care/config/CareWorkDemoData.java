package com.example.care.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import com.example.care.entity.*;
import com.example.care.entity.Booking.Status;
import com.example.care.entity.CareWorkflow.ClaimStatus;
import com.example.care.entity.CareWorkflow.PaymentStatus;
import com.example.care.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "app.seed-demo",
    havingValue = "true"
)
public class CareWorkDemoData {

    private final UserRepository users;
    private final CareServiceRepository services;
    private final CareSlotRepository slots;
    private final BookingRepository bookings;
    private final CareCaseRepository cases;
    private final CareWorkflowRepository workflows;
    private final CareAuditRepository audits;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        // 已有個案時，不重複新增。
        if (cases.count() > 0) {
            return;
        }

        UserAccount admin = users.findByEmail("admin@care.test")
            .orElse(null);

        if (admin == null) {
            return;
        }

        List<UserAccount> customers = new ArrayList<>();
        List<UserAccount> caregivers = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            UserAccount user = users.findByEmail(
                "customer" + i + "@care.test"
            ).orElse(null);

            if (user == null) {
                return;
            }

            customers.add(user);
        }

        for (int i = 1; i <= 4; i++) {
            UserAccount user = users.findByEmail(
                "caregiver" + i + "@care.test"
            ).orElse(null);

            if (user == null) {
                return;
            }

            caregivers.add(user);
        }

        List<CareService> serviceList =
            services.findByActiveTrueOrderById();

        if (serviceList.isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(
            ZoneId.of("Asia/Taipei")
        );

        String[] names = {
            "示範個案一", "示範個案二",
            "示範個案三", "示範個案四",
            "示範個案五", "示範個案六",
            "示範個案七", "示範個案八",
            "示範個案九", "示範個案十"
        };

        String[] needs = {
            "生活陪伴與備餐協助",
            "陪同散步與安全看視",
            "居家環境整理",
            "陪同就醫與交通協助",
            "日常活動與家屬喘息服務",
            "備餐與生活用品代購",
            "社區活動陪同",
            "生活起居協助",
            "居家陪伴與安全提醒",
            "家屬喘息與生活陪伴"
        };

        for (int i = 0; i < 10; i++) {
            UserAccount customer = customers.get(i % 5);
            UserAccount caregiver = caregivers.get(i % 4);

            CareCase careCase = new CareCase();

            careCase.setCustomer(customer);
            careCase.setName(names[i]);
            careCase.setBirthDate(LocalDate.of(1945 + i, 1, 15));
            careCase.setContactName(customer.getName());
            careCase.setContactPhone("0900000000");
            careCase.setAddress(
                "示範市安心路 " + (i + 1) + " 號"
            );
            careCase.setCareNeeds(
                needs[i] + "。此為虛構示範資料。"
            );
            careCase.setActive(true);
            careCase.setSubsidyRate(new BigDecimal("60.00"));
            careCase.setSubsidyLimit(new BigDecimal("1000.00"));

            cases.saveAndFlush(careCase);

            LocalDate date = today.minusDays(i + 1L);
            LocalDateTime start = date.atTime(9, 0);
            LocalDateTime end = date.atTime(11, 0);

            // 若已有重疊時段，保留既有資料，不建立這筆示範預約。
            if (slots.countOverlapping(
                    caregiver.getId(), start, end) > 0) {
                continue;
            }

            CareSlot slot = slots.saveAndFlush(
                new CareSlot(caregiver, start, end)
            );

            CareService service =
                serviceList.get(i % serviceList.size());

            Booking booking = new Booking();

            booking.setCustomer(customer);
            booking.setCareService(service);
            booking.setSlot(slot);
            booking.setStatus(Status.COMPLETED);
            booking.setPrice(service.getPrice());
            booking.setAddress(careCase.getAddress());
            booking.setNote("新增功能示範資料，非真實交易");
            booking.setCreatedAt(start.minusDays(1));

            bookings.saveAndFlush(booking);

            CareWorkflow workflow = new CareWorkflow();

            workflow.setBooking(booking);
            workflow.setCareCase(careCase);
            workflow.setSubsidyRate(careCase.getSubsidyRate());
            workflow.setSubsidyLimit(careCase.getSubsidyLimit());
            workflow.setActualStart(start);
            workflow.setActualEnd(end);
            workflow.setServiceNote(
                "示範紀錄：已完成" + needs[i] + "。"
            );

            BigDecimal amount = service.getPrice()
                .multiply(new BigDecimal("0.60"))
                .setScale(2, RoundingMode.HALF_UP)
                .min(careCase.getSubsidyLimit());

            int stage = i % 5;

            if (stage >= 1) {
                workflow.setRequestedAmount(amount);
                workflow.setClaimStatus(ClaimStatus.SUBMITTED);
            }

            if (stage >= 2) {
                workflow.setClaimStatus(ClaimStatus.APPROVED);
                workflow.setApprovedAmount(amount);
                workflow.setCustomerAmount(
                    service.getPrice().subtract(amount)
                );
                workflow.setReviewNote("示範審核通過");
            }

            if (stage == 3) {
                workflow.setPaymentStatus(PaymentStatus.REPORTED);
                workflow.setPaymentReference(
                    "DEMO-TRANSFER-" + (i + 1)
                );
            }

            if (stage == 4) {
                workflow.setPaymentStatus(PaymentStatus.PAID);
                workflow.setPaymentReference(
                    "DEMO-PAID-" + (i + 1)
                );
                workflow.setPaidAt(end.plusHours(1));
            }

            workflows.saveAndFlush(workflow);

            CareAudit audit = new CareAudit();

            audit.setActor(admin);
            audit.setBooking(booking);
            audit.setAction("DEMO_CREATED");
            audit.setDetail(
                "系統建立示範資料；狀態："
                    + workflow.getClaimStatus()
                    + " / " + workflow.getPaymentStatus()
                    + "，不代表真實收款。"
            );
            audit.setCreatedAt(
                LocalDateTime.now(ZoneId.of("Asia/Taipei"))
            );

            audits.save(audit);
        }
    }
}