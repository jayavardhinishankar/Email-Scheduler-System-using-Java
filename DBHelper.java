import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all database operations for the Email Scheduler application using MySQL.
 * * IMPORTANT: You must have the MySQL JDBC Connector JAR in your classpath.
 * IMPORTANT: You must ensure the database 'email_scheduler' exists 
 * and the table 'scheduled_mail' is created with appropriate columns (id, name, 
 * email, message, send_date (DATE), send_time (TIME), status).
 */
public class DBHelper {
    private Connection conn;
    
    // NOTE: Replace credentials if needed.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/email_scheduler?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";       // your MySQL username
    private static final String DB_PASS = "Jaya@123";   // your MySQL password


    public DBHelper() {
        try {
            // Ensure the JDBC driver is loaded (optional for modern JDBC, but safe)
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Establish the connection
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            System.out.println("Successfully connected to MySQL database.");
            
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found. Please add the mysql-connector-java JAR to your classpath.");
            throw new RuntimeException("Database initialization failed.", e);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to connect to MySQL database.", e);
        }
    }

    // Insert email
    public void insertMail(String name, String email, String message, LocalDateTime sendDateTime) throws SQLException {
        String sql = "INSERT INTO scheduled_mail(name,email,message,send_date,send_time,status) VALUES(?,?,?,?,?,?)";
        
        // Use try-with-resources to ensure PreparedStatement is closed
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, message);
            // Convert LocalDateTime parts to java.sql.Date and java.sql.Time
            ps.setDate(4, java.sql.Date.valueOf(sendDateTime.toLocalDate()));
            ps.setTime(5, java.sql.Time.valueOf(sendDateTime.toLocalTime()));
            ps.setString(6, "PENDING"); // Set default status on insert
            ps.executeUpdate();
            System.out.println("New mail scheduled successfully.");
        }
    }

    /**
     * Fetches all PENDING emails that are due to be sent (current date/time or earlier).
     * Since send_date and send_time are separate, we combine them for the check.
     * Note: This simple query assumes the system clock is in the same timezone as the DB.
     */
    public List<MailRecord> getPendingMails() throws SQLException {
        List<MailRecord> list = new ArrayList<>();
        
        // Combined SQL check for pending emails that are now due or overdue
        String sql = "SELECT id, name, email, message, send_date, send_time, status FROM scheduled_mail WHERE status='PENDING' AND CONCAT(send_date, ' ', send_time) <= NOW() ORDER BY send_date ASC, send_time ASC";
        
        // Use try-with-resources to ensure Statement and ResultSet are closed
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            // Check if the 'email' column exists, although SQL exceptions usually cover this.
            // If the application continues to fail, the column name in MySQL is the issue.
            
            while (rs.next()) {
                LocalDateTime sendDateTime = LocalDateTime.of(
                    rs.getDate("send_date").toLocalDate(),
                    rs.getTime("send_time").toLocalTime()
                );

                // IMPORTANT: Ensure the column name 'email' in your MySQL table matches EXACTLY!
                String recipientEmail = rs.getString("email");
                
                if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                    System.err.println("WARNING: Email ID " + rs.getInt("id") + " retrieved from DB has a NULL or empty email address. Skipping send attempt.");
                    // Skip adding this record to the list if the email is invalid
                    continue; 
                }

                list.add(new MailRecord(
                    rs.getInt("id"),
                    rs.getString("name"),
                    recipientEmail, // Use the checked recipient email
                    rs.getString("message"),
                    sendDateTime,
                    rs.getString("status")
                ));
            }
        }
        return list;
    }

    // Update email status
    public void updateStatus(int id, String status) throws SQLException {
        // FIX: Removed 'sent_at=?' binding since the column doesn't exist in the database.
        String sql = "UPDATE scheduled_mail SET status=? WHERE id=?";
        
        // Use try-with-resources to ensure PreparedStatement is closed
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            // Removed ps.setTimestamp(2, ...) line
            ps.setInt(2, id);
            ps.executeUpdate();
            System.out.println("Mail ID " + id + " status updated to " + status);
        }
    }
    
    // Close the connection when the application shuts down
    public void closeConnection() {
        if (conn != null) {
            try {
                conn.close();
                System.out.println("Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }


    // Inner class for mail record
    public static class MailRecord {
        public int id;
        public String name;
        public String email;
        public String message;
        public LocalDateTime sendTime;
        public String status;

        public MailRecord(int id, String name, String email, String message, LocalDateTime sendTime, String status) {
            this.id = id;
            this.name = name;
            this.email = email; // Assigned here
            this.message = message;
            this.sendTime = sendTime;
            this.status = status;
        }
    }
}