package hospital.system;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class connection implements AutoCloseable {

    public Connection connection;
    public Statement statement;

    public connection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/hospital_system",
                    "root",
                    "@Saloni04"
            );

            statement = connection.createStatement();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }
}
