package vn.phongtroxanh.backend.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    MockMvc mvc;
    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InputController()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void malformedJsonReturns400WithExistingProblemShape() throws Exception {
        mvc.perform(post("/input").contentType(MediaType.APPLICATION_JSON).content("{broken")
                        .header("X-Request-ID", "test-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_DATA"))
                .andExpect(jsonPath("$.instance").value("/input"))
                .andExpect(jsonPath("$.requestId").value("test-request"));
    }

    @Test void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/input/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test void missingRequiredParameterReturns400() throws Exception {
        mvc.perform(get("/required")).andExpect(status().isBadRequest());
    }

    @Test void beanConstraintViolationReturns400() throws Exception {
        mvc.perform(get("/failure/validation")).andExpect(status().isBadRequest());
    }

    @Test void concurrentDataConstraintReturns409WithoutDatabaseDetails() throws Exception {
        mvc.perform(get("/failure/constraint")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret_sql"))));
    }

    @Test void optimisticLockFailureReturns409() throws Exception {
        mvc.perform(get("/failure/optimistic")).andExpect(status().isConflict());
    }

    @Test void ordinaryProgrammingArgumentBugRemains500() throws Exception {
        mvc.perform(get("/failure/programming")).andExpect(status().isInternalServerError());
    }

    @RestController
    static class InputController {
        @PostMapping("/input") Map<String, Object> input(@RequestBody Map<String, Object> body) { return body; }
        @GetMapping("/input/{id}") String id(@PathVariable UUID id) { return id.toString(); }
        @GetMapping("/required") String required(@RequestParam String value) { return value; }
        @GetMapping("/failure/{type}") void failure(@PathVariable String type) {
            switch (type) {
                case "validation" -> throw new ConstraintViolationException("invalid", Set.of());
                case "constraint" -> throw new DataIntegrityViolationException("secret_sql");
                case "optimistic" -> throw new ObjectOptimisticLockingFailureException(InputController.class, UUID.randomUUID());
                default -> throw new IllegalArgumentException("programming defect");
            }
        }
    }
}
