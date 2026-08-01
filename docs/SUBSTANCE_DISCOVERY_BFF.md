# 물질검색·발견 BFF 연동

FE 물질검색 탭은 `POST /api/c2guard/v1/substances/discover`만 호출한다. BE는 query와
Top-K 범위를 검증하고 AI `POST /api/v1/substances/discover`에 같은 request ID로 전달한다.

응답에는 복수 후보, CAS, 일치한 관찰, 공식 출처와 확인 필요 상태만 포함한다. 후보 점수는
확률로 표시하지 않으며 후보 없음은 물질 부재나 안전을 뜻하지 않는다. 상세 근거가 적재되지
않은 CAS는 다른 물질의 근거로 대체하지 않는다.

사용자 인증과 운영 CORS/cookie 경계는 #5가 담당한다. #5가 병합되기 전에는 이 endpoint를
운영 공개하지 않는다.
