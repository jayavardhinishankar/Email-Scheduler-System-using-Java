import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;

/**
 * A reusable service class to configure and send emails using the Jakarta Mail API.
 * This class has been refactored to lazily create the mail Session.
 * * NOTE: Requires 'jakarta.mail' and 'jakarta.activation' JARs in the classpath.
 */
public class EmailSender {
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final boolean useTls;

    public EmailSender(String smtpHost, int smtpPort, String username, String password, boolean useTls) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.useTls = useTls;
    }

    /**
     * Configures and creates a new Jakarta Mail Session instance.
     */
    private Session createSession() {
        Properties props = new Properties();
        
        // Basic configuration
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        
        // TLS/SSL configuration
        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            // Recommended for modern Gmail connections
            props.put("mail.smtp.ssl.protocols", "TLSv1.2"); 
        } else {
            // For SMTPS (usually port 465)
            props.put("mail.smtp.ssl.enable", "true");
        }
        
        // Connection timeouts and debug
        props.put("mail.smtp.connectiontimeout", 10000); // 10 seconds
        props.put("mail.smtp.timeout", 10000);       // 10 seconds
        props.put("mail.debug", "true"); 

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };

        return Session.getInstance(props, auth);
    }

    /**
     * Sends a simple text email using the configured session.
     * @param toEmail The recipient's email address.
     * @param subject The subject line of the email.
     * @param body The body content of the email.
     * @throws MessagingException if the email fails to send.
     */
    public void send(String toEmail, String subject, String body) throws MessagingException {
        // Create a fresh session for each send operation (or reuse if performance is key)
        Session session = createSession();

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject);
        message.setText(body);

        Transport.send(message);
        System.out.println("Email sent to: " + toEmail);
    }
}