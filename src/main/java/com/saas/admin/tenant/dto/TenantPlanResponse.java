package com.saas.admin.tenant.dto;

import com.saas.admin.tenant.domain.TenantPlan;

/** 요금제 선택지(콤보박스)용 최소 정보. */
public record TenantPlanResponse(Long planId, String code, String name, int monthlyPrice, boolean active) {
    public static TenantPlanResponse from(TenantPlan p) {
        return new TenantPlanResponse(p.getId(), p.getCode(), p.getName(), p.getMonthlyPrice(), p.isActive());
    }
}
