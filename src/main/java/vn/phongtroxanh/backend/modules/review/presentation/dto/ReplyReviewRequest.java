package vn.phongtroxanh.backend.modules.review.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyReviewRequest {

    @NotBlank(message = "Nội dung phản hồi không được để trống")
    private String replyComment;

    public String getReply() {
        return replyComment;
    }

    public void setReply(String reply) {
        this.replyComment = reply;
    }
}
