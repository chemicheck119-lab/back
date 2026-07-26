package com.c2guard.service;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 위험물안전관리법 시행령 [별표19] 유별 혼재기준표를 로딩한다.
 * 위험물_유별_혼재기준표.csv : 유별,성질,1류,2류,3류,4류,5류,6류
 */
@Service
public class HazardMatrixService {

    private static final String MATRIX_PATH = "data/위험물_유별_혼재기준표.csv";
    private static final List<String> CLASSES = List.of("1류", "2류", "3류", "4류", "5류", "6류");

    // classA -> classB -> "O" | "X" | "-"
    private final Map<String, Map<String, String>> matrix = new LinkedHashMap<>();

    @PostConstruct
    public void load() {
        try (InputStream is = new ClassPathResource(MATRIX_PATH).getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                String rowClass = record.get("유별");
                Map<String, String> row = new LinkedHashMap<>();
                for (String colClass : CLASSES) {
                    row.put(colClass, record.get(colClass));
                }
                matrix.put(rowClass, row);
            }
        } catch (Exception e) {
            throw new IllegalStateException("혼재기준표 CSV 로딩 실패: " + MATRIX_PATH, e);
        }
    }

    /**
     * @return "O"(혼재가능) / "X"(혼재불가) / "-"(동일유별, 별도확인필요) / null(유별값 자체가 이상함)
     */
    public String lookup(String classA, String classB) {
        Map<String, String> row = matrix.get(classA);
        return row == null ? null : row.get(classB);
    }
}
