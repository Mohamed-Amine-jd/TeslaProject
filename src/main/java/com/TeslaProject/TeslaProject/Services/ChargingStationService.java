package com.TeslaProject.TeslaProject.Services;

import com.TeslaProject.TeslaProject.models.ChargingStation;
import com.TeslaProject.TeslaProject.models.ChargingStationCoordinates;
import com.TeslaProject.TeslaProject.repository.ChargingStationRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;

@Service
public class ChargingStationService {

    private static final Logger logger = Logger.getLogger(ChargingStationService.class.getName());

    private final ChargingStationRepository chargingStationRepository;

    public ChargingStationService(ChargingStationRepository chargingStationRepository) {
        this.chargingStationRepository = chargingStationRepository;
    }

    @PostConstruct
    public void seedDefaultStations() {
        List<ChargingStation> defaults = List.of(
                new ChargingStation(null, "Tesla Tunis", new ChargingStationCoordinates(36.8065, 10.1815), 12, 8, 150),
                new ChargingStation(null, "Tesla Lac", new ChargingStationCoordinates(36.8456, 10.2510), 10, 6, 180),
                new ChargingStation(null, "Tesla Marsa", new ChargingStationCoordinates(36.8782, 10.3242), 8, 5, 120),
                new ChargingStation(null, "Tesla Sousse", new ChargingStationCoordinates(35.8256, 10.6084), 14, 9, 180),
                new ChargingStation(null, "Tesla Sfax", new ChargingStationCoordinates(34.7406, 10.7603), 16, 11, 250),
                new ChargingStation(null, "Tesla Monastir", new ChargingStationCoordinates(35.7770, 10.8262), 9, 4, 120),
                new ChargingStation(null, "Tesla Bizerte", new ChargingStationCoordinates(37.2746, 9.8739), 10, 7, 150),
                new ChargingStation(null, "Tesla Gabes", new ChargingStationCoordinates(33.8815, 10.0982), 12, 8, 180),
                new ChargingStation(null, "Tesla Djerba", new ChargingStationCoordinates(33.8076, 10.8451), 8, 5, 120)
        );

        Set<String> existingNames = new HashSet<>();
        for (ChargingStation station : chargingStationRepository.findAll()) {
            if (station.getName() != null) {
                existingNames.add(station.getName().trim().toLowerCase());
            }
        }

        List<ChargingStation> toInsert = defaults.stream()
                .filter(station -> !existingNames.contains(station.getName().trim().toLowerCase()))
                .toList();

        if (toInsert.isEmpty()) {
            logger.info("All default charging stations already present in MongoDB");
            return;
        }

        logger.info("Seeding " + toInsert.size() + " missing default charging stations into MongoDB");
        chargingStationRepository.saveAll(toInsert);
        logger.info("Missing default charging stations seeded successfully");
    }

    public List<ChargingStation> findAllStations() {
        logger.info("MongoDB query started: chargingStationRepository.findAll()");
        List<ChargingStation> stations = chargingStationRepository.findAll();
        logger.info("MongoDB query finished: found " + stations.size() + " charging stations");
        return stations;
    }
}