package com.example.care.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

import com.example.care.dto.Dtos;
import com.example.care.dto.WorkDtos;
import com.example.care.entity.*;
import com.example.care.entity.Booking.Status;
import com.example.care.entity.CareWorkflow.ClaimStatus;
import com.example.care.entity.CareWorkflow.PaymentStatus;
import com.example.care.entity.UserAccount.Role;
import com.example.care.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CareWorkService {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private final UserRepository users;
    private final BookingRepository bookings;
    private final CareCaseRepository cases;
    private final CareWorkflowRepository workflows;
    private final CareAuditRepository audits;
    private final BookingService bookingService;

    @Transactional(readOnly = true)
    public List<Dtos.UserView> customers(Long userId) {
        admin(actor(userId));

        return users.findByRoleOrderById(Role.CUSTOMER)
            .stream()
            .map(u -> new Dtos.UserView(
                u.getId(),
                u.getEmail(),
                u.getName(),
                u.getRole().name()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkDtos.CaseView> cases(Long userId) {
        UserAccount user = actor(userId);

        return cases.findVisible(
                userId,
                user.getRole().name(),
                Status.CANCELLED
            )
            .stream()
            .map(c -> caseView(c, user))
            .toList();
    }

    public Long saveCase(
            Long userId,
            Long caseId,
            WorkDtos.CaseRequest request
    ) {
        UserAccount user = actor(userId);

        if (user.getRole() == Role.CAREGIVER) {
            throw forbidden("照服員不能修改個案基本資料");
        }

        CareCase careCase;

        if (caseId == null) {
            careCase = new CareCase();

            Long customerId = user.getRole() == Role.CUSTOMER
                ? userId
                : request.customerId();

            if (customerId == null) {
                throw bad("請選擇顧客");
            }

            UserAccount customer = users.findById(customerId)
                .orElseThrow(() -> bad("顧客不存在"));

            if (customer.getRole() != Role.CUSTOMER) {
                throw bad("個案必須歸屬顧客帳號");
            }

            careCase.setCustomer(customer);
        } else {
            careCase = lockCase(caseId);
            ownerOrAdmin(user, careCase.getCustomer().getId());

            if (request.customerId() != null
                    && !request.customerId().equals(
                        careCase.getCustomer().getId())) {
                throw bad("此版本不提供轉移個案所屬顧客");
            }
        }

        careCase.setName(request.name().trim());
        careCase.setBirthDate(request.birthDate());
        careCase.setContactName(request.contactName().trim());
        careCase.setContactPhone(request.contactPhone().trim());
        careCase.setAddress(request.address().trim());
        careCase.setCareNeeds(request.careNeeds().trim());
        careCase.setActive(request.active());

        return cases.saveAndFlush(careCase).getId();
    }

    public void policy(
            Long userId,
            Long caseId,
            WorkDtos.PolicyRequest request
    ) {
        admin(actor(userId));

        CareCase careCase = lockCase(caseId);

        careCase.setSubsidyRate(request.subsidyRate().setScale(2));
        careCase.setSubsidyLimit(request.subsidyLimit().setScale(2));
    }

    public Long createBooking(
            Long userId,
            WorkDtos.NewBookingRequest request
    ) {
        UserAccount user = actor(userId);

        if (user.getRole() != Role.CUSTOMER) {
            throw forbidden("只有顧客可以建立預約");
        }

        CareCase careCase = lockCase(request.caseId());

        ownerOrAdmin(user, careCase.getCustomer().getId());
        check(careCase.isActive(), "個案已停用");

        String address = request.address() == null
                || request.address().isBlank()
            ? careCase.getAddress()
            : request.address().trim();

        // 沿用現有預約程式的時段鎖定與重複預約檢查。
        Long bookingId = bookingService.create(
            userId,
            new Dtos.CreateBookingRequest(
                request.serviceId(),
                request.slotId(),
                address,
                request.note()
            )
        );

        Booking booking = bookings.findById(bookingId)
            .orElseThrow(() -> missing("預約不存在"));

        attach(user, booking, careCase);

        return bookingId;
    }

    public void link(
            Long userId,
            Long bookingId,
            WorkDtos.LinkRequest request
    ) {
        UserAccount user = actor(userId);
        Booking booking = lockBooking(bookingId);

        ownerOrAdmin(user, booking.getCustomer().getId());

        check(
            booking.getStatus() != Status.CANCELLED,
            "已取消的預約不能綁定個案"
        );

        check(
            !workflows.existsByBookingId(bookingId),
            "這筆預約已綁定個案"
        );

        CareCase careCase = lockCase(request.caseId());

        check(careCase.isActive(), "個案已停用");

        if (!careCase.getCustomer().getId()
                .equals(booking.getCustomer().getId())) {
            throw bad("個案與預約必須屬於同一位顧客");
        }

        attach(user, booking, careCase);
    }

    @Transactional(readOnly = true)
    public List<WorkDtos.WorkView> list(
            Long userId,
            String month
    ) {
        UserAccount user = actor(userId);
        YearMonth ym = parseMonth(month);

        return workflows.findVisible(
                userId,
                user.getRole().name(),
                ym.atDay(1).atStartOfDay(),
                ym.plusMonths(1).atDay(1).atStartOfDay()
            )
            .stream()
            .map(w -> view(w, user))
            .toList();
    }

    public void record(
            Long userId,
            Long bookingId,
            WorkDtos.RecordRequest request
    ) {
        UserAccount user = actor(userId);
        Booking booking = lockBooking(bookingId);

        staff(user, booking);

        CareWorkflow workflow = workflow(bookingId);

        check(
            booking.getStatus() == Status.CONFIRMED
                || booking.getStatus() == Status.COMPLETED,
            "請先確認預約，取消的預約不能填寫紀錄"
        );

        check(
            workflow.getClaimStatus() == ClaimStatus.DRAFT
                || workflow.getClaimStatus() == ClaimStatus.REJECTED,
            "核銷送審後不能修改服務紀錄"
        );

        LocalDateTime start = request.actualStart();
        LocalDateTime end = request.actualEnd();

        check(end.isAfter(start), "結束时间必須晚於開始時間");

        check(
            !start.isBefore(booking.getSlot().getStartAt())
                && !end.isAfter(booking.getSlot().getEndAt()),
            "實際服務時間須落在預約時段內"
        );

        check(!end.isAfter(now()), "不能填寫尚未完成的服務");

        check(
            !booking.getSlot().getEndAt().isAfter(now()),
            "預約時段結束後才能完成服務"
        );

        workflow.setActualStart(start);
        workflow.setActualEnd(end);
        workflow.setServiceNote(request.serviceNote().trim());

        booking.setStatus(Status.COMPLETED);

        audit(
            user,
            booking,
            "SERVICE_RECORDED",
            "服務時間：" + start + " ～ " + end
                + "；紀錄：" + request.serviceNote().trim()
        );
    }

    public void submit(Long userId, Long bookingId) {
        UserAccount user = actor(userId);
        Booking booking = lockBooking(bookingId);

        ownerOrAdmin(user, booking.getCustomer().getId());

        CareWorkflow workflow = workflow(bookingId);

        check(
            booking.getStatus() == Status.COMPLETED,
            "服務尚未完成"
        );

        check(
            workflow.getActualStart() != null
                && workflow.getActualEnd() != null
                && !workflow.getServiceNote().isBlank(),
            "請先填寫服務紀錄"
        );

        check(
            workflow.getClaimStatus() == ClaimStatus.DRAFT
                || workflow.getClaimStatus() == ClaimStatus.REJECTED,
            "目前狀態不能送審"
        );

        BigDecimal amount = booking.getPrice()
            .multiply(workflow.getSubsidyRate())
            .divide(
                new BigDecimal("100"),
                2,
                RoundingMode.HALF_UP
            )
            .min(workflow.getSubsidyLimit())
            .min(booking.getPrice())
            .setScale(2);

        workflow.setRequestedAmount(amount);
        workflow.setClaimStatus(ClaimStatus.SUBMITTED);
        workflow.setReviewNote("");

        audit(
            user,
            booking,
            "CLAIM_SUBMITTED",
            "申請補助：" + amount
        );
    }

    public void review(
            Long userId,
            Long bookingId,
            WorkDtos.ReviewRequest request
    ) {
        UserAccount user = actor(userId);
        admin(user);

        Booking booking = lockBooking(bookingId);
        CareWorkflow workflow = workflow(bookingId);

        check(
            booking.getStatus() == Status.COMPLETED,
            "服務尚未完成"
        );

        check(
            workflow.getClaimStatus() == ClaimStatus.SUBMITTED,
            "只有待審核資料可以審核"
        );

        workflow.setReviewNote(request.reason().trim());

        if (!request.approve()) {
            workflow.setClaimStatus(ClaimStatus.REJECTED);

            audit(
                user,
                booking,
                "CLAIM_REJECTED",
                request.reason().trim()
            );
            return;
        }

        BigDecimal amount = request.amount();

        if (amount == null) {
            throw bad("核准時必須填寫補助金額");
        }

        check(
            amount.signum() >= 0
                && amount.compareTo(
                    workflow.getRequestedAmount()) <= 0
                && amount.compareTo(booking.getPrice()) <= 0,
            "核准補助不得小於零或超過申請金額及服務費用"
        );

        amount = amount.setScale(2);

        BigDecimal customerAmount = booking.getPrice()
            .subtract(amount)
            .setScale(2);

        workflow.setApprovedAmount(amount);
        workflow.setCustomerAmount(customerAmount);
        workflow.setClaimStatus(ClaimStatus.APPROVED);

        workflow.setPaymentStatus(
            customerAmount.signum() == 0
                ? PaymentStatus.WAIVED
                : PaymentStatus.UNPAID
        );

        audit(
            user,
            booking,
            "CLAIM_APPROVED",
            "核准補助：" + amount
                + "；顧客自付：" + customerAmount
                + "；原因：" + request.reason().trim()
        );
    }

    public void financeAction(
            Long userId,
            Long bookingId,
            String action,
            String text
    ) {
        UserAccount user = actor(userId);
        Booking booking = lockBooking(bookingId);
        CareWorkflow workflow = workflow(bookingId);

        ownerOrAdmin(user, booking.getCustomer().getId());

        check(
            booking.getStatus() == Status.COMPLETED
                && workflow.getClaimStatus() == ClaimStatus.APPROVED,
            "必須完成服務並核准核銷後才能操作"
        );

        String value = text == null ? "" : text.trim();

        if (value.isBlank() || value.length() > 200) {
            throw bad("請填寫 1～200 字的交易編號或說明");
        }

        PaymentStatus payment = workflow.getPaymentStatus();

        switch (action) {
            case "subsidy-received" -> {
                admin(user);

                check(
                    workflow.getApprovedAmount().signum() > 0,
                    "沒有應收補助款"
                );

                check(
                    workflow.getSubsidyReceivedAt() == null,
                    "補助已登錄入帳"
                );

                workflow.setSubsidyReceivedAt(now());
                workflow.setSubsidyReference(value);
            }

            case "payment-report" -> {
                check(
                    payment == PaymentStatus.UNPAID,
                    "目前不能登錄付款"
                );

                workflow.setPaymentReference(value);
                workflow.setPaymentStatus(PaymentStatus.REPORTED);
            }

            case "payment-confirm" -> {
                admin(user);

                check(
                    payment == PaymentStatus.REPORTED,
                    "請先登錄付款"
                );

                workflow.setPaymentReference(value);
                workflow.setPaymentStatus(PaymentStatus.PAID);
                workflow.setPaidAt(now());
            }

            case "payment-reject" -> {
                admin(user);

                check(
                    payment == PaymentStatus.REPORTED,
                    "只有待確認付款可以退回"
                );

                workflow.setPaymentStatus(PaymentStatus.UNPAID);
                workflow.setPaymentReference("");
            }

            case "refund-request" -> {
                check(
                    payment == PaymentStatus.PAID,
                    "只有已收款項可以申請退款"
                );

                workflow.setPaymentStatus(
                    PaymentStatus.REFUND_REQUESTED
                );
                workflow.setRefundReference(value);
            }

            case "refund-reject" -> {
                admin(user);

                check(
                    payment == PaymentStatus.REFUND_REQUESTED,
                    "目前沒有退款申請"
                );

                workflow.setPaymentStatus(PaymentStatus.PAID);
                workflow.setRefundReference(value);
            }

            case "refund-confirm" -> {
                admin(user);

                check(
                    payment == PaymentStatus.REFUND_REQUESTED,
                    "請先提出退款申請"
                );

                workflow.setPaymentStatus(PaymentStatus.REFUNDED);
                workflow.setRefundReference(value);
                workflow.setRefundedAt(now());
            }

            default -> throw bad("不支援的財務操作");
        }

        audit(user, booking, action, value);
    }

    @Transactional(readOnly = true)
    public List<WorkDtos.AuditView> history(
            Long userId,
            Long bookingId
    ) {
        UserAccount user = actor(userId);

        Booking booking = bookings.findById(bookingId)
            .orElseThrow(() -> missing("預約不存在"));

        ownerOrAdmin(user, booking.getCustomer().getId());

        return audits.findByBookingIdOrderByIdAsc(bookingId)
            .stream()
            .map(a -> new WorkDtos.AuditView(
                a.getId(),
                a.getActor().getName(),
                a.getAction(),
                a.getDetail(),
                a.getCreatedAt()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public WorkDtos.WorkView settlement(
            Long userId,
            Long bookingId
    ) {
        UserAccount user = actor(userId);
        CareWorkflow workflow = workflow(bookingId);

        ownerOrAdmin(
            user,
            workflow.getBooking().getCustomer().getId()
        );

        check(
            workflow.getClaimStatus() == ClaimStatus.APPROVED,
            "核銷核准後才能下載結算單"
        );

        return view(workflow, user);
    }

    private void attach(
            UserAccount user,
            Booking booking,
            CareCase careCase
    ) {
        CareWorkflow workflow = new CareWorkflow();

        workflow.setBooking(booking);
        workflow.setCareCase(careCase);
        workflow.setSubsidyRate(careCase.getSubsidyRate());
        workflow.setSubsidyLimit(careCase.getSubsidyLimit());

        workflows.saveAndFlush(workflow);

        audit(
            user,
            booking,
            "CASE_LINKED",
            "綁定個案 #" + careCase.getId()
                + "；補助比例：" + careCase.getSubsidyRate()
                + "%；每筆上限：" + careCase.getSubsidyLimit()
        );
    }

    private WorkDtos.CaseView caseView(
            CareCase c,
            UserAccount user
    ) {
        boolean financial = user.getRole() != Role.CAREGIVER;

        return new WorkDtos.CaseView(
            c.getId(),
            c.getCustomer().getId(),
            c.getCustomer().getName(),
            c.getName(),
            c.getBirthDate(),
            c.getContactName(),
            c.getContactPhone(),
            c.getAddress(),
            c.getCareNeeds(),
            c.isActive(),
            financial ? c.getSubsidyRate() : null,
            financial ? c.getSubsidyLimit() : null
        );
    }

    private WorkDtos.WorkView view(
            CareWorkflow w,
            UserAccount user
    ) {
        Booking b = w.getBooking();
        WorkDtos.FinanceView finance = null;

        if (user.getRole() != Role.CAREGIVER) {
            finance = new WorkDtos.FinanceView(
                w.getSubsidyRate(),
                w.getSubsidyLimit(),
                w.getClaimStatus().name(),
                w.getRequestedAmount(),
                w.getApprovedAmount(),
                w.getCustomerAmount(),
                w.getReviewNote(),
                w.getSubsidyReceivedAt(),
                w.getSubsidyReference(),
                w.getPaymentStatus().name(),
                w.getPaymentReference(),
                w.getPaidAt(),
                w.getRefundReference(),
                w.getRefundedAt()
            );
        }

        boolean staff = user.getRole() == Role.ADMIN
            || (
                user.getRole() == Role.CAREGIVER
                && b.getSlot().getCaregiver().getId()
                    .equals(user.getId())
            );

        boolean editable = staff
            && (
                b.getStatus() == Status.CONFIRMED
                || b.getStatus() == Status.COMPLETED
            )
            && (
                w.getClaimStatus() == ClaimStatus.DRAFT
                || w.getClaimStatus() == ClaimStatus.REJECTED
            );

        return new WorkDtos.WorkView(
            b.getId(),
            w.getCareCase().getId(),
            w.getCareCase().getName(),
            b.getCustomer().getName(),
            b.getSlot().getCaregiver().getName(),
            b.getCareService().getName(),
            b.getSlot().getStartAt(),
            b.getSlot().getEndAt(),
            b.getStatus().name(),
            b.getPrice(),
            w.getActualStart(),
            w.getActualEnd(),
            w.getServiceNote(),
            editable,
            finance
        );
    }

    private void audit(
            UserAccount user,
            Booking booking,
            String action,
            String detail
    ) {
        CareAudit audit = new CareAudit();

        audit.setActor(user);
        audit.setBooking(booking);
        audit.setAction(action);
        audit.setDetail(detail);
        audit.setCreatedAt(now());

        audits.save(audit);
    }

    private UserAccount actor(Long userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "登入帳號不存在"
            ));
    }

    private CareCase lockCase(Long id) {
        return cases.lockById(id)
            .orElseThrow(() -> missing("個案不存在"));
    }

    private Booking lockBooking(Long id) {
        return bookings.lockById(id)
            .orElseThrow(() -> missing("預約不存在"));
    }

    private CareWorkflow workflow(Long bookingId) {
        return workflows.findByBookingId(bookingId)
            .orElseThrow(() -> missing("請先將預約綁定個案"));
    }

    private void admin(UserAccount user) {
        if (user.getRole() != Role.ADMIN) {
            throw forbidden("只有管理者可以操作");
        }
    }

    private void ownerOrAdmin(
            UserAccount user,
            Long customerId
    ) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        if (user.getRole() != Role.CUSTOMER
                || !user.getId().equals(customerId)) {
            throw forbidden("沒有操作這筆資料的權限");
        }
    }

    private void staff(UserAccount user, Booking booking) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }

        if (user.getRole() != Role.CAREGIVER
                || !booking.getSlot().getCaregiver().getId()
                    .equals(user.getId())) {
            throw forbidden("只有管理者或指定照服員可以操作");
        }
    }

    private YearMonth parseMonth(String value) {
        try {
            YearMonth result = YearMonth.parse(value);

            if (result.getYear() < 1900
                    || result.getYear() > 9998) {
                throw new IllegalArgumentException();
            }

            return result;
        } catch (RuntimeException e) {
            throw bad("月份格式須為 yyyy-MM，年份限 1900～9998");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(TAIPEI);
    }

    private void check(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
            );
        }
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            message
        );
    }

    private ResponseStatusException missing(String message) {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            message
        );
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            message
        );
    }
}