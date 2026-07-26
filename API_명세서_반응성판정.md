# API 명세서 - 반응성 판정 / 시설 조회

| 이름 | Method | Endpoint | Status |
|---|---|---|---|
| 물질 반응성/혼재 판정 | POST | /api/compatibility/check | 개발 완료 (v0.2, 유별 데이터 대기중) |
| 시설 보유물질 조회 (정확일치) | GET | /api/facilities/{name}/substances | 개발 완료 (샘플 3종만 크롤링됨) |
| 시설명 검색 (부분일치) | GET | /api/facilities/search?keyword= | 개발 완료 |
| 시설+사고물질 통합 조회 (핵심 기능) | POST | /api/incident-check | 개발 완료 |

## 물질 반응성/혼재 판정

### 기능명
두 화학물질명을 받아, 위험물안전관리법 별표19 기준 혼재 가능 여부를 판정한다.

### 요청 (Request)

```
POST /api/compatibility/check
Content-Type: application/json
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| substanceA | String | 필수 | 물질명 (한글, registry 등록명과 일치해야 함) |
| substanceB | String | 필수 | 물질명 |

**요청 예시**
```json
{
  "substanceA": "황산",
  "substanceB": "메탄올"
}
```

### 응답 (Response)

| 필드 | 타입 | 설명 |
|---|---|---|
| substanceA / substanceB | String | 요청받은 물질명 그대로 반환 |
| result | String | `COMPATIBLE`(혼재가능) / `INCOMPATIBLE`(혼재불가) / `SAME_CLASS`(동일유별,별도확인) / `UNCLASSIFIED`(유별미분류) / `UNKNOWN`(registry에없음) |
| reason | String | 판정 사유 |
| basis | String \| null | 판정 근거 (법령명 등). UNKNOWN일 때는 null |

**응답 예시 (현재 상태 - 유별 데이터 없어서 대부분 이렇게 나옴)**
```json
{
  "substanceA": "황산",
  "substanceB": "메탄올",
  "result": "UNCLASSIFIED",
  "reason": "물질은 확인됐지만 유별 미분류: 황산",
  "basis": "위험물제조소 현황 / MSDS 데이터로 유별 분류 필요"
}
```

**응답 예시 (유별 채워진 이후 - CAMEO/MSDS 연동 후 기대되는 형태)**
```json
{
  "substanceA": "톨루엔",
  "substanceB": "아세톤",
  "result": "SAME_CLASS",
  "reason": "4류 - 4류 혼재기준",
  "basis": "위험물안전관리법 시행령 별표19"
}
```

### 실패 케이스

| 상황 | 처리 |
|---|---|
| registry에 없는 물질명 입력 | 200 OK로 응답하되 `result: UNKNOWN` 반환 (에러가 아니라 정상 판정 결과 중 하나로 취급) |
| substanceA/B 누락 | (TODO) 400 Bad Request 처리 필요 — 현재 버전엔 미구현 |

### 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026.07.20 | v0.2 — 별표19 매트릭스 기반 판정 API 최초 작성 (백승효) |
| 2026.07.21 | v0.3 — 시설 보유물질 조회 + 통합 조회 API 추가 (실크롤링 데이터 연동) |

---

## 시설+사고물질 통합 조회 (핵심 기능)

### 기능명
출동시설명 + 사고물질을 받아, 그 시설이 보유한 모든 물질과 사고물질의 반응성을 한 번에 검토한다.
기획서 서비스 흐름의 "시설 보유 화학물질 조회 → Rule-Based 대응 충돌 검토" 단계에 대응.

### 요청

```
POST /api/incident-check
Content-Type: application/json
```

```json
{
  "facilityName": "(주)LG생활건강",
  "incidentSubstance": "톨루엔"
}
```

### 응답

```json
{
  "facilityName": "(주)LG생활건강",
  "incidentSubstance": "톨루엔",
  "heldSubstanceCount": 1,
  "facilityFound": true,
  "hasIncompatible": false,
  "results": [
    {
      "substanceA": "톨루엔",
      "substanceB": "황산",
      "result": "UNCLASSIFIED",
      "reason": "물질은 확인됐지만 유별 미분류: 톨루엔",
      "basis": "위험물제조소 현황 / MSDS 데이터로 유별 분류 필요"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| facilityFound | 시설명이 크롤링 데이터에 있었는지. false면 "이 시설은 아직 데이터 없음" 안내 필요 |
| hasIncompatible | 보유물질 중 하나라도 INCOMPATIBLE 나오면 true — 프론트에서 빨간 경고 배너 트리거용 |
| results | 보유물질 하나하나에 대한 개별 판정 목록 |

### 참고
- 지금은 크롤링이 염산·황산·수산화나트륨 3종만 된 상태라, 대부분 시설은 `heldSubstanceCount`가 0~1로 작게 나옴 — 전체 크롤링 완료되면 자동으로 늘어남 (CSV만 교체하면 코드 변경 없이 반영)

