package com.c2guard.service;

import com.c2guard.domain.Substance;
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
import java.util.Map;

/**
 * substance_registry_draft.csv (화학물정보 x 재난별화학물질 조인 결과, 202종)를
 * 애플리케이션 시작 시 메모리에 올린다.
 * <p>
 * TODO: DB(RDS) 연동 후에는 이 클래스를 SubstanceRepository(JPA)로 교체.
 *       지금은 MVP 단계라 CSV 직접 로딩으로 대체한다.
 */
@Service
public class SubstanceRegistryService {

    private static final String REGISTRY_PATH = "data/substance_registry_draft.csv";

    private final Map<String, Substance> registry = new LinkedHashMap<>();

    @PostConstruct
    public void load() {
        try (InputStream is = skipBom(new ClassPathResource(REGISTRY_PATH).getInputStream());
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                String name = record.get("화학물질_한글명");
                if (name == null || name.isBlank()) {
                    continue;
                }
                Substance substance = new Substance(
                        name,
                        emptyToNull(record.get("CHEM_SBSTN_ENG_NM")),
                        emptyToNull(record.get("CAS_NO")),
                        emptyToNull(record.get("UN_MNG_NO")),
                        emptyToNull(record.get("RDTMP_STTS_NM")),
                        parseIntSafe(record.get("울산_출현건수")),
                        emptyToNull(record.get("유별_위험물분류"))
                );
                registry.put(name, substance);
            }
        } catch (Exception e) {
            throw new IllegalStateException("registry CSV 로딩 실패: " + REGISTRY_PATH, e);
        }
    }

    public Substance find(String koreanName) {
        return registry.get(koreanName);
    }

    public int size() {
        return registry.size();
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * pandas.to_csv(encoding='utf-8-sig')로 저장된 파일은 맨 앞에 UTF-8 BOM(EF BB BF)이 붙는다.
     * 그대로 읽으면 첫 헤더("화학물질_한글명")에 BOM이 섞여 컬럼 매칭이 깨지므로 건너뛴다.
     */
    private InputStream skipBom(InputStream is) throws java.io.IOException {
        java.io.PushbackInputStream pb = new java.io.PushbackInputStream(is, 3);
        byte[] bom = new byte[3];
        int read = pb.read(bom, 0, 3);
        if (read != 3 || bom[0] != (byte) 0xEF || bom[1] != (byte) 0xBB || bom[2] != (byte) 0xBF) {
            pb.unread(bom, 0, Math.max(read, 0));
        }
        return pb;
    }
}
