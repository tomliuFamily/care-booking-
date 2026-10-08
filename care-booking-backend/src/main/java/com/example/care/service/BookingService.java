package com.example.care.service;

import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.care.dao.BookingDao;
import com.example.care.dto.Dtos;
import com.example.care.entity.*;
import com.example.care.entity.Booking.Status;
import com.example.care.entity.UserAccount.Role;
import com.example.care.repository.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
    .Isolation;
import org.springframework.transaction.annotation
    .Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private static final ZoneId TAIPEI =
        ZoneId.of("Asia/Taipei");

    private final UserRepository users;
    private final CareServiceRepository services;
    private final CareSlotRepository slots;
    private final BookingRepository bookings;
    private final BookingDao dao;

    public BookingService(
            UserRepository users,
            CareServiceRepository services,
            CareSlotRepository slots,
            BookingRepository bookings,
            BookingDao dao
    ) {
        this.users = users;
        this.services = services;
        this.slots = slots;
        this.bookings = bookings;
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public List<Dtos.ServiceView> services() {
        return services.findByActiveTrueOrderById()
            .stream()
            .map(s -> new Dtos.ServiceView(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getPrice()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.CaregiverView> caregivers() {
        return users.findByRoleOrderById(Role.CAREGIVER)
            .stream()
            .map(u -> new Dtos.CaregiverView(
                u.getId(), u.getName()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.SlotView> availableSlots(String month) {
        YearMonth ym = parseMonth(month);

        LocalDateTime from =
            ym.atDay(1).atStartOfDay();

        LocalDateTime to =
            ym.plusMonths(1).atDay(1).atStartOfDay();

        Set<Long> occupied = new HashSet<>(
            bookings.occupiedSlotIds(
                from, to, Status.CANCELLED
            )
        );

        LocalDateTime now = now();

        return slots.findInMonth(from, to)
            .stream()
            .filter(s -> s.getStartAt().isAfter(now))
            .filter(s -> !occupied.contains(s.getId()))
            .map(s -> new Dtos.SlotView(
                s.getId(),
                s.getCaregiver().getId(),
                s.getCaregiver().getName(),
                s.getStartAt(),
                s.getEndAt()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Dtos.BookingView> list(
            Long userId,
            String month
    ) {
        UserAccount user = user(userId);
        YearMonth ym = parseMonth(month);

        return dao.findVisible(
            userId,
            user.getRole().name(),
            ym.atDay(1).atStartOfDay(),
            ym.plusMonths(1).atDay(1).atStartOfDay(),
            null
        );
    }

    @Transactional(readOnly = true)
    public Dtos.BookingView detail(
            Long userId,
            Long bookingId
    ) {
        UserAccount user = user(userId);

        List<Dtos.BookingView> result = dao.findVisible(
            userId,
            user.getRole().name(),
            null,
            null,
            bookingId
        );

        if (result.isEmpty()) {
            throw error(
                HttpStatus.NOT_FOUND,
                "找不到預約，或你沒有查看權限"
            );
        }

        return result.get(0);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long create(
            Long customerId,
            Dtos.CreateBookingRequest request
    ) {
        // 先鎖住顧客，避免同一顧客同時送出重疊預約
        UserAccount customer = users.lockById(customerId)
            .orElseThrow(() -> error(
                HttpStatus.NOT_FOUND, "找不到顧客"
            ));

        if (customer.getRole() != Role.CUSTOMER) {
            throw error(
                HttpStatus.FORBIDDEN,
                "只有顧客可以建立預約"
            );
        }

        // 鎖住時段，讓同時搶同一時段的請求依序處理
        CareSlot slot = slots.lockById(request.slotId())
            .orElseThrow(() -> error(
                HttpStatus.NOT_FOUND, "找不到時段"
            ));

        if (!slot.getStartAt().isAfter(now())) {
            throw error(
                HttpStatus.BAD_REQUEST,
                "不能預約已開始或過去的時段"
            );
        }

        if (bookings.existsBySlotIdAndStatusNot(
                slot.getId(), Status.CANCELLED)) {
            throw error(
                HttpStatus.CONFLICT,
                "這個時段已被預約，請重新選擇"
            );
        }

        long overlap = bookings.countCustomerOverlap(
            customerId,
            slot.getStartAt(),
            slot.getEndAt(),
            Status.CANCELLED
        );

        if (overlap > 0) {
            throw error(
                HttpStatus.CONFLICT,
                "你在這個時間已有其他預約"
            );
        }

        CareService service = services
            .findById(request.serviceId())
            .filter(CareService::isActive)
            .orElseThrow(() -> error(
                HttpStatus.NOT_FOUND,
                "服務不存在或已停用"
            ));

        Booking booking = new Booking();

        booking.setCustomer(customer);
        booking.setCareService(service);
        booking.setSlot(slot);
        booking.setStatus(Status.PENDING);
        booking.setPrice(service.getPrice());
        booking.setAddress(request.address().trim());

        booking.setNote(
            request.note() == null
                ? ""
                : request.note().trim()
        );

        booking.setCreatedAt(now());

        return bookings.saveAndFlush(booking).getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long createSlot(
            Long userId,
            Dtos.CreateSlotRequest request
    ) {
        UserAccount actor = user(userId);

        if (actor.getRole() == Role.CUSTOMER) {
            throw error(
                HttpStatus.FORBIDDEN,
                "顧客不能開放照服時段"
            );
        }

        if (actor.getRole() == Role.CAREGIVER
                && !actor.getId().equals(
                    request.caregiverId())) {
            throw error(
                HttpStatus.FORBIDDEN,
                "只能開放自己的時段"
            );
        }

        // 同一照服員開放時段時，先鎖住照服員資料列
        UserAccount caregiver = users.lockById(
            request.caregiverId()
        ).orElseThrow(() -> error(
            HttpStatus.NOT_FOUND, "照服員不存在"
        ));

        if (caregiver.getRole() != Role.CAREGIVER) {
            throw error(
                HttpStatus.BAD_REQUEST,
                "所選帳號不是照服員"
            );
        }

        LocalDateTime start = request.startAt();
        LocalDateTime end = start.plusHours(2);

        if (!start.isAfter(now())) {
            throw error(
                HttpStatus.BAD_REQUEST,
                "只能開放未來的時段"
            );
        }

        if (start.getMinute() != 0
                || start.getSecond() != 0
                || start.getNano() != 0
                || start.getHour() < 8
                || start.getHour() > 18) {
            throw error(
                HttpStatus.BAD_REQUEST,
                "開始時間須為 08:00～18:00 的整點"
            );
        }

        if (slots.countOverlapping(
                caregiver.getId(), start, end) > 0) {
            throw error(
                HttpStatus.CONFLICT,
                "照服員已有重疊時段"
            );
        }

        CareSlot slot = slots.saveAndFlush(
            new CareSlot(caregiver, start, end)
        );

        return slot.getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeStatus(
            Long userId,
            Long bookingId,
            String statusText
    ) {
        UserAccount actor = user(userId);

        Booking booking = bookings.lockById(bookingId)
            .orElseThrow(() -> error(
                HttpStatus.NOT_FOUND, "找不到預約"
            ));

        Status target;

        try {
            target = Status.valueOf(statusText);
        } catch (IllegalArgumentException e) {
            throw error(
                HttpStatus.BAD_REQUEST,
                "不支援的預約狀態"
            );
        }

        boolean admin = actor.getRole() == Role.ADMIN;

        boolean owner =
            actor.getRole() == Role.CUSTOMER
            && booking.getCustomer().getId()
                .equals(userId);

        boolean assigned =
            actor.getRole() == Role.CAREGIVER
            && booking.getSlot().getCaregiver().getId()
                .equals(userId);

        if (!admin && !owner && !assigned) {
            throw error(
                HttpStatus.FORBIDDEN,
                "不能修改他人的預約"
            );
        }

        Status current = booking.getStatus();

        if (current == Status.CANCELLED
                || current == Status.COMPLETED) {
            throw error(
                HttpStatus.CONFLICT,
                "已取消或已完成的預約不能再修改"
            );
        }

        switch (target) {
            case CANCELLED -> {
                if (!admin && !owner) {
                    throw error(
                        HttpStatus.FORBIDDEN,
                        "只有管理者或預約顧客能取消"
                    );
                }

                if (!admin
                        && !booking.getSlot().getStartAt()
                            .isAfter(now())) {
                    throw error(
                        HttpStatus.CONFLICT,
                        "預約已開始，請聯絡管理者"
                    );
                }
            }

            case CONFIRMED -> {
                if (!admin && !assigned) {
                    throw error(
                        HttpStatus.FORBIDDEN,
                        "只有管理者或指定照服員能確認"
                    );
                }

                if (current != Status.PENDING) {
                    throw error(
                        HttpStatus.CONFLICT,
                        "只有待確認預約能進行確認"
                    );
                }
            }

            case COMPLETED -> {
                if (!admin && !assigned) {
                    throw error(
                        HttpStatus.FORBIDDEN,
                        "只有管理者或指定照服員能完成"
                    );
                }

                if (current != Status.CONFIRMED) {
                    throw error(
                        HttpStatus.CONFLICT,
                        "必須先確認預約"
                    );
                }

                if (booking.getSlot().getEndAt()
                        .isAfter(now())) {
                    throw error(
                        HttpStatus.CONFLICT,
                        "服務時段尚未結束"
                    );
                }
            }

            default -> throw error(
                HttpStatus.BAD_REQUEST,
                "不能將預約改回待確認"
            );
        }

        booking.setStatus(target);
    }

    private UserAccount user(Long id) {
        return users.findById(id)
            .orElseThrow(() -> error(
                HttpStatus.UNAUTHORIZED,
                "登入帳號不存在"
            ));
    }

    private YearMonth parseMonth(String month) {
        try {
            YearMonth result = YearMonth.parse(month);

            if (result.getYear() < 1900
                    || result.getYear() > 9998) {
                throw new IllegalArgumentException();
            }

            return result;
        } catch (RuntimeException e) {
            throw error(
                HttpStatus.BAD_REQUEST,
                "月份格式須為 yyyy-MM，年份限 1900～9998"
            );
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(TAIPEI);
    }

    private ResponseStatusException error(
            HttpStatus status,
            String message
    ) {
        return new ResponseStatusException(status, message);
    }
}