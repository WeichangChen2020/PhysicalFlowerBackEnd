package com.zjgsu.physicalflower;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.http.HttpSession;

import org.json.JSONArray;
import org.json.JSONObject;

public class DpHandler {

	protected JSONObject Req;
	protected PrintWriter out;
	protected String pf;
	protected String tag;
	protected HttpSession session;
	protected SQLManager sqlmgr;
	protected HashMap<Object,Object> res;
	
	public DpHandler(JSONObject Req,PrintWriter out, HttpSession session) throws IOException {	
		this.Req = Req;
		this.out = out;
		this.session = session;
		this.res = new HashMap<>();
		
		this.pf = this.Req.getString("pf");
		this.tag = this.Req.getString("tag");
		this.res.put("pf", this.pf);
		this.res.put("tag", this.tag);
	}
	
	
	public void userLogin() {
		String usr = this.Req.getString("usr");
		String psw = this.Req.getString("psw");
		
		String sql = "SELECT * FROM adminInfo WHERE usr=? and psw=? and enable=?";
		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setString(1, usr);
			this.sqlmgr.preparedStmt.setString(2, psw);
			this.sqlmgr.preparedStmt.setBoolean(3, true);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			if (rs.next()) {
				this.session.setAttribute("usr", usr);
//	    		返回SESSIONID
	    		this.res.put("cookies", "JSESSIONID="+this.session.getId());
				
				
				this.res.put("errCode", 0);
				this.res.put("msg", "Login successfully.");
				
			}else {
				this.res.put("errCode", 4001);
				this.res.put("msg", "Login failed");
				
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		this.out.print(new JSONObject(this.res).toString());
	}
	
	
	public void getLocation() {
//    	TODO: Authentication
    	
    	JSONArray location = new JSONArray();
    	
    	this.sqlmgr = new SQLManager();
    	String sql = "SELECT * FROM Location WHERE enable=?";
    	this.sqlmgr.prepare(sql);
    	try {
			this.sqlmgr.preparedStmt.setBoolean(1, true);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			
			while (rs.next()) {
				HashMap<Object, Object> tmp = new HashMap<>();
				tmp.put("idLocation",rs.getInt("idLocation"));
				tmp.put("name", rs.getString("name"));
				tmp.put("lon", rs.getString("longitude"));
				tmp.put("lat",rs.getString("latitude"));
				location.put(tmp);
				
				
			}
			
			this.res.put("location",location);
			this.res.put("errCode", 0);
			this.res.put("msg", "Get location info successfully.");
			
		} catch (SQLException e) {
			this.res.put("errCode", 4001);
			this.res.put("msg", "Get location info failed.");
			e.printStackTrace();
		}
    	
    	this.out.println(new JSONObject(this.res).toString(2));
    }
	
	
	
}
