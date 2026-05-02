package com.TeslaProject.TeslaProject.controller;

import com.TeslaProject.TeslaProject.Services.ChargingStationService;
import com.TeslaProject.TeslaProject.models.ChargingStation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/public/charging-stations")
public class ChargingStationController {

    private static final Logger logger = Logger.getLogger(ChargingStationController.class.getName());

    private final ChargingStationService chargingStationService;

    public ChargingStationController(ChargingStationService chargingStationService) {
        this.chargingStationService = chargingStationService;
    }

    @GetMapping
    public List<ChargingStation> getAllStations() {
        logger.info("GET /api/public/charging-stations received: fetching charging stations from MongoDB");
        List<ChargingStation> stations = chargingStationService.findAllStations();
        logger.info("GET /api/public/charging-stations returning " + stations.size() + " stations");
        return stations;
    }
}