package com.fitouts.workitemconfiguration.api;

// import jakarta.validation.constraints.NotBlank;
// import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkItemMasterCreateRequest {

    // @NotBlank(message = "Name is required")
    // @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    // @NotBlank(message = "Code is required")
    // @Size(max = 50, message = "Code must not exceed 50 characters")
    private String code;
}
