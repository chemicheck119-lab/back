package com.c2guard.bff.demolog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DemoIncidentLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO synthetic_demo_incident_logs (
                demo_log_id, dataset_version, scenario_id, station_id,
                station_display_name, region, occurred_at, facility_name,
                facility_address, incident_substance_name, incident_substance_cas,
                conflict_substance_name, conflict_substance_cas, rule_id,
                rule_version, severity, risk_level, risk_level_ko, hazard_codes,
                gas_products, risk_summary, performed_action,
                brief_application_status, additional_factor,
                final_response_outcome, data_classification, operational_record,
                source_name, source_url, disclosure, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public DemoIncidentLogRepository(JdbcTemplate jdbcTemplate,
                                     TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public int seed(String datasetVersion, List<SyntheticDemoIncidentLog> records) {
        Integer inserted = transactionTemplate.execute(status -> {
            int existing = countForDataset(datasetVersion);
            if (existing == records.size()) return 0;
            if (existing != 0) {
                throw new IllegalStateException(
                        "합성 데모 로그 데이터셋이 일부만 적재되어 있습니다: " + existing);
            }
            jdbcTemplate.batchUpdate(INSERT_SQL, records, 500,
                    DemoIncidentLogRepository::bind);
            int stored = countForDataset(datasetVersion);
            if (stored != records.size()) {
                throw new IllegalStateException(
                        "합성 데모 로그 적재 건수가 일치하지 않습니다: " + stored);
            }
            return stored;
        });
        return inserted == null ? 0 : inserted;
    }

    public int countForDataset(String datasetVersion) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM synthetic_demo_incident_logs
                        WHERE dataset_version = ?
                        """, Integer.class, datasetVersion);
        return count == null ? 0 : count;
    }

    public int stationCount(String datasetVersion) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(DISTINCT station_id)
                        FROM synthetic_demo_incident_logs
                        WHERE dataset_version = ?
                        """, Integer.class, datasetVersion);
        return count == null ? 0 : count;
    }

    public int countByStation(String datasetVersion, String stationId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM synthetic_demo_incident_logs
                        WHERE dataset_version = ? AND station_id = ?
                        """, Integer.class, datasetVersion, stationId);
        return count == null ? 0 : count;
    }

    public List<SyntheticDemoIncidentLog> findByStation(
            String datasetVersion, String stationId, int offset, int limit) {
        return jdbcTemplate.query("""
                        SELECT * FROM synthetic_demo_incident_logs
                        WHERE dataset_version = ? AND station_id = ?
                        ORDER BY occurred_at DESC, demo_log_id
                        LIMIT ? OFFSET ?
                        """, (resultSet, rowNumber) -> new SyntheticDemoIncidentLog(
                        resultSet.getString("demo_log_id"),
                        resultSet.getString("dataset_version"),
                        resultSet.getString("scenario_id"),
                        resultSet.getString("station_id"),
                        resultSet.getString("station_display_name"),
                        resultSet.getString("region"),
                        resultSet.getObject("occurred_at", OffsetDateTime.class),
                        resultSet.getString("facility_name"),
                        resultSet.getString("facility_address"),
                        resultSet.getString("incident_substance_name"),
                        resultSet.getString("incident_substance_cas"),
                        resultSet.getString("conflict_substance_name"),
                        resultSet.getString("conflict_substance_cas"),
                        resultSet.getString("rule_id"),
                        resultSet.getString("rule_version"),
                        resultSet.getString("severity"),
                        resultSet.getString("risk_level"),
                        resultSet.getString("risk_level_ko"),
                        split(resultSet.getString("hazard_codes")),
                        split(resultSet.getString("gas_products")),
                        resultSet.getString("risk_summary"),
                        resultSet.getString("performed_action"),
                        resultSet.getString("brief_application_status"),
                        resultSet.getString("additional_factor"),
                        resultSet.getString("final_response_outcome"),
                        resultSet.getString("data_classification"),
                        resultSet.getBoolean("operational_record"),
                        resultSet.getString("source_name"),
                        resultSet.getString("source_url"),
                        resultSet.getString("disclosure"),
                        resultSet.getObject("created_at", OffsetDateTime.class)),
                datasetVersion, stationId, limit, offset);
    }

    public Map<String, RegionCoverage> coverageByRegion(String datasetVersion) {
        Map<String, RegionCoverage> coverage = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        SELECT region, COUNT(DISTINCT station_id) AS station_count,
                               COUNT(*) AS log_count
                        FROM synthetic_demo_incident_logs
                        WHERE dataset_version = ?
                        GROUP BY region
                        """, (RowCallbackHandler) resultSet -> coverage.put(resultSet.getString("region"),
                        new RegionCoverage(resultSet.getString("region"),
                                resultSet.getInt("station_count"),
                                resultSet.getInt("log_count"))), datasetVersion);
        return Map.copyOf(coverage);
    }

    private static void bind(PreparedStatement statement,
                             SyntheticDemoIncidentLog record) throws SQLException {
        int index = 1;
        statement.setString(index++, record.demoLogId());
        statement.setString(index++, record.datasetVersion());
        statement.setString(index++, record.scenarioId());
        statement.setString(index++, record.stationId());
        statement.setString(index++, record.stationDisplayName());
        statement.setString(index++, record.region());
        statement.setObject(index++, record.occurredAt());
        statement.setString(index++, record.facilityName());
        statement.setString(index++, record.facilityAddress());
        statement.setString(index++, record.incidentSubstanceName());
        statement.setString(index++, record.incidentSubstanceCas());
        statement.setString(index++, record.conflictSubstanceName());
        statement.setString(index++, record.conflictSubstanceCas());
        statement.setString(index++, record.ruleId());
        statement.setString(index++, record.ruleVersion());
        statement.setString(index++, record.severity());
        statement.setString(index++, record.riskLevel());
        statement.setString(index++, record.riskLevelKo());
        statement.setString(index++, join(record.hazardCodes()));
        statement.setString(index++, join(record.gasProducts()));
        statement.setString(index++, record.riskSummary());
        statement.setString(index++, record.performedAction());
        statement.setString(index++, record.briefApplicationStatus());
        statement.setString(index++, record.additionalFactor());
        statement.setString(index++, record.finalResponseOutcome());
        statement.setString(index++, record.dataClassification());
        statement.setBoolean(index++, record.operationalRecord());
        statement.setString(index++, record.sourceName());
        statement.setString(index++, record.sourceUrl());
        statement.setString(index++, record.disclosure());
        statement.setObject(index, record.createdAt());
    }

    private static String join(List<String> values) {
        return String.join("|", values);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split("\\|", -1));
    }

    public record RegionCoverage(String region, int stationCount, int logCount) {
    }
}
