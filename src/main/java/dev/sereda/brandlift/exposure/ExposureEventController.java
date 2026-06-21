package dev.sereda.brandlift.exposure;

import dev.sereda.brandlift.exposure.ExposureEventService.IngestResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exposure-events")
public class ExposureEventController {

    private final ExposureEventService service;

    public ExposureEventController(ExposureEventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExposureEventResponse> ingest(@Valid @RequestBody IngestExposureEventRequest request) {
        IngestResult result = service.ingest(request);
        ExposureEventResponse body = ExposureEventResponse.from(result.event(), result.duplicate());
        // New event -> 201 Created; duplicate delivery is a successful no-op -> 200 OK.
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(body);
    }
}
