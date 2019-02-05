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
	 * @author Mizuki 返回状态码和openid
	 * @param code       登录时获取的 code
	 * @param grant_type 授权类型，此处只需填写 authorization_code
	 */
	public void userLogin() {
		String js_code = this.Req.getString("code");
		String grant_type = this.Req.getString("grant_type");
//    	调用wechat api进行验证登录
		String url = "https://api.weixin.qq.com/sns/jscode2session";
		String param = "appid=" + this.appId + "&secret=" + this.appSecret + "&js_code=" + js_code + "&grant_type="
				+ grant_type;
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
					userBasicInfo.put("idUser", rs.getInt("idUser"));
					userBasicInfo.put("openid", openid);
					userBasicInfo.put("telphone", rs.getString("telphone"));
					userBasicInfo.put("name", rs.getString("name"));
					userBasicInfo.put("stunum", rs.getString("stunum"));
					userBasicInfo.put("country", rs.getString("country"));
					userBasicInfo.put("province", rs.getString("province"));
					userBasicInfo.put("city", rs.getString("city"));
					this.res.put("userBasicInfo", userBasicInfo);
					this.res.put("errCode", 0);
					this.res.put("msg", "user information is supplemented");

//					写入session以保持登录状态
					this.session.setAttribute("idUser", rs.getString("idUser"));
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
	 * @author Mizuki 完善用户信息，如果之前完善过，就返回用户信息，否则在数据库中插入一条新的数据
	 */
	public void updateInfo() throws IllegalArgumentException {
		if (this.session.getAttribute("openid") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
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
					this.sqlmgr.preparedStmt.setString(2,
							this.Req.getJSONObject("userBasicInfo").getString("telphone"));
					this.sqlmgr.preparedStmt.setString(3, this.Req.getJSONObject("userBasicInfo").getString("stunum"));
					this.sqlmgr.preparedStmt.setString(4, this.Req.getJSONObject("userBasicInfo").getString("country"));
					this.sqlmgr.preparedStmt.setString(5,
							this.Req.getJSONObject("userBasicInfo").getString("province"));
					this.sqlmgr.preparedStmt.setString(6, this.Req.getJSONObject("userBasicInfo").getString("city"));
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
					this.sqlmgr.preparedStmt.setString(3,
							this.Req.getJSONObject("userBasicInfo").getString("telphone"));
					this.sqlmgr.preparedStmt.setInt(4, 1);
					this.sqlmgr.preparedStmt.setString(5, this.Req.getJSONObject("userBasicInfo").getString("country"));
					this.sqlmgr.preparedStmt.setString(6,
							this.Req.getJSONObject("userBasicInfo").getString("province"));
					this.sqlmgr.preparedStmt.setString(7, this.Req.getJSONObject("userBasicInfo").getString("city"));
					this.sqlmgr.preparedStmt.setString(8,
							this.Req.getJSONObject("userBasicInfo").getString("nickName"));
					this.sqlmgr.preparedStmt.setLong(9, totalSeconds);
					this.sqlmgr.preparedStmt.setInt(10, this.Req.getJSONObject("userBasicInfo").getInt("gender"));
					this.sqlmgr.preparedStmt.setString(11,
							this.Req.getJSONObject("userBasicInfo").getString("avatarUrl"));
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
	 * @author Mizuki 从班级中删除某个学生
	 * @param idCourse 课程id
	 * @param idUser   学生id
	 */
	public void delUser() {
		int idUser = this.Req.getInt("idUser");
		int idCourse = this.Req.getInt("idCourse");

		String sql = "update pf_courseAdd set status = ? where idUser = ? and idCourse = ?;";
		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, 0);
			this.sqlmgr.preparedStmt.setInt(2, idUser);
			this.sqlmgr.preparedStmt.setInt(3, idCourse);
			this.sqlmgr.preparedStmt.execute();
			this.res.put("errCode", 0);
			this.res.put("msg", "delete success!");
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}

		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 将某个学生添加到某个课程中
	 * @param idCourse 课程id
	 */
	public void addCourse() {
		if (this.session.getAttribute("openid") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int idCourse = this.Req.getInt("idCourse");
			int idUser = Integer.parseInt(this.session.getAttribute("idUser").toString());
			long totalMilliSeconds = System.currentTimeMillis();
			long totalSeconds = totalMilliSeconds / 1000;

			String sql = "select courseName from pf_course where idCourse = ?;";
			this.sqlmgr = new SQLManager();
			this.sqlmgr.prepare(sql);
			try {
				this.sqlmgr.preparedStmt.setInt(1, idCourse);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				if (rs.next()) {
					sql = "select status from pf_courseAdd where idCourse = ? and idUser = ?;";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setInt(1, idCourse);
					this.sqlmgr.preparedStmt.setInt(2, idUser);
					rs = this.sqlmgr.preparedStmt.executeQuery();
					if (rs.next()) {
						if (rs.getInt("status") == 0) {
							sql = "update pf_courseAdd set status = ?, gmtModify = ? where idCourse = ? and idUser = ?;";
							this.sqlmgr.prepare(sql);
							this.sqlmgr.preparedStmt.setInt(1, 1);
							this.sqlmgr.preparedStmt.setLong(2, totalSeconds);
							this.sqlmgr.preparedStmt.setInt(3, idCourse);
							this.sqlmgr.preparedStmt.setInt(4, idUser);

							this.sqlmgr.preparedStmt.execute();
							this.res.put("errCode", 0);
							this.res.put("msg", "add(update) success!");
						} else {
							this.res.put("errCode", 4001);
							this.res.put("msg", "user has already exit!");
						}
					} else {
						sql = "insert into pf_courseAdd (idUser, idCourse, status, gmtCreate) values(?, ?, ?, ?);";
						this.sqlmgr.prepare(sql);
						this.sqlmgr.preparedStmt.setInt(1, idUser);
						this.sqlmgr.preparedStmt.setInt(2, idCourse);
						this.sqlmgr.preparedStmt.setInt(3, 1);
						this.sqlmgr.preparedStmt.setLong(4, totalSeconds);

						this.sqlmgr.preparedStmt.execute();
						this.res.put("errCode", 1);
						this.res.put("msg", "add(insert) success!");
					}

				} else {
					this.res.put("errCode", 4003);
					this.res.put("msg", "Course create required!");
				}
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}
	}

	/**
	 * @author Mizuki
	 * @param questionSet
	 * @param courseName
	 */
	public void courseCreate() {
		String courseName = this.Req.getString("courseName");
		int idCreater = Integer.parseInt(this.session.getAttribute("idUser").toString());
		int questionSet = this.Req.getInt("questionSet");
		long totalMilliSeconds = System.currentTimeMillis();
		long totalSeconds = totalMilliSeconds / 1000;

		String sql = "select * from pf_course where courseName = ?;";
		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setString(1, courseName);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			if (rs.next()) {
				if (rs.getInt("status") == 1) {
					this.res.put("errCode", 4002);
					this.res.put("msg", "Sorry,the course has already exists!");
				} else {
					sql = "update pf_course set status = ?, gmtModify = ?, idCreater = ? where courseName = ?;";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setInt(1, 1);
					this.sqlmgr.preparedStmt.setLong(2, totalSeconds);
					this.sqlmgr.preparedStmt.setInt(3, idCreater);
					this.sqlmgr.preparedStmt.setString(4, courseName);
					this.sqlmgr.preparedStmt.execute();

					this.res.put("errCode", 0);
					this.res.put("msg", "create(update) success!");
				}

			} else {
				sql = "insert into pf_course (idCreater, courseName, questionSet, status, gmtCreate) values(?, ?, ?, ?, ?);";
				this.sqlmgr.prepare(sql);
				this.sqlmgr.preparedStmt.setInt(1, idCreater);
				this.sqlmgr.preparedStmt.setString(2, courseName);
				this.sqlmgr.preparedStmt.setInt(3, questionSet);
				this.sqlmgr.preparedStmt.setInt(4, 1);
				this.sqlmgr.preparedStmt.setLong(5, totalSeconds);
				this.sqlmgr.preparedStmt.execute();

				this.res.put("errCode", 1);
				this.res.put("msg", "create(insert) success!");
			}
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki
	 * @param question 问题描述
	 * @param picture  问题截图的base64编码
	 */
	public void queReport() {
		if (this.session.getAttribute("openid") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			String question = this.Req.getString("question");
			String picture0 = this.Req.getString("picture0");
			String picture1 = this.Req.getString("picture1");
			String picture2 = this.Req.getString("picture2");

			String openid = this.session.getAttribute("openid").toString();

			String sql = "select idUser from pf_user where openid = ?;";
			this.sqlmgr = new SQLManager();
			this.sqlmgr.prepare(sql);
			try {
				long totalMilliSeconds = System.currentTimeMillis();
				long totalSeconds = totalMilliSeconds / 1000;

				this.sqlmgr.prepare(sql);
				this.sqlmgr.preparedStmt.setString(1, openid);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				if (rs.next()) {
					int idUser = rs.getInt("idUser");

					sql = "insert into pf_queReport (idUser, question, picture0, picture1, picture2, gmtCreate) values (?, ?, ?, ?, ?, ?);";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setInt(1, idUser);
					this.sqlmgr.preparedStmt.setString(2, question);
					this.sqlmgr.preparedStmt.setString(3, picture0);
					this.sqlmgr.preparedStmt.setString(4, picture1);
					this.sqlmgr.preparedStmt.setString(5, picture2);
					this.sqlmgr.preparedStmt.setLong(6, totalSeconds);
					this.sqlmgr.preparedStmt.execute();

					this.res.put("errCode", 0);
					this.res.put("msg", "report success!");
				}
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}
	}

}
