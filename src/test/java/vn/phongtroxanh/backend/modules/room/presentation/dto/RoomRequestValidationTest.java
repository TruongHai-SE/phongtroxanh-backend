package vn.phongtroxanh.backend.modules.room.presentation.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomRequestValidationTest {
    @Test void rejectsNegativeDepositAndBlankFee() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = CreateRoomRequest.builder().title("Room").description("Room details").roomType("ROOM")
                    .price(BigDecimal.TEN).depositAmount(BigDecimal.valueOf(-1)).areaSqm(BigDecimal.TEN)
                    .addressStreet("12 Main Street").district("District 1")
                    .fees(List.of(RoomFeeDTO.builder().feeLabel(" ").feeValue(" ").build())).build();
            assertThat(factory.getValidator().validate(request)).extracting(v -> v.getPropertyPath().toString())
                    .contains("depositAmount", "fees[0].feeLabel", "fees[0].feeValue");
        }
    }
}
