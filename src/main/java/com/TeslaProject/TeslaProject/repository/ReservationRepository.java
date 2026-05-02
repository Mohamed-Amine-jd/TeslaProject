package com.TeslaProject.TeslaProject.repository;

import com.TeslaProject.TeslaProject.models.Reservation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends MongoRepository<Reservation, String> {
    Optional<Reservation> findByUserIdAndStatus(String userId, String status);
    List<Reservation> findByUserId(String userId);
    List<Reservation> findByStationIdAndStatus(String stationId, String status);
    Long countByStationIdAndStatus(String stationId, String status);
}
