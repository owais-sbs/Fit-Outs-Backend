package com.fitouts.workitemconfiguration.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.fitouts.procurement.domain.Material;
import com.fitouts.workitemconfiguration.api.WorkItemMaterialLineRequest;
import com.fitouts.workitemconfiguration.domain.WorkItem;
import com.fitouts.workitemconfiguration.domain.WorkItemMaterial;

public final class WorkItemPricingHelper {

    private WorkItemPricingHelper() {}

    public static BigDecimal calculateMaterialCost(List<WorkItemMaterial> lines) {
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (WorkItemMaterial line : lines) {
            total = total.add(lineCost(line));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineCost(WorkItemMaterial line) {
        Material material = line.getMaterial();
        if (material == null || material.getCostPrice() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal qty = line.getQuantityPerUnit() != null ? line.getQuantityPerUnit() : BigDecimal.ONE;
        BigDecimal wastage = line.getWastagePercent() != null ? line.getWastagePercent() : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return material.getCostPrice().multiply(qty).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal lineCost(Material material, WorkItemMaterialLineRequest line) {
        if (material == null || material.getCostPrice() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal qty = line.getQuantityPerUnit() != null ? line.getQuantityPerUnit() : BigDecimal.ONE;
        BigDecimal wastage = line.getWastagePercent() != null ? line.getWastagePercent() : BigDecimal.ZERO;
        BigDecimal factor = BigDecimal.ONE.add(wastage.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return material.getCostPrice().multiply(qty).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public static void applyPricing(WorkItem workItem, BigDecimal manualCost, boolean costOverride,
            boolean sellingOverride) {
        if (!costOverride && manualCost != null) {
            workItem.setCostPrice(manualCost);
        }
        if (!sellingOverride) {
            BigDecimal cost = workItem.getCostPrice() != null ? workItem.getCostPrice() : BigDecimal.ZERO;
            BigDecimal markup = workItem.getMarkupPercentage() != null ? workItem.getMarkupPercentage() : BigDecimal.ZERO;
            BigDecimal factor = BigDecimal.ONE.add(markup.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            workItem.setDefaultRate(cost.multiply(factor).setScale(2, RoundingMode.HALF_UP));
        }
    }
}
