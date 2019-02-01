package com.zjgsu.physicalflower;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.http.HttpSession;

import org.json.JSONObject;

public class WxHandler {

	protected JSONObject Req;
	protected PrintWriter out;
	protected String pf;
	protected String tag;
	protected HttpSession session;
	protected SQLManager sqlmgr;
	protected HashMap<Object, Object> res;
	protected String appId = "wx9061828744e52511";
	protected String appSecret = "f8edd6c9131964c83646a2056eba3700";

	public WxHandler(JSONObject Req, PrintWriter out, HttpSession session) throws IOException {
		this.Req = Req;
		this.out = out;
		this.session = session;
		this.res = new HashMap<>();

		this.pf = this.Req.getString("pf");
		this.tag = this.Req.getString("tag");
		this.res.put("pf", this.pf);
		this.res.put("tag", this.tag);
	}


	/**
	 *@author Mizuki
	 *返回状态码和openid
	 *@param code    登录时获取的 code
	 *@param grant_type 授权类型，此处只需填写 authorization_code
	 */
	public void userLogin() {
		String js_code = this.Req.getString("code");
		String grant_type = this.Req.getString("grant_type");
//    	调用wechat api进行验证登录
		String url = "https://api.weixin.qq.com/sns/jscode2session";
		String param = "appid=" + this.appId + "&secret=" + this.appSecret + "&js_code=" + js_code + "&grant_type=" + grant_type;
		JSONObject wx_msg = new JSONObject(HttpRequest.Get(url, param));

		this.res.put("wx_msg", wx_msg);

		if (wx_msg.has("openid")) {
//    		返回SESSIONID
			this.res.put("cookie", "JSESSIONID=" + this.session.getId());
			String openid = wx_msg.getString("openid");
			this.session.setAttribute("openid", openid);

//    		检索数据库，若没有注册信息则要求注册
			this.sqlmgr = new SQLManager();
			String sql = "Select * FROM pf_user WHERE openid=?";
			this.sqlmgr.prepare(sql);
			try {
				sqlmgr.preparedStmt.setString(1, openid);

				ResultSet rs = sqlmgr.preparedStmt.executeQuery();
				if (rs.next()) {
					HashMap<Object, Object> userBasicInfo = new HashMap<>();
					userBasicInfo.put("openid", openid);
					userBasicInfo.put("telphone", rs.getString("telphone"));
					userBasicInfo.put("name", rs.getString("name"));
					userBasicInfo.put("stunum", rs.getString("stunum"));
					userBasicInfo.put("country", rs.getString("country"));
					userBasicInfo.put("province", rs.getString("province"));
					userBasicInfo.put("city", rs.getString("city"));
					this.res.put("userBasicInfo", userBasicInfo);
					this.res.put("errcode", 0);
					this.res.put("msg", "user information is supplemented");

//					写入session以保持登录状态
//					this.session.setAttribute("userName", rs.getString("userName"));
//					this.session.setAttribute("telephoneNumber", rs.getString("telephoneNumber"));

				} else {
					this.res.put("errCode", 1);
					this.res.put("msg", "User information needs to be supplemented");
				}

			} catch (SQLException e) {
				e.printStackTrace();
			}

		} else {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login failed.");
		}

		this.out.println(new JSONObject(this.res).toString(2));

	}

/**
 * @author Mizuki
 * 完善用户信息，如果之前完善过，就返回用户信息，否则在数据库中插入一条新的数据
 */
	public void updateInfo() throws IllegalArgumentException {
		if (this.session.getAttribute("openid") == null) {
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		}else {
		this.sqlmgr = new SQLManager();
		String sql = "SELECT * FROM pf_user WHERE status=? AND openid=?;";
		this.sqlmgr.prepare(sql);

		try {
			this.sqlmgr.preparedStmt.setInt(1, 1);
			this.sqlmgr.preparedStmt.setString(2, this.session.getAttribute("openid").toString());
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			if (rs.next()) {

//		    	更新原有记录
				this.sqlmgr = new SQLManager();
				sql = "UPDATE pf_user SET name=?, telphone=?, stunum=?, country=?, province=?, city=? WHERE openid=?";
				this.sqlmgr.prepare(sql);
				this.sqlmgr.preparedStmt.setString(1, this.Req.getJSONObject("userBasicInfo").getString("name"));
				this.sqlmgr.preparedStmt.setString(2,this.Req.getJSONObject("userBasicInfo").getString("telphone"));
				this.sqlmgr.preparedStmt.setString(3,this.Req.getJSONObject("userBasicInfo").getString("stunum"));
				this.sqlmgr.preparedStmt.setString(4,this.Req.getJSONObject("userBasicInfo").getString("country"));
				this.sqlmgr.preparedStmt.setString(5,this.Req.getJSONObject("userBasicInfo").getString("province"));
				this.sqlmgr.preparedStmt.setString(6,this.Req.getJSONObject("userBasicInfo").getString("city"));
				this.sqlmgr.preparedStmt.setString(7, this.session.getAttribute("openid").toString());
				this.sqlmgr.preparedStmt.execute();

				this.res.put("userBasicInfo", this.Req.getJSONObject("userBasicInfo"));
				this.res.put("errCode", 0);
				this.res.put("msg", "update success");
			} else {
//				插入一条记录
				this.sqlmgr = new SQLManager();
				long totalMilliSeconds = System.currentTimeMillis();
		        long totalSeconds = totalMilliSeconds / 1000;
				sql = "INSERT INTO pf_user (openid,name,telphone,status,country,province,city,nickName,gmtCreate,gender,avatarUrl,stunum) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
				this.sqlmgr.prepare(sql);
				this.sqlmgr.preparedStmt.setString(1, this.session.getAttribute("openid").toString());
				this.sqlmgr.preparedStmt.setString(2, this.Req.getJSONObject("userBasicInfo").getString("name"));
				this.sqlmgr.preparedStmt.setString(3,this.Req.getJSONObject("userBasicInfo").getString("telphone"));
				this.sqlmgr.preparedStmt.setInt(4, 1);
				this.sqlmgr.preparedStmt.setString(5, this.Req.getJSONObject("userBasicInfo").getString("country"));
				this.sqlmgr.preparedStmt.setString(6, this.Req.getJSONObject("userBasicInfo").getString("province"));
				this.sqlmgr.preparedStmt.setString(7, this.Req.getJSONObject("userBasicInfo").getString("city"));
				this.sqlmgr.preparedStmt.setString(8, this.Req.getJSONObject("userBasicInfo").getString("nickName"));
				this.sqlmgr.preparedStmt.setLong(9, totalSeconds);
				this.sqlmgr.preparedStmt.setInt(10, this.Req.getJSONObject("userBasicInfo").getInt("gender"));
				this.sqlmgr.preparedStmt.setString(11, this.Req.getJSONObject("userBasicInfo").getString("avatarUrl"));
				this.sqlmgr.preparedStmt.setString(12, this.Req.getJSONObject("userBasicInfo").getString("stunum"));
				this.sqlmgr.preparedStmt.execute();

				this.res.put("userBasicInfo", this.Req.getJSONObject("userBasicInfo"));
				this.res.put("errCode", 1);
				this.res.put("msg", "insert success!");
			}

		} catch (SQLException e) {
			e.printStackTrace();
		}
		this.out.println(new JSONObject(this.res).toString(2));
	}
	}

	/**
	 * @author Mizuki
	 * 从班级中删除某个学生
	 */
	public void delUser() {
		String openid = this.session.getAttribute("openid").toString();
		int idCourse = this.Req.getInt("idCourse");
		
		String sql =  "select idUser from pf_user where openid = ?;";
		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setString(1, openid);
			ResultSet rs = sqlmgr.preparedStmt.executeQuery();
			
			int idUser = rs.getInt("idUser");
			sql = "update pf_courseAdd set status = ? where idUser = ?;";
			this.sqlmgr.preparedStmt.setInt(1, 0);
			this.sqlmgr.preparedStmt.setInt(2,idUser);
			this.sqlmgr.preparedStmt.execute();
			this.res.put("errcode", 0);
			this.res.put("msg", "delete success!");
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}
		
		this.out.print(new JSONObject(this.res).toString(2));
	}
}
