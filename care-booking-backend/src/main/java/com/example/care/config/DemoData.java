package com.example.care.config;

import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import com.example.care.entity.*;
import com.example.care.entity.Booking.Status;
import com.example.care.entity.UserAccount.Role;
import com.example.care.repository.*;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition
    .ConditionalOnProperty;
import org.springframework.security.crypto.password
    .PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "app.seed-demo",
    havingValue = "true"
)
public class DemoData implements CommandLineRunner {

    private final UserRepository users;
    private final CareServiceRepository services;
    private final CareSlotRepository slots;
    private final BookingRepository bookings;
    private final PasswordEncoder encoder;

    public DemoData(
            UserRepository users,
            CareServiceRepository services,
            CareSlotRepository slots,
            BookingRepository bookings,
            PasswordEncoder encoder
    ) {
        this.users = users;
        this.services = services;
        this.slots = slots;
        this.bookings = bookings;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {

        // 只在完全沒有帳號時建立，重新啟動不重複新增
        if (users.count() > 0) {
            return;
        }

        String password = encoder.encode("Demo12345!");

        users.save(new UserAccount(
            "admin@care.test",
            password,
            "系統管理者",
            Role.ADMIN
        ));

        String[] customerNames = {
            "王小明", "陳美玲", "林志宏", "張雅婷", "李文德"
        };

        List<UserAccount> customers = new ArrayList<>();

        for (int i = 0; i < customerNames.length; i++) {
            customers.add(users.save(
                new UserAccount(
                    "customer" + (i + 1) + "@care.test",
                    password,
                    customerNames[i],
                    Role.CUSTOMER
                )
            ));
        }

        String[] caregiverNames = {
            "黃照服", "吳照服", "蔡照服", "許照服"
        };

        List<UserAccount> caregivers = new ArrayList<>();

        for (int i = 0; i < caregiverNames.length; i++) {
            caregivers.add(users.save(
                new UserAccount(
                    "caregiver" + (i + 1) + "@care.test",
                    password,
                    caregiverNames[i],
                    Role.CAREGIVER
                )
            ));
        }

        String[] serviceNames = {
            "居家陪伴",
            "備餐協助",
            "居家環境整理",
            "陪同散步",
            "陪同就醫",
            "代購生活用品",
            "日常生活協助",
            "安全看視",
            "社區活動陪同",
            "家屬喘息陪伴"
        };

        List<CareService> careServices = new ArrayList<>();

        for (int i = 0; i < serviceNames.length; i++) {
            careServices.add(services.save(
                new CareService(
                    serviceNames[i],
                    "兩小時示範服務，實際內容須另行確認。",
                    BigDecimal.valueOf(800 + i * 100L)
                )
            ));
        }

        LocalDate today = LocalDate.now(
            ZoneId.of("Asia/Taipei")
        );

        for (int day = 1; day <= 10; day++) {

            LocalDate date = today.plusDays(day);
            CareSlot bookedSlot = null;

            for (int c = 0; c < caregivers.size(); c++) {

                UserAccount caregiver = caregivers.get(c);

                CareSlot morning = slots.save(
                    new CareSlot(
                        caregiver,
                        date.atTime(9, 0),
                        date.atTime(11, 0)
                    )
                );

                slots.save(new CareSlot(
                    caregiver,
                    date.atTime(13, 0),
                    date.atTime(15, 0)
                ));

                if (c == (day - 1) % caregivers.size()) {
                    bookedSlot = morning;
                }
            }

            Booking booking = new Booking();

            booking.setCustomer(
                customers.get((day - 1) % customers.size())
            );

            CareService service = careServices.get(day - 1);

            booking.setCareService(service);
            booking.setSlot(bookedSlot);
            booking.setPrice(service.getPrice());

            booking.setStatus(
                day % 2 == 0
                    ? Status.CONFIRMED
                    : Status.PENDING
            );

            booking.setAddress(
                "示範市安心路 " + day + " 號"
            );

            booking.setNote("示範資料，非真實個案");

            booking.setCreatedAt(
                LocalDateTime.now(
                    ZoneId.of("Asia/Taipei")
                )
            );

            bookings.save(booking);
        }
    }
}