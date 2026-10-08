package com.example.care.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

public final class PdfReportSupport {

    private static final DateTimeFormatter DATE_TIME =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private PdfReportSupport() {
    }

    public static byte[] render(
            String templatePath,
            Map<String, Object> parameters
    ) throws JRException {

        Map<String, Object> reportParameters =
            new HashMap<>(parameters);

        // 真正從網站下載時，不顯示 Studio 的示範資料提示。
        reportParameters.put("PREVIEW_NOTICE", "");

        ClassPathResource resource =
            new ClassPathResource(templatePath);

        // 使用 InputStream，打包成 Spring Boot JAR 後也能讀取。
        try (InputStream input = resource.getInputStream()) {

            JasperReport report =
                JasperCompileManager.compileReport(input);

            // 這兩份是單筆表單，資料全部透過 parameters 傳入。
            JasperPrint print =
                JasperFillManager.fillReport(
                    report,
                    reportParameters,
                    new JREmptyDataSource(1)
                );

            return JasperExportManager.exportReportToPdf(print);

        } catch (IOException exception) {
            throw new JRException(
                "無法讀取報表版型：" + templatePath,
                exception
            );
        }
    }

    public static String text(Object value) {

        if (value == null) {
            return "—";
        }

        String result = value.toString().strip();

        return result.isEmpty() ? "—" : result;
    }

    public static String dateTime(LocalDateTime value) {
        return dateTime(value, "未登錄");
    }

    public static String dateTime(
            LocalDateTime value,
            String emptyText
    ) {
        return value == null
            ? emptyText
            : value.format(DATE_TIME);
    }

    public static String money(BigDecimal value) {

        if (value == null) {
            return "—";
        }

        // NumberFormat 不是 thread-safe，因此每次呼叫建立一個。
        NumberFormat format =
            NumberFormat.getNumberInstance(Locale.TAIWAN);

        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setRoundingMode(RoundingMode.HALF_UP);

        return "NT$ " + format.format(value);
    }
}