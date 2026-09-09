package vn.phongtroxanh.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailResponse {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String code;
    private List<InvalidParam> invalidParams;
    @Builder.Default
    private Instant timestamp = Instant.now();
    private String requestId;
}
