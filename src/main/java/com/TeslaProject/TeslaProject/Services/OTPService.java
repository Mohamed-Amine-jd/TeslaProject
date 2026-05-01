package com.TeslaProject.TeslaProject.Services;

import com.TeslaProject.TeslaProject.models.Client;
import com.TeslaProject.TeslaProject.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PostConstruct;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.security.SecureRandom;

@Service
public class OTPService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private ClientRepository clientRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.from-number:}")
    private String twilioFromNumber;

    @Value("${twilio.messaging-service-sid:}")
    private String twilioMessagingServiceSid;

    private boolean twilioEnabled = false;

    @PostConstruct
    public void initTwilio() {
        if (twilioAccountSid != null && !twilioAccountSid.isBlank()
            && twilioAuthToken != null && !twilioAuthToken.isBlank()
            && ((twilioFromNumber != null && !twilioFromNumber.isBlank())
            || (twilioMessagingServiceSid != null && !twilioMessagingServiceSid.isBlank()))) {
            try {
                Twilio.init(twilioAccountSid, twilioAuthToken);
                twilioEnabled = true;
                System.out.println("Twilio initialized for SMS sending");
            } catch (Exception e) {
                twilioEnabled = false;
                System.err.println("Failed to init Twilio: " + e.getMessage());
            }
        }
    }

    public String generateAndSaveOTP(String matricule) {
        int code = secureRandom.nextInt(1_000_000); // 0..999999
        String otp = String.format("%06d", code);
        Client client = clientRepository.findByMatricule(matricule)
                .orElseThrow(() -> new RuntimeException("Client non trouvé en base de données"));

        client.setOtpCode(otp);
        client.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
        clientRepository.save(client);
        return otp;
    }

    // Dans ton OTPService.java
    public void sendOTPEmail(String email, String otp) {
        try {
            if (mailSender != null) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Votre code OTP");
                message.setText("Votre code OTP: " + otp + " (valable 5 minutes)");
                mailSender.send(message);
                System.out.println("📩 Email envoyé: " + otp + " à " + email);
            } else {
                System.out.println("📩 [MOCK EMAIL] Envoi du code " + otp + " à " + email);
            }
        } catch (Exception e) {
            System.err.println("Erreur envoi email : " + e.getMessage());
        }
    }

    public void sendOTPSMS(String phone, String otp) {
        try {
            if (twilioEnabled) {
                Message message;
                String body = "Votre code OTP: " + otp + " (valable 5 minutes)";
                if (twilioMessagingServiceSid != null && !twilioMessagingServiceSid.isBlank()) {
                    message = Message.creator(
                            new PhoneNumber(phone),
                            twilioMessagingServiceSid,
                            body
                    ).create();
                } else {
                    message = Message.creator(
                            new PhoneNumber(phone),
                            new PhoneNumber(twilioFromNumber),
                            body
                    ).create();
                }
                System.out.println("📱 SMS envoyé via Twilio: SID=" + message.getSid());
            } else {
                System.out.println("📱 [MOCK SMS] Envoi du code " + otp + " à " + phone);
            }
        } catch (Exception e) {
            System.err.println("Erreur envoi SMS : " + e.getMessage());
        }
    }

    public boolean verifyOTP(String matricule, String code) {
        return clientRepository.findByMatricule(matricule)
                .filter(c -> c.getOtpCode() != null && c.getOtpCode().equals(code))
                .filter(c -> c.getOtpExpiry() != null && c.getOtpExpiry().isAfter(LocalDateTime.now()))
                .map(c -> {
                    c.setOtpCode(null);
                    c.setOtpExpiry(null);
                    clientRepository.save(c);
                    return true;
                })
                .orElse(false);
    }
}