package com.fitouts.shared.geocode;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/geocode")
@RequiredArgsConstructor
public class GeocodeController extends BaseController {

    private final GeocodeService geocodeService;

    @GetMapping("/resolve")
    public Object resolve(@RequestParam("q") String query) {
        try {
            return successResponse(geocodeService.resolve(query));
        } catch (Exception e) {
            return failureResponse("Unable to resolve location", e.getMessage());
        }
    }
}
