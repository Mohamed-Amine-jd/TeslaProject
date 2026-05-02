package com.TeslaProject.TeslaProject.controller;

import com.TeslaProject.TeslaProject.models.Reservation;
import com.TeslaProject.TeslaProject.Services.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "*")
public class ReservationController {
    private static final Logger logger = LoggerFactory.getLogger(ReservationController.class);
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<?> createReservation(
            @RequestBody ReservationRequest request,
            Authentication authentication) {
        logger.info("POST /api/reservations - Create reservation request for station: {} from {} to {}", 
                request.getStationId(), request.getStartTime(), request.getEndTime());
        
        try {
            String userId = authentication.getName();
            Reservation reservation = reservationService.createReservation(
                    userId,
                    request.getStationId(),
                    request.getStationName(),
                    request.getStartTime(),
                    request.getEndTime()
            );
            logger.info("Reservation created successfully: {}", reservation.getId());
            return ResponseEntity.ok(reservation);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid reservation request: {}", e.getMessage());
            return ResponseEntity.status(400).body("Invalid reservation: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error creating reservation: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error creating reservation: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getActiveReservation(Authentication authentication) {
        logger.info("GET /api/reservations - Getting active reservation for user: {}", authentication.getName());
        
        try {
            String userId = authentication.getName();
            Optional<Reservation> reservation = reservationService.getActiveReservation(userId);
            return ResponseEntity.ok(reservation.orElse(null));
        } catch (Exception e) {
            logger.error("Error getting reservation: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error getting reservation: " + e.getMessage());
        }
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<?> getReservationCount(@PathVariable String stationId) {
        logger.info("GET /api/reservations/station/{} - Getting reservation count", stationId);
        
        try {
            Long count = reservationService.getActiveReservationCount(stationId);
            return ResponseEntity.ok(new CountResponse(count));
        } catch (Exception e) {
            logger.error("Error getting reservation count: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error getting reservation count: " + e.getMessage());
        }
    }

    @DeleteMapping("/{reservationId}")
    public ResponseEntity<?> cancelReservation(@PathVariable String reservationId) {
        logger.info("DELETE /api/reservations/{} - Cancelling reservation", reservationId);
        
        try {
            reservationService.cancelReservation(reservationId);
            return ResponseEntity.ok("Reservation cancelled successfully");
        } catch (Exception e) {
            logger.error("Error cancelling reservation: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error cancelling reservation: " + e.getMessage());
        }
    }

    // Request/Response DTOs
    public static class ReservationRequest {
        private String stationId;
        private String stationName;
        private String startTime;  // ISO 8601 format
        private String endTime;    // ISO 8601 format

        public String getStationId() {
            return stationId;
        }

        public void setStationId(String stationId) {
            this.stationId = stationId;
        }

        public String getStationName() {
            return stationName;
        }

        public void setStationName(String stationName) {
            this.stationName = stationName;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }
    }

    public static class CountResponse {
        private Long count;

        public CountResponse(Long count) {
            this.count = count;
        }

        public Long getCount() {
            return count;
        }

        public void setCount(Long count) {
            this.count = count;
        }
    }
}
