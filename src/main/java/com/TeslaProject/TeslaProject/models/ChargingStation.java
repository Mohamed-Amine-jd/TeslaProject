package com.TeslaProject.TeslaProject.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "charging_stations")
public class ChargingStation {
    @Id
    private String id;
    private String name;
    private ChargingStationCoordinates coordinates;
    private int totalSlots;
    private int availableSlots;
    private int power;

    public ChargingStation(String id, String name, ChargingStationCoordinates coordinates, int totalSlots, int availableSlots, int power) {
        this.id = id;
        this.name = name;
        this.coordinates = coordinates;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
        this.power = power;
    }

    public ChargingStation() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ChargingStationCoordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(ChargingStationCoordinates coordinates) {
        this.coordinates = coordinates;
    }

    public int getTotalSlots() {
        return totalSlots;
    }

    public void setTotalSlots(int totalSlots) {
        this.totalSlots = totalSlots;
    }

    public int getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(int availableSlots) {
        this.availableSlots = availableSlots;
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }
}