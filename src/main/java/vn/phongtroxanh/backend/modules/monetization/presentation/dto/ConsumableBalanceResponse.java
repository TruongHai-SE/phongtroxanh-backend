package vn.phongtroxanh.backend.modules.monetization.presentation.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumableBalanceResponse {
    private int swipesLeft;
    private int boostsLeft;
}
