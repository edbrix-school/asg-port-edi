package com.asg.portediintegration.controller;

import com.asg.portediintegration.service.GlobalParameterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.asg.common.lib.dto.response.ApiResponse.success;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/config")
public class GlobalParameterController {

    private final GlobalParameterService globalParameterService;

    @PostMapping("/reload")
    public ResponseEntity<?> reloadParameters() {
        log.info("Manual reload of global parameters requested");
        globalParameterService.reload();
        return success("Global parameters reloaded successfully", null);
    }
}