package com.techsync;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaticAssetController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        // Avoid noisy 404s in browser console when no favicon asset is provided.
        return ResponseEntity.noContent().build();
    }
}
