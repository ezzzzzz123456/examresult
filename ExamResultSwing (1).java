import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class ExamResultSwing extends JFrame {

    static final String SERVER_URL = "jdbc:mysql://localhost:3306/";
    static final String DB_NAME    = "exam_db";
    static final String USER       = "root";
    static final String PASSWORD   = "";

    static final int PASS_MARK = 40;

    JTextField rollField    = new JTextField(10);
    JTextField nameField    = new JTextField(15);
    JTextField mathsField   = new JTextField(5);
    JTextField scienceField = new JTextField(5);
    JTextField englishField = new JTextField(5);

    DefaultTableModel tableModel = new DefaultTableModel(
        new String[]{"Roll No", "Name", "Maths", "Science", "English", "Total", "Result"}, 0) {
        public boolean isCellEditable(int row, int col) { return false; }
    };
    JTable table = new JTable(tableModel);

    JLabel statusLabel = new JLabel("Ready");

    public ExamResultSwing() {
        super("Exam Result System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(750, 500);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(2, 5, 8, 8));
        form.setBorder(BorderFactory.createTitledBorder("Student details and marks (0-100)"));
        form.add(new JLabel("Roll No"));
        form.add(new JLabel("Name"));
        form.add(new JLabel("Maths"));
        form.add(new JLabel("Science"));
        form.add(new JLabel("English"));
        form.add(rollField);
        form.add(nameField);
        form.add(mathsField);
        form.add(scienceField);
        form.add(englishField);

        JButton saveBtn   = new JButton("Save Student");
        JButton viewBtn   = new JButton("View Result (by Roll No)");
        JButton passedBtn = new JButton("List Passed");
        JButton failedBtn = new JButton("List Failed");
        JButton allBtn    = new JButton("Show All");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(saveBtn);
        buttons.add(viewBtn);
        buttons.add(passedBtn);
        buttons.add(failedBtn);
        buttons.add(allBtn);

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.NORTH);
        top.add(buttons, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        saveBtn.addActionListener(e -> saveStudent());
        viewBtn.addActionListener(e -> viewOne());
        passedBtn.addActionListener(e -> loadTable("PASS", -1));
        failedBtn.addActionListener(e -> loadTable("FAIL", -1));
        allBtn.addActionListener(e -> loadTable("ALL", -1));
    }

    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(SERVER_URL + DB_NAME, USER, PASSWORD);
    }

    static void setupDatabase() throws SQLException {
        try (Connection con = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
             Statement st = con.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);
        }
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS students (" +
                "  roll_no BIGINT PRIMARY KEY," +
                "  name VARCHAR(50) NOT NULL)");
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS marks (" +
                "  id INT AUTO_INCREMENT PRIMARY KEY," +
                "  roll_no BIGINT NOT NULL," +
                "  subject VARCHAR(30) NOT NULL," +
                "  marks INT NOT NULL," +
                "  FOREIGN KEY (roll_no) REFERENCES students(roll_no) ON DELETE CASCADE)");
        }
    }

    void saveStudent() {
        long roll;
        int maths, science, english;
        String name = nameField.getText().trim();

        try {
            roll    = Long.parseLong(rollField.getText().trim());
            maths   = Integer.parseInt(mathsField.getText().trim());
            science = Integer.parseInt(scienceField.getText().trim());
            english = Integer.parseInt(englishField.getText().trim());
        } catch (NumberFormatException ex) {
            showError("Roll number and marks must be whole numbers.");
            return;
        }
        if (name.isEmpty()) {
            showError("Please enter the student's name.");
            return;
        }
        if (!validMark(maths) || !validMark(science) || !validMark(english)) {
            showError("Marks must be between 0 and 100.");
            return;
        }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                PreparedStatement ps1 = con.prepareStatement(
                    "INSERT INTO students (roll_no, name) VALUES (?, ?)");
                ps1.setLong(1, roll);
                ps1.setString(2, name);
                ps1.executeUpdate();

                PreparedStatement ps2 = con.prepareStatement(
                    "INSERT INTO marks (roll_no, subject, marks) VALUES (?, ?, ?)");
                String[] subjects = {"Maths", "Science", "English"};
                int[] marks = {maths, science, english};
                for (int i = 0; i < subjects.length; i++) {
                    ps2.setLong(1, roll);
                    ps2.setString(2, subjects[i]);
                    ps2.setInt(3, marks[i]);
                    ps2.executeUpdate();
                }
                con.commit();
                statusLabel.setText("Saved student " + name + " (roll " + roll + ")");
                clearForm();
                loadTable("ALL", -1);
            } catch (SQLIntegrityConstraintViolationException ex) {
                con.rollback();
                showError("A student with roll number " + roll + " already exists.");
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            showError("Database error: " + ex.getMessage());
        }
    }

    void viewOne() {
        try {
            long roll = Long.parseLong(rollField.getText().trim());
            loadTable("ONE", roll);
        } catch (NumberFormatException ex) {
            showError("Type the roll number in the Roll No box first.");
        }
    }

    void loadTable(String mode, long roll) {
        String sql =
            "SELECT s.roll_no, s.name, " +
            "  MAX(CASE WHEN m.subject = 'Maths'   THEN m.marks END) AS maths, " +
            "  MAX(CASE WHEN m.subject = 'Science' THEN m.marks END) AS science, " +
            "  MAX(CASE WHEN m.subject = 'English' THEN m.marks END) AS english, " +
            "  SUM(m.marks) AS total, MIN(m.marks) AS lowest " +
            "FROM students s JOIN marks m ON s.roll_no = m.roll_no ";
        if (mode.equals("ONE")) sql += "WHERE s.roll_no = ? ";
        sql += "GROUP BY s.roll_no, s.name ";
        if (mode.equals("PASS")) sql += "HAVING MIN(m.marks) >= " + PASS_MARK + " ";
        if (mode.equals("FAIL")) sql += "HAVING MIN(m.marks) < "  + PASS_MARK + " ";
        sql += "ORDER BY s.roll_no";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (mode.equals("ONE")) ps.setLong(1, roll);
            ResultSet rs = ps.executeQuery();

            tableModel.setRowCount(0);
            while (rs.next()) {
                String result = rs.getInt("lowest") >= PASS_MARK ? "PASS" : "FAIL";
                tableModel.addRow(new Object[]{
                    rs.getLong("roll_no"), rs.getString("name"),
                    rs.getInt("maths"), rs.getInt("science"), rs.getInt("english"),
                    rs.getInt("total"), result
                });
            }
            statusLabel.setText(tableModel.getRowCount() + " record(s) shown");
            if (tableModel.getRowCount() == 0) {
                statusLabel.setText("No records found");
            }
        } catch (SQLException ex) {
            showError("Database error: " + ex.getMessage());
        }
    }

    boolean validMark(int m) { return m >= 0 && m <= 100; }

    void clearForm() {
        rollField.setText("");
        nameField.setText("");
        mathsField.setText("");
        scienceField.setText("");
        englishField.setText("");
    }

    void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        statusLabel.setText(msg);
    }

    public static void main(String[] args) {
        try {
            setupDatabase();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null,
                "Could not connect to MySQL:\n" + e.getMessage() +
                "\n\nCheck that MySQL is running and USER / PASSWORD are correct.",
                "Connection failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> new ExamResultSwing().setVisible(true));
    }
}
