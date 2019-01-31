package com.zjgsu.physicalflower;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class SQLManager {
	Properties properties = new Properties();
	Connection conn;
	Statement stmt;
	PreparedStatement preparedStmt;

	public SQLManager() {
		properties.setProperty("user", "root");
		properties.setProperty("password", "Zsc.0508");
		properties.setProperty("useSSL", "false");
		properties.setProperty("autoReconnect", "false");
		properties.setProperty("characterEncoding", "utf8");
		properties.setProperty("allowPublicKeyRetrieval", "true");
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			conn = DriverManager.getConnection("jdbc:mysql://zjgsu.geefunlab.com:3306/physicalFlower", properties);
			stmt = conn.createStatement();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * 
	 * @param sql
	 */
	public void prepare(String sql) {
		try {
			this.preparedStmt = this.conn.prepareStatement(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
		}
	}

}
