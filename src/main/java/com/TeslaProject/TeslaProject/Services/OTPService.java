package com.TeslaProject.TeslaProject.Services;

import com.TeslaProject.TeslaProject.models.Client;
import com.TeslaProject.TeslaProject.repository.ClientRepository;
import com.TeslaProject.TeslaProject.util.MatriculeVariants;
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
        Client client = clientRepository.findFirstByMatriculeIn(MatriculeVariants.aliases(matricule))
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
/*
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
*/
public void sendOTPSMS(String phone, String otp) {
    try {
        // Nettoyage et vérification du numéro
        String targetPhone = phone.trim();

        // Log pour débogage
        System.out.println("🚀 Tentative d'envoi SMS à : " + targetPhone);

        if (twilioEnabled) {
            // Sécurité : Twilio exige le format international (ex: +216XXXXXXXX)
            if (!targetPhone.startsWith("+")) {
                System.err.println("⚠️ ALERTE : Le numéro " + targetPhone + " ne commence pas par '+'. Twilio risque d'échouer.");
                // Optionnel : Forcer l'indicatif Tunisie si absent
                // targetPhone = "+216" + targetPhone;
            }

            Message message;
            String body = "Votre code OTP Tesla: " + otp + " (valable 5 minutes)";

            if (twilioMessagingServiceSid != null && !twilioMessagingServiceSid.isBlank()) {
                message = Message.creator(
                        new PhoneNumber(targetPhone),
                        twilioMessagingServiceSid,
                        body
                ).create();
            } else {
                message = Message.creator(
                        new PhoneNumber(targetPhone),
                        new PhoneNumber(twilioFromNumber),
                        body
                ).create();
            }

            System.out.println("✅ SMS envoyé avec succès ! Numéro: " + targetPhone + " | SID: " + message.getSid());

        } else {
            System.out.println("📱 [MOCK SMS] Mode test activé. Code " + otp + " pour " + targetPhone);
        }
    } catch (Exception e) {
        System.err.println("❌ Erreur Twilio critique pour le numéro " + phone + " : " + e.getMessage());
    }
}
    public boolean verifyOTP(String matricule, String code) {
        return clientRepository.findFirstByMatriculeIn(MatriculeVariants.aliases(matricule))
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

    /** True if code matches and not expired; does not clear the OTP (for Keycloak step before consume). */
    public boolean otpCodeValid(String matricule, String code) {
        return clientRepository.findFirstByMatriculeIn(MatriculeVariants.aliases(matricule))
                .filter(c -> c.getOtpCode() != null && c.getOtpCode().equals(code))
                .filter(c -> c.getOtpExpiry() != null && c.getOtpExpiry().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    public void clearOtpForMatricule(String matricule) {
        clientRepository.findFirstByMatriculeIn(MatriculeVariants.aliases(matricule)).ifPresent(c -> {
            c.setOtpCode(null);
            c.setOtpExpiry(null);
            clientRepository.save(c);
        });
    }
}