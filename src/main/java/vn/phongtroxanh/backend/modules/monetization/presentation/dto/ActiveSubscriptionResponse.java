package vn.phongtroxanh.backend.modules.monetization.presentation.dto;

import java.time.Instant;

public record ActiveSubscriptionResponse(String planId, String planName, Instant endDate) {}
