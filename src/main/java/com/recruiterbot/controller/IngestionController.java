package com.recruiterbot.controller;

import com.recruiterbot.dto.IngestionResponse;
import com.recruiterbot.service.IngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /** Loads the assignment's candidates and jobs. Idempotent. */
    @PostMapping("/ingest")
    public IngestionResponse ingest() {
        return ingestionService.ingest();
    }
}
