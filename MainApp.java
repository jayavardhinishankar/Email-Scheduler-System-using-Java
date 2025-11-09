import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainApp {

    private DBHelper dbHelper;
    private Scheduler scheduler;
    private EmailSender emailSender;

    public static void main(String[] args) {
        // Use a shutdown hook to close resources when the JVM exits
        MainApp app = new MainApp();
        Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        SwingUtilities.invokeLater(app::init);
    }

    private void init() {
        try {
            // 1. Initialize Database and Email services
            dbHelper = new DBHelper(); // Connects to MySQL
            emailSender = new EmailSender(
                "smtp.gmail.com",
                587,
                "yamicavr@gmail.com",       // Your Gmail ID
                "quayfdvhisabslfe",     // App Password
                true
            );
            
            // 2. Initialize Scheduler
            scheduler = new Scheduler(emailSender, dbHelper);
            // The scheduler.start() call now handles the initial check immediately (delay=0)
            scheduler.start(); 

            // 3. Create the UI
            createUI();
            
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Initialization error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            // If init fails, exit the app
            System.exit(1); 
        }
    }
    
    private void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown(); // Gracefully stops the background scheduler
        }
        // DBHelper closes its connection automatically in its shutdown logic
    }

    private void createUI() {
        JFrame frame = new JFrame("Email Scheduler");
        
        // Use BorderLayout for better structure
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10); // Padding

        JTextField nameField = new JTextField();
        JTextField emailField = new JTextField();
        JTextArea messageArea = new JTextArea(5, 20); // 5 rows, 20 columns
        JTextField dateField = new JTextField(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        JTextField timeField = new JTextField(LocalDateTime.now().plusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm")));
        JButton saveButton = new JButton("Save & Schedule");
        
        // Setup the layout (Label, Field pairs)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        panel.add(new JLabel("Recipient Name:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.7;
        panel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        panel.add(new JLabel("Recipient Email:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.7;
        panel.add(emailField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        panel.add(new JLabel("Message Body:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.7; gbc.gridheight = 2;
        panel.add(new JScrollPane(messageArea), gbc);
        gbc.gridheight = 1; // Reset height

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.3;
        panel.add(new JLabel("Date (yyyy-MM-dd):"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 0.7;
        panel.add(dateField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.3;
        panel.add(new JLabel("Time (HH:mm):"), gbc);
        gbc.gridx = 1; gbc.gridy = 5; gbc.weightx = 0.7;
        panel.add(timeField, gbc);
        
        // Button spans two columns
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.weighty = 0.1; 
        panel.add(saveButton, gbc);

        // Styling the button for a modern look
        saveButton.setFont(new Font("Arial", Font.BOLD, 14));
        saveButton.setBackground(new Color(60, 140, 255));
        saveButton.setForeground(Color.WHITE);
        saveButton.setFocusPainted(false);
        saveButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));


        saveButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String message = messageArea.getText().trim();
            String dateStr = dateField.getText().trim();
            String timeStr = timeField.getText().trim();

            if (name.isEmpty() || email.isEmpty() || message.isEmpty() || dateStr.isEmpty() || timeStr.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please fill all fields!", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                LocalDateTime sendTime = LocalDateTime.parse(dateStr + " " + timeStr, formatter);
                
                if (sendTime.isBefore(LocalDateTime.now())) {
                    JOptionPane.showMessageDialog(frame, "Scheduled time cannot be in the past!", "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                dbHelper.insertMail(name, email, message, sendTime);
                
                JOptionPane.showMessageDialog(frame, "Email scheduled successfully! Will be checked by the scheduler every minute.", "Success", JOptionPane.INFORMATION_MESSAGE);
                // Clear fields after successful save
                nameField.setText("");
                emailField.setText("");
                messageArea.setText("");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(), "Input/Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.add(panel);
        frame.pack(); // Adjusts window size to fit components
        frame.setLocationRelativeTo(null); // Center the window
        frame.setVisible(true);
    }
}