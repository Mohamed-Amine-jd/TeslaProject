package com.TeslaProject.TeslaProject.repository;

import com.TeslaProject.TeslaProject.models.ChargingStation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChargingStationRepository extends MongoRepository<ChargingStation, String> {
}