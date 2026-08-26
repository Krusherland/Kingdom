package KruDev.Kingdom.dto;

import KruDev.Kingdom.model.enums.ActionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActionRequest {

    @NotNull(message = "Action type is required")
    private ActionType actionType;

    @NotNull(message = "Target nickname is required")
    private String targetNickname;
}
