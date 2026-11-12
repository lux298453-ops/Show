package com.wireforge.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController
    static class Failures {
        @GetMapping("/argument") String argument() { throw new IllegalArgumentException("HTML 内容不能为空"); }
        @GetMapping("/state") String state() { throw new IllegalStateException("页面正在编辑"); }
        @GetMapping("/missing") String missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "素材不存在"); }
        @GetMapping("/internal") String internal() { throw new UnsupportedOperationException("internal error"); }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Failures())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void clientArgumentsAre400AndExistingStatusCodesArePreserved() throws Exception {
        mvc.perform(get("/argument")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("HTML 内容不能为空"));
        mvc.perform(get("/state")).andExpect(status().isBadRequest());
        mvc.perform(get("/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/internal")).andExpect(status().isInternalServerError());
    }
}
