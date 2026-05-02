package com.TeslaProject.TeslaProject.Services;

import com.TeslaProject.TeslaProject.models.Reservation;
import com.TeslaProject.TeslaProject.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;

@Service
public class ReservationService {
    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    private final ReservationRepository reservationRepository;
    private final JavaMailSender mailSender;

    public ReservationService(ReservationRepository reservationRepository, JavaMailSender mailSender) {
        this.reservationRepository = reservationRepository;
        this.mailSender = mailSender;
    }

    public Reservation createReservation(String userId, String stationId, String stationName, String startTimeStr, String endTimeStr) {
        logger.info("Creating reservation for userId: {}, stationId: {}, startTime: {}, endTime: {}",
                userId, stationId, startTimeStr, endTimeStr);

        // Parse times
        LocalDateTime startTime = parseDateTime(startTimeStr);
        LocalDateTime endTime = parseDateTime(endTimeStr);

        // Validate times
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Invalid date/time format. Use ISO 8601 format.");
        }

        if (endTime.isBefore(startTime) || endTime.isEqual(startTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start time must be in the future.");
        }

        // Cancel any existing active reservation for this user
        Optional<Reservation> existingActive = reservationRepository.findByUserIdAndStatus(userId, "ACTIVE");
        if (existingActive.isPresent()) {
            Reservation oldReservation = existingActive.get();
            oldReservation.setStatus("CANCELLED");
            reservationRepository.save(oldReservation);
            logger.info("Cancelled old reservation: {}", oldReservation.getId());
        }

        // Create new reservation
        Reservation reservation = new Reservation(userId, stationId, stationName, startTime, endTime);
        Reservation saved = reservationRepository.save(reservation);

        // Send confirmation email
        sendConfirmationEmail(saved);

        logger.info("Reservation created successfully: {}", saved.getId());
        return saved;
    }

    private void sendConfirmationEmail(Reservation reservation) {
        try {
            // Email recipient
            String recipientEmail = "mohamedaminejandoubi35@gmail.com";

            // Compose the email
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipientEmail);
            message.setSubject("Reservation Confirmation");
            message.setText(String.format(
                    "Dear User,\n\nYour reservation has been confirmed.\n\nDetails:\n" +
                            "Station Name: %s\nStart Time: %s\nEnd Time: %s\n\nThank you for using our service.\n\nBest regards,\nTesla Project Team",
                    reservation.getStationName(),
                    reservation.getStartTime().format(ISO_FORMATTER),
                    reservation.getEndTime().format(ISO_FORMATTER)
            ));

            // Send the email
            mailSender.send(message);
            logger.info("Confirmation email sent to {}", recipientEmail);
        } catch (Exception e) {
            logger.error("Failed to send confirmation email: {}", e.getMessage(), e);
        }
    }

    public Optional<Reservation> getActiveReservation(String userId) {
        logger.info("Getting active reservation for userId: {}", userId);
        return reservationRepository.findByUserIdAndStatus(userId, "ACTIVE");
    }

    public void cancelReservation(String reservationId) {
        logger.info("Cancelling reservation: {}", reservationId);
        Optional<Reservation> reservation = reservationRepository.findById(reservationId);
        if (reservation.isPresent()) {
            Reservation res = reservation.get();
            res.setStatus("CANCELLED");
            reservationRepository.save(res);
            logger.info("Reservation cancelled: {}", reservationId);
        } else {
            logger.warn("Reservation not found: {}", reservationId);
        }
    }

    public Long getActiveReservationCount(String stationId) {
        long count = reservationRepository.countByStationIdAndStatus(stationId, "ACTIVE");
        logger.info("Active reservations for stationId {}: {}", stationId, count);
        return count;
    }

    public boolean isStationAvailable(String stationId, int totalSlots) {
        Long activeReservations = getActiveReservationCount(stationId);
        return activeReservations < totalSlots;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr, ISO_FORMATTER);
        } catch (Exception e) {
            logger.warn("Failed to parse date time: {}", dateTimeStr);
            return null;
        }
    }
}