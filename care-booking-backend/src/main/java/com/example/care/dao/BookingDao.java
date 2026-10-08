package com.example.care.dao;

import java.time.LocalDateTime;
import java.util.List;

import com.example.care.dto.Dtos.BookingView;
import com.example.care.mybatis.BookingMapper;

import org.springframework.stereotype.Repository;

@Repository
public class BookingDao {

    private final BookingMapper mapper;

    public BookingDao(BookingMapper mapper) {
        this.mapper = mapper;
    }

    public List<BookingView> findVisible(
            Long userId,
            String role,
            LocalDateTime from,
            LocalDateTime to,
            Long id
    ) {
        return mapper.findVisible(
            userId, role, from, to, id
        );
    }
}