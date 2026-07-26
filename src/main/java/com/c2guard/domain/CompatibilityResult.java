package com.c2guard.domain;

/**
 * check_pair() 판정 결과 상태.
 * rule_engine_draft.py 의 UNKNOWN / UNCLASSIFIED / O / X 4단계를 그대로 옮김.
 */
public enum CompatibilityResult {
    COMPATIBLE,      // O - 혼재 가능
    INCOMPATIBLE,    // X - 혼재 불가
    SAME_CLASS,      // - 동일 유별, 지정수량 1/10 이하 등 별도 확인 필요
    UNCLASSIFIED,    // 물질은 registry에 있으나 유별 미분류
    UNKNOWN          // registry에 없는 물질
}
