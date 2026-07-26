package com.c2guard.service;

import com.c2guard.domain.FacilityRecord;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 시설(사업장) 보유 화학물질 조회.
 * ulsan_sample_company_chemical_db.csv (화학물질종합정보시스템 크롤링 결과)를 로딩한다.
 * <p>
 * 현재 상태: 염산·황산·수산화나트륨 3종만 샘플로 크롤링됨 (전체 크롤링 미완료).
 * 전체 크롤링 완료되면 이 CSV만 교체하면 됨 (코드 변경 불필요).
 */
@Service
public class FacilityService {

    private static final String DATA_PATH = "data/ulsan_sample_company_chemical_db.csv";

    private final List<FacilityRecord> records = new ArrayList<>();

    @PostConstruct
    public void load() {
        try (InputStream is = skipBom(new ClassPathResource(DATA_PATH).getInputStream());
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                records.add(new FacilityRecord(
                        Integer.parseInt(record.get("reportYear").trim()),
                        record.get("bplcId"),
                        record.get("bplcNm"),
                        record.get("locplcAdres"),
                        record.get("induty"),
                        record.get("matterNm"),
                        record.get("casNo"),
                        record.get("ctprvnCdRmate"),
                        record.get("signguCdRmate")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("시설 보유물질 CSV 로딩 실패: " + DATA_PATH, e);
        }
    }

    /**
     * 시설명으로 보유 화학물질 목록을 조회한다. (정확히 일치하는 사업장명 기준)
     */
    public List<FacilityRecord> findByFacilityName(String facilityName) {
        return records.stream()
                .filter(r -> r.getFacilityName().equals(facilityName))
                .toList();
    }

    /**
     * 시설명 일부만 알아도 찾을 수 있도록 부분 일치 검색도 제공.
     * (구어체 입력이 "OO화학" 처럼 짧게 들어올 수 있어서 필요)
     */
    public List<FacilityRecord> searchByFacilityNameContains(String keyword) {
        String lower = keyword.toLowerCase(Locale.KOREA);
        return records.stream()
                .filter(r -> r.getFacilityName().toLowerCase(Locale.KOREA).contains(lower))
                .toList();
    }

    public int size() {
        return records.size();
    }

    private InputStream skipBom(InputStream is) throws java.io.IOException {
        PushbackInputStream pb = new PushbackInputStream(is, 3);
        byte[] bom = new byte[3];
        int read = pb.read(bom, 0, 3);
        if (read != 3 || bom[0] != (byte) 0xEF || bom[1] != (byte) 0xBB || bom[2] != (byte) 0xBF) {
            pb.unread(bom, 0, Math.max(read, 0));
        }
        return pb;
    }
}
