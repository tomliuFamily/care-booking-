package com.example.care.mybatis;

import java.time.LocalDateTime;
import java.util.List;

import com.example.care.dto.Dtos.BookingView;

import org.apache.ibatis.annotations.Param;

public interface BookingMapper {

    List<BookingView> findVisible(
        @Param("userId") Long userId,
        @Param("role") String role,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("id") Long id
    );
}