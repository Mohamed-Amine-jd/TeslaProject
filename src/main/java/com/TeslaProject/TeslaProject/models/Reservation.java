package com.TeslaProject.TeslaProject.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "reservations")
public class Reservation {
    @Id
    private String id;
    private String userId;
    private String stationId;
    private String stationName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status; // ACTIVE, COMPLETED, CANCELLED
    private LocalDateTime createdAt;

    // Constructors
    public Reservation() {
    }

    public Reservation(String userId, String stationId, String stationName, LocalDateTime startTime, LocalDateTime endTime) {
        this.userId = userId;
        this.stationId = stationId;
        this.stationName = stationName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

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

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
