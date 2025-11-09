# Email-Scheduler-System-using-Java
The Email Scheduler System uses a desktop UI to schedule emails. The MainApp stores details via DBHelper in MySQL. A background Scheduler service concurrently checks the database every 60 seconds for due tasks. It uses EmailSender to send the mail and updates its status (SENT/FAILED)
