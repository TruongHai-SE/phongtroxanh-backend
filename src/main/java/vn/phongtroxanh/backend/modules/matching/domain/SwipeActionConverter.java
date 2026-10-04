package vn.phongtroxanh.backend.modules.matching.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class SwipeActionConverter implements AttributeConverter<SwipeAction, String> {

    @Override
    public String convertToDatabaseColumn(SwipeAction attribute) {
        if (attribute == null) {
            return null;
        }
        return switch (attribute) {
            case LIKE -> "RIGHT";
            case DISLIKE -> "LEFT";
        };
    }

    @Override
    public SwipeAction convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData) {
            case "RIGHT", "SUPER" -> SwipeAction.LIKE;
            case "LEFT" -> SwipeAction.DISLIKE;
            default -> throw new IllegalArgumentException("Unknown swipe_dir_enum value: " + dbData);
        };
    }
}
