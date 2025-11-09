import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.sql.SQLException;
import jakarta.mail.MessagingException;

/**
 * The core scheduling component. 
 * Uses a thread pool and a recurring task to periodically check the database 
 * for emails that are due to be sent and executes the send operations concurrently.
 */
public class Scheduler {

    private final EmailSender emailSender;
    private final DBHelper dbHelper;
    
    // Use a thread pool of 5 to allow for concurrent email sending.
    private final ScheduledExecutorService scheduler;
    
    // Check for pending emails every 60 seconds
    private static final int CHECK_INTERVAL_SECONDS = 60; 

    public Scheduler(EmailSender emailSender, DBHelper dbHelper) {
        this.emailSender = emailSender;
        this.dbHelper = dbHelper;
        // Initialize the scheduled thread pool with 5 workers
        this.scheduler = Executors.newScheduledThreadPool(5);
    }

    /**
     * Starts the recurring task to check the database for due emails.
     * This task ensures that any due email (even if the application restarted) is processed.
     */
    public void start() {
        // Schedule the database check task to run repeatedly
        scheduler.scheduleAtFixedRate(this::checkAndExecuteDueEmails, 
                                      0, // initial delay (start immediately)
                                      CHECK_INTERVAL_SECONDS, 
                                      TimeUnit.SECONDS);
        System.out.println("Email Scheduler started. Checking database every " + CHECK_INTERVAL_SECONDS + " seconds.");
    }
    
    /**
     * The main logic: fetches pending and due emails, then submits them to the 
     * thread pool for concurrent sending.
     */
    private void checkAndExecuteDueEmails() {
        try {
            // Retrieve pending emails that are now due (based on the DBHelper query)
            List<DBHelper.MailRecord> tasks = dbHelper.getPendingMails();
            
            if (tasks.isEmpty()) {
                System.out.println("No emails due for sending right now.");
                return;
            }
            
            System.out.println("Found " + tasks.size() + " email(s) due for sending. Submitting to pool...");
            
            for (DBHelper.MailRecord task : tasks) {
                // Submit the sending logic to the thread pool for immediate, asynchronous execution
                // This prevents the main checker thread from blocking while waiting for SMTP response.
                scheduler.execute(() -> sendEmailTask(task));
            }
        } catch (SQLException e) {
            System.err.println("Database error during email check: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Runnable task to handle the actual email sending and status update.
     * This runs inside one of the scheduler's pool threads.
     */
    private void sendEmailTask(DBHelper.MailRecord record) {
        try {
            String subject = "Scheduled Message for " + record.name;
            System.out.println("Sending email ID " + record.id + " to " + record.email + "...");
            
            emailSender.send(record.email, subject, record.message);
            
            // Success: Update database status to SENT
            dbHelper.updateStatus(record.id, "SENT");
            
        } catch (MessagingException e) {
            System.err.println("CRITICAL: Failed to send email for Task ID " + record.id + " (" + record.email + ").");
            e.printStackTrace();
            
            // Failure: Update status to FAILED
            try {
                dbHelper.updateStatus(record.id, "FAILED");
            } catch (SQLException ex) {
                System.err.println("Failed to update status to FAILED for Task ID " + record.id + ": " + ex.getMessage());
            }
        } catch (SQLException e) {
             System.err.println("Database error during status update for Task ID " + record.id + ": " + e.getMessage());
             e.printStackTrace();
        }
    }
    
    /**
     * Stops the scheduled task execution and shuts down the thread pool.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            // Wait for up to 5 seconds for existing tasks to complete
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow(); // Force shutdown if tasks are stuck
            }
            // Close DB connection on shutdown
            dbHelper.closeConnection();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Email Scheduler stopped.");
    }
}