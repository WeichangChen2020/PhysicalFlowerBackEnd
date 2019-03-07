package com.zjgsu.physicalflower;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
	String imgUrl = "http://dataplatform-physicalflower.stor.sinaapp.com/physcialflower_questionbank/";

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
//			改为如果信息不完整则要求完善
			this.sqlmgr = new SQLManager();
			String sql = "select * from pf_user where openid = ?;";
			this.sqlmgr.prepare(sql);
			try {
				this.sqlmgr.preparedStmt.setString(1, openid);

				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				if (rs.next()) {
					if (rs.getString("name") != null) {
						// 用户信息已完善，返回给前台
						HashMap<Object, Object> userBasicInfo = new HashMap<>();
						userBasicInfo.put("idUser", rs.getInt("idUser"));
						userBasicInfo.put("openid", rs.getString("openid"));
						userBasicInfo.put("name", rs.getString("name"));
						userBasicInfo.put("nickName", rs.getString("nickName"));
						userBasicInfo.put("gender", rs.getInt("gender"));
						userBasicInfo.put("country", rs.getString("country"));
						userBasicInfo.put("province", rs.getString("province"));
						userBasicInfo.put("city", rs.getString("city"));
						userBasicInfo.put("avatarUrl", rs.getString("avatarUrl"));
						userBasicInfo.put("telphone", rs.getString("telphone"));
						userBasicInfo.put("stunum", rs.getString("stunum"));

						this.res.put("userBasicInfo", userBasicInfo);
						this.res.put("errCode", 0);
						this.res.put("msg", "user information is supplement!");
						this.session.setAttribute("idUser", rs.getInt("idUser"));
					} else {
//						 用户openid已写入数据库，但是信息未完善
						this.res.put("errCode", 1);
						this.res.put("msg", "user information is need to supplement!");
						this.session.setAttribute("idUser", rs.getInt("idUser"));
						this.res.put("idUser", rs.getInt("idUser"));
					}

				} else {
//					 openid还未写入数据库
					String Sql = "insert into pf_user (openid, gmtCreate) values (?, ?);";
					this.sqlmgr.prepare(Sql);
					this.sqlmgr.preparedStmt = this.sqlmgr.conn.prepareStatement(Sql,
							this.sqlmgr.stmt.RETURN_GENERATED_KEYS);
					this.sqlmgr.preparedStmt.setString(1, openid);
					this.sqlmgr.preparedStmt.setLong(2, System.currentTimeMillis() / 1000);

					this.sqlmgr.preparedStmt.executeUpdate();
					ResultSet Rs = this.sqlmgr.preparedStmt.getGeneratedKeys();
					if (Rs.next()) {
						this.res.put("idUser", Rs.getInt(1));
						this.session.setAttribute("idUser", Rs.getInt(1));
					}
					this.res.put("errCode", 0);
					this.res.put("msg", "login(insert) success!");
				}
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}
		} else {
			// 微信登陆失败
			this.res.put("errCode", 4002);
			this.res.put("msg", "login failed!");
		}
		this.out.println(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 完善用户信息，如果之前完善过，就返回用户信息，否则在数据库中插入一条新的数据
	 */
	public void updateInfo() throws IllegalArgumentException {
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int idUser = Integer.parseInt(this.session.getAttribute("idUser").toString());
			this.sqlmgr = new SQLManager();
			String sql = "SELECT * FROM pf_user WHERE idUser=?;";
			this.sqlmgr.prepare(sql);

			try {
				this.sqlmgr.preparedStmt.setInt(1, idUser);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				if (rs.next()) {

//		    	更新原有记录(status置1)
					this.sqlmgr = new SQLManager();
					sql = "UPDATE pf_user SET nickName=?, avatarUrl=?, name=?, telphone=?, stunum=?, country=?, province=?, city=?, status=1, gmtModify=?, gender = ? WHERE openid=?";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setString(1,
							this.Req.getJSONObject("userBasicInfo").getString("nickName"));
					this.sqlmgr.preparedStmt.setString(2,
							this.Req.getJSONObject("userBasicInfo").getString("avatarUrl"));
					this.sqlmgr.preparedStmt.setString(3, this.Req.getJSONObject("userBasicInfo").getString("name"));
					this.sqlmgr.preparedStmt.setString(4,
							this.Req.getJSONObject("userBasicInfo").getString("telphone"));
					this.sqlmgr.preparedStmt.setString(5, this.Req.getJSONObject("userBasicInfo").getString("stunum"));
					this.sqlmgr.preparedStmt.setString(6, this.Req.getJSONObject("userBasicInfo").getString("country"));
					this.sqlmgr.preparedStmt.setString(7,
							this.Req.getJSONObject("userBasicInfo").getString("province"));
					this.sqlmgr.preparedStmt.setString(8, this.Req.getJSONObject("userBasicInfo").getString("city"));
					this.sqlmgr.preparedStmt.setLong(9, System.currentTimeMillis() / 1000);
					this.sqlmgr.preparedStmt.setInt(10, this.Req.getJSONObject("userBasicInfo").getInt("gender"));
					this.sqlmgr.preparedStmt.setString(11, this.session.getAttribute("openid").toString());
					this.sqlmgr.preparedStmt.execute();

					this.Req.getJSONObject("userBasicInfo").put("idUser", idUser);
					this.res.put("userBasicInfo", this.Req.getJSONObject("userBasicInfo"));
					this.res.put("errCode", 0);
					this.res.put("msg", "msg success!");
				}

			} catch (SQLException e) {
				e.printStackTrace();
				this.res.put("msg", e.toString());
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
			this.res.put("msg", e.toString());
		}

		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 将某个学生添加到某个课程中
	 * @param idCourse 课程id
	 */
	public void addToCourse() {
		if (this.session.getAttribute("idUser") == null) {
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
					sql = "select * from pf_courseAdd where idCourse = ? and idUser = ?;";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setInt(1, idCourse);
					this.sqlmgr.preparedStmt.setInt(2, idUser);
					rs = this.sqlmgr.preparedStmt.executeQuery();
					if (rs.next()) {
						if (rs.getInt("status") == 0) {
							HashMap<Object, Object> userBasicInfo = new HashMap<>();
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
							HashMap<Object, Object> userBasicInfo = new HashMap<>();
							userBasicInfo.put("idUser", rs.getInt("idUser"));
							userBasicInfo.put("idCourse", rs.getInt("idCourse"));
							userBasicInfo.put("gmtCreate", rs.getLong("gmtCreate"));
							userBasicInfo.put("gmtModify", rs.getLong("gmtModify"));

							this.res.put("userBasicInfo", userBasicInfo);
							this.res.put("errCode", 4001);
							this.res.put("msg", "user has already exit!");
						}
					} else {
//						HashMap<Object, Object> userBasicInfo = new HashMap<>();
						sql = "insert into pf_courseAdd (idUser, idCourse, status, gmtCreate) values(?, ?, ?, ?);";

						this.sqlmgr.prepare(sql);
						this.sqlmgr.preparedStmt.setInt(1, idUser);
						this.sqlmgr.preparedStmt.setInt(2, idCourse);
						this.sqlmgr.preparedStmt.setInt(3, 1);
						this.sqlmgr.preparedStmt.setLong(4, totalSeconds);

						this.sqlmgr.preparedStmt.execute();

						this.res.put("errCode", 0);
						this.res.put("msg", "add(insert) success!");
					}

				} else {
					this.res.put("errCode", 4003);
					this.res.put("msg", "Course create required!");
				}
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
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
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			String logo = this.Req.getString("logo");
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
						HashMap<Object, Object> courseBasicInfo = new HashMap<>();
						courseBasicInfo.put("idCourse", rs.getInt("idCourse"));
						courseBasicInfo.put("idCreater", rs.getInt("idCreater"));
						courseBasicInfo.put("courseName", rs.getString("courseName"));
						courseBasicInfo.put("questionSet", rs.getInt("questionSet"));
						courseBasicInfo.put("gmtCreate", rs.getLong("gmtCreate"));
						courseBasicInfo.put("logo", rs.getString("logo"));

						this.res.put("courseBasicInfo", courseBasicInfo);
						this.res.put("errCode", 4003);
						this.res.put("msg", "Sorry,the course has already exists!");

					} else {
						sql = "update pf_course set status = ?, gmtModify = ?, idCreater = ? where courseName = ?;";
						this.sqlmgr.prepare(sql);
						this.sqlmgr.preparedStmt.setInt(1, 1);
						this.sqlmgr.preparedStmt.setLong(2, totalSeconds);
						this.sqlmgr.preparedStmt.setInt(3, idCreater);
						this.sqlmgr.preparedStmt.setString(4, courseName);
						this.sqlmgr.preparedStmt.execute();
						HashMap<Object, Object> courseBasicInfo = new HashMap<>();
						courseBasicInfo.put("idCourse", rs.getInt("idCourse"));
						courseBasicInfo.put("idCreater", rs.getInt("idCreater"));
						courseBasicInfo.put("courseName", rs.getString("courseName"));
						courseBasicInfo.put("questionSet", rs.getInt("questionSet"));
						courseBasicInfo.put("gmtCreate", rs.getLong("gmtCreate"));

						this.res.put("courseBasicInfo", courseBasicInfo);
						this.res.put("errCode", 0);
						this.res.put("msg", "create(update) success!");
					}

				} else {
					sql = "insert into pf_course (idCreater, courseName, questionSet, status, gmtCreate, logo) values(?, ?, ?, ?, ?, ?);";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setInt(1, idCreater);
					this.sqlmgr.preparedStmt.setString(2, courseName);
					this.sqlmgr.preparedStmt.setInt(3, questionSet);
					this.sqlmgr.preparedStmt.setInt(4, 1);
					this.sqlmgr.preparedStmt.setLong(5, totalSeconds);
					this.sqlmgr.preparedStmt.setString(6, logo);
					this.sqlmgr.preparedStmt.execute();

					sql = "select * from pf_course where courseName = ?;";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setString(1, courseName);
					ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
					if (Rs.next()) {
						HashMap<Object, Object> courseBasicInfo = new HashMap<>();
						courseBasicInfo.put("idCourse", Rs.getInt("idCourse"));
						courseBasicInfo.put("idCreater", Rs.getInt("idCreater"));
						courseBasicInfo.put("courseName", Rs.getString("courseName"));
						courseBasicInfo.put("questionSet", Rs.getInt("questionSet"));
						courseBasicInfo.put("gmtCreate", Rs.getLong("gmtCreate"));
						courseBasicInfo.put("logo", Rs.getString("logo"));

						this.res.put("courseBasicInfo", courseBasicInfo);
						this.res.put("errCode", 0);
						this.res.put("msg", "create(insert) success!");
					}

				}
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				this.res.put("msg", e.toString());
				e.printStackTrace();
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}
	}

	/**
	 * @author Mizuki
	 * @param idCourse
	 */
	public void delCourse() {
		int idCourse = this.Req.getInt("idCourse");
		String sql = "update pf_course set status = ?, gmtModify = ? where idCourse = ?;";
		long totalMilliSeconds = System.currentTimeMillis();
		long totalSeconds = totalMilliSeconds / 1000;

		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, 0);
			this.sqlmgr.preparedStmt.setLong(2, totalSeconds);
			this.sqlmgr.preparedStmt.setInt(3, idCourse);

			this.sqlmgr.preparedStmt.execute();
			this.res.put("errCode", 0);
			this.res.put("msg", "delete course success!");
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			this.res.put("msg", e.toString());
			e.printStackTrace();
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki
	 * @param idCourse
	 */
	public void courseDetail() {
		int idCourse = this.Req.getInt("idCourse");
		String sql = "select * from pf_course where idCourse = ? and status = 1";
		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, idCourse);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			if (rs.next()) {
				HashMap<Object, Object> courseInfo = new HashMap<>();
				String sql1 = "select count(*) from pf_courseAdd where idCourse = ? and status = 1;";
				this.sqlmgr.prepare(sql1);
				this.sqlmgr.preparedStmt.setInt(1, idCourse);
				ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
				if (Rs.next()) {
					courseInfo.put("number", Rs.getInt(1));
				} else {
					courseInfo.put("number", 0);
				}
				courseInfo.put("idCourse", idCourse);
				courseInfo.put("courseName", rs.getString("courseName"));
				courseInfo.put("logo", rs.getString("logo"));
				courseInfo.put("gmtCreate", rs.getLong("gmtCreate"));
//				courseInfo.put("idCreater", rs.getInt("idCreater"));
				String sql2 = "select name from pf_user where idUser = ?;";
				this.sqlmgr.prepare(sql2);
				this.sqlmgr.preparedStmt.setInt(1, rs.getInt("idCreater"));
				ResultSet rS = this.sqlmgr.preparedStmt.executeQuery();
				if (rS.next()) {
					courseInfo.put("CreaterName", rS.getString("name"));
				}
				this.res.put("courseInfo", courseInfo);
				this.res.put("errCode", 0);
				this.res.put("msg", "get coursedetail success!");
			} else {
				this.res.put("errCode", 4003);
				this.res.put("msg", "the course is not exist!");
			}
		} catch (SQLException e) {
			this.res.put("msg", e.toString());
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
		if (this.session.getAttribute("idUser") == null) {
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
				this.res.put("msg", e.toString());
				e.printStackTrace();
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}
	}

	/**
	 * @author Mizuki 创建签到
	 */
	public void signinCreate() {
		String signinName = this.Req.getString("signinName");
		int idCourse = this.Req.getInt("idCourse");
		BigDecimal latitude = this.Req.getBigDecimal("latitude");
		BigDecimal longitude = this.Req.getBigDecimal("longitude");
		int horizontalAccuracy = this.Req.getInt("horizontalAccuracy");
		int radius = this.Req.getInt("radius");
		String description = " ";
		if (this.Req.getString("description") != null) {
			description = this.Req.getString("description");
		}
		long gmtStart = this.Req.getLong("gmtStart");
		long gmtEnd = this.Req.getLong("gmtEnd");
		if (gmtEnd <= gmtStart) {
			this.res.put("errCode", 4003);
			this.res.put("msg", "Sorry, you must reset time!");
		} else {
			long totalMilliSeconds = System.currentTimeMillis();
			long totalSeconds = totalMilliSeconds / 1000;

			String sql = "insert into pf_signin (idCourse, signinName, gmtStart, gmtEnd, longitude, latitude, horizontalAccuracy, radius, description, status, gmtCreate) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
			this.sqlmgr = new SQLManager();
			this.sqlmgr.prepare(sql);
			try {
				this.sqlmgr.preparedStmt = this.sqlmgr.conn.prepareStatement(sql,
						this.sqlmgr.stmt.RETURN_GENERATED_KEYS);
				this.sqlmgr.preparedStmt.setInt(1, idCourse);
				this.sqlmgr.preparedStmt.setString(2, signinName);
				this.sqlmgr.preparedStmt.setLong(3, gmtStart);
				this.sqlmgr.preparedStmt.setLong(4, gmtEnd);
				this.sqlmgr.preparedStmt.setBigDecimal(5, longitude);
				this.sqlmgr.preparedStmt.setBigDecimal(6, latitude);
				this.sqlmgr.preparedStmt.setInt(7, horizontalAccuracy);
				this.sqlmgr.preparedStmt.setInt(8, radius);
				this.sqlmgr.preparedStmt.setString(9, description);
				this.sqlmgr.preparedStmt.setInt(10, 1);
				this.sqlmgr.preparedStmt.setLong(11, totalSeconds);
//			this.sqlmgr.preparedStmt = this.sqlmgr.conn.prepareStatement(sql, this.sqlmgr.stmt.RETURN_GENERATED_KEYS);
//			this.sqlmgr.preparedStmt.execute();
				this.sqlmgr.preparedStmt.executeUpdate();
				ResultSet rs = this.sqlmgr.preparedStmt.getGeneratedKeys();
				if (rs.next()) {
					this.res.put("idSignin", rs.getInt(1));
				}
				this.res.put("errCode", 0);
				this.res.put("msg", "signin create success!");

			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				this.res.put("msg", e.toString());
				e.printStackTrace();
			}
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 获得课程签到列表
	 */

	public void getSigninList() {
		int idCourse = this.Req.getInt("idCourse");
		int page = this.Req.getInt("page");
		int num = (page - 1) * 10;
		String sql = " select   *   from   pf_signin  where idCourse = ? and status = 1 order by gmtCreate desc limit ?,10;";

		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, idCourse);
			this.sqlmgr.preparedStmt.setInt(2, num);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			List<HashMap> signinInfo = new ArrayList<HashMap>();
			while (rs.next()) {
				HashMap<Object, Object> Info = new HashMap<>();
				Info.put("idSignin", rs.getInt("idSignin"));
				Info.put("idCourse", rs.getInt("idCourse"));
				Info.put("signinName", rs.getString("signinName"));
				Info.put("gmtStrat", rs.getLong("gmtStart"));
				Info.put("gmtEnd", rs.getLong("gmtEnd"));
				Info.put("description", rs.getString("description"));
				signinInfo.add(Info);
			}
			this.res.put("signinInfo", signinInfo);
			this.res.put("errCode", 0);
			this.res.put("msg", "select success!");
		} catch (SQLException e) {
			this.res.put("msg", e.toString());
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * 获得创建的课程列表
	 */
	public void getCreatecourselist() {
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int idCreater = Integer.parseInt(this.session.getAttribute("idUser").toString());
			String sql = "select * from pf_course where idCreater = ? and status = 1;";
			this.sqlmgr = new SQLManager();

			this.sqlmgr.prepare(sql);
			List<HashMap> courseInfo = new ArrayList<HashMap>();
			try {
				this.sqlmgr.preparedStmt.setInt(1, idCreater);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				while (rs.next()) {
					HashMap<Object, Object> Info = new HashMap<>();
					Info.put("idCourse", rs.getInt("idCourse"));
					Info.put("courseName", rs.getString("courseName"));
					Info.put("logo", rs.getString("logo"));
					String sql1 = "SELECT * FROM physicalFlower.courseCount where idCourse = ?";
					this.sqlmgr.prepare(sql1);
					this.sqlmgr.preparedStmt.setInt(1, rs.getInt("idCourse"));
					ResultSet result = this.sqlmgr.preparedStmt.executeQuery();
					if (result.next()) {
						Info.put("count", result.getInt("stuCount"));
					}
					courseInfo.add(Info);
				}
				this.res.put("courseInfo", courseInfo);
				this.res.put("errCode", 0);
				this.res.put("msg", "get createCourse success!");
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}
	}

	/**
	 * 获得加入的课程列表
	 */
	public void getJoincourselist() {
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int idUser = Integer.parseInt(this.session.getAttribute("idUser").toString());
			this.sqlmgr = new SQLManager();
			String sql = "select * from pf_courseAdd where idUser = ? and status = 1;";

			this.sqlmgr.prepare(sql);
			List<HashMap> courseInfo = new ArrayList<HashMap>();
			try {
				this.sqlmgr.preparedStmt.setInt(1, idUser);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				while (rs.next()) {
					HashMap<Object, Object> Info = new HashMap<>();
					int idCourse = rs.getInt("idCourse");
					String sql1 = "select * from pf_course where idCourse = ? and status = 1;";
					this.sqlmgr.prepare(sql1);
					this.sqlmgr.preparedStmt.setInt(1, idCourse);
					ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
					if (Rs.next()) {
						String Sql = "select * from pf_user where idUser = ? and status = 1;";
						this.sqlmgr.prepare(Sql);
						this.sqlmgr.preparedStmt.setInt(1, idUser);
						ResultSet rS = this.sqlmgr.preparedStmt.executeQuery();
						if (rS.next()) {
							Info.put("createrName", rS.getString("name"));
						}
						Info.put("idCourse", idCourse);
						Info.put("idCreater", Rs.getInt("idCreater"));
						Info.put("courseName", Rs.getString("courseName"));
						Info.put("logo", Rs.getString("logo"));
						Info.put("questionSet", Rs.getInt("questionSet"));
						sql = "SELECT * FROM physicalFlower.courseCount where idCourse = ?";
						this.sqlmgr.prepare(sql);
						this.sqlmgr.preparedStmt.setInt(1, idCourse);
						ResultSet result = this.sqlmgr.preparedStmt.executeQuery();
						if (result.next()) {
							Info.put("count", result.getInt("stuCount"));
						}
					}
					courseInfo.add(Info);
				}
				this.res.put("errCode", 0);
				this.res.put("msg", "get courseInfo success!");
				this.res.put("courseInfo", courseInfo);
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}
	}

	/**
	 * @author Mizuki 学生进行签到(进行距离判断)
	 */
	public void doSignin() {
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int idSignin = this.Req.getInt("idSignin");
			int idUser = Integer.parseInt(this.session.getAttribute("idUser").toString());
			BigDecimal latitude = this.Req.getBigDecimal("latitude");
			BigDecimal longitude = this.Req.getBigDecimal("longitude");
			int horizontalAccuracy = this.Req.getInt("horizontalAccuracy");
			String sql = "select * from pf_signinRecord where idSignin = ? and idUser = ?;";

			this.sqlmgr = new SQLManager();
			this.sqlmgr.prepare(sql);
			try {
				this.sqlmgr.preparedStmt.setInt(1, idSignin);
				this.sqlmgr.preparedStmt.setInt(2, idUser);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				if (rs.next()) {
					if (rs.getInt("status") == 1) {
						this.res.put("errCode", 1);
						this.res.put("msg", "user has already doSignin!");
					} else {
						sql = "update pf_signinRecord set status = 1, gmtModify = ? where idSignin = ? and idUser  = ?;";

						this.sqlmgr.prepare(sql);
						this.sqlmgr.preparedStmt.setLong(1, System.currentTimeMillis() / 1000);
						this.sqlmgr.preparedStmt.setInt(2, idSignin);
						this.sqlmgr.preparedStmt.setInt(3, idUser);

						this.sqlmgr.preparedStmt.execute();
						this.res.put("errCode", 0);
						this.res.put("msg", "doSignin success!");
					}
				} else {
					DistanceUtil distance = new DistanceUtil();
					String Sql = "select * from pf_signin where idSignin = ?;";
					this.sqlmgr.prepare(Sql);
					this.sqlmgr.preparedStmt.setInt(1, idSignin);
					ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
					if (Rs.next()) {
						BigDecimal lat2 = Rs.getBigDecimal("latitude");
						BigDecimal lng2 = Rs.getBigDecimal("longitude");
						int radius = Rs.getInt("radius");
						if (distance.getDistance(latitude.doubleValue(), longitude.doubleValue(), lat2.doubleValue(),
								lng2.doubleValue()) <= radius) {
							sql = "insert into pf_signinRecord (idUser, longitude, latitude, horizontalAccuracy, status, gmtCreate, idSignin) values (?, ?, ?, ?, ?, ?, ?);";
							this.sqlmgr.prepare(sql);
							this.sqlmgr.preparedStmt.setInt(1, idUser);
							this.sqlmgr.preparedStmt.setBigDecimal(2, longitude);
							this.sqlmgr.preparedStmt.setBigDecimal(3, latitude);
							this.sqlmgr.preparedStmt.setInt(4, horizontalAccuracy);
							this.sqlmgr.preparedStmt.setInt(5, 1);
							this.sqlmgr.preparedStmt.setLong(6, System.currentTimeMillis() / 1000);
							this.sqlmgr.preparedStmt.setInt(7, idSignin);

							this.sqlmgr.preparedStmt.execute();
							this.res.put("errCode", 0);
							this.res.put("msg", "doSignin success!");
						} else {
							this.res.put("distance", distance.getDistance(latitude.doubleValue(),
									longitude.doubleValue(), lat2.doubleValue(), lng2.doubleValue()));
							this.res.put("errCode", 4003);
							this.res.put("msg", "sorry, you are not in the area!");
						}
					} else {
						this.res.put("errCode", 1);
						this.res.put("msg", "sorry, the signin is not exist!");
						return;
					}
				}
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}
	}

	/**
	 * @author Mizuki 获得学生在某一课程的签到信息
	 */
	public void getStuAllSigninList() {
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int page = this.Req.getInt("page");
			int num = (page - 1) * 10;
			int idCourse = this.Req.getInt("idCourse");
			int idUser = Integer.parseInt(this.session.getAttribute("idUser").toString());
			this.sqlmgr = new SQLManager();
			String sql = "select * from pf_signin where idCourse = ? and status = 1 order by gmtEnd desc limit ?,10;";

			this.sqlmgr.prepare(sql);
			try {
				List<HashMap> signinInfo = new ArrayList<HashMap>();
				this.sqlmgr.preparedStmt.setInt(1, idCourse);
				this.sqlmgr.preparedStmt.setInt(2, num);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();

				while (rs.next()) {
					HashMap<Object, Object> Info = new HashMap<>();
					int idSignin = rs.getInt("idSignin");
					Info.put("gmtEnd", rs.getLong("gmtEnd"));
					Info.put("idSignin", idSignin);
					Info.put("signinName", rs.getString("signinName"));

					String Sql = "select * from pf_signinRecord where idSignin = ? and idUser = ?;";
					this.sqlmgr.prepare(Sql);
					this.sqlmgr.preparedStmt.setInt(1, idSignin);
					this.sqlmgr.preparedStmt.setInt(2, idUser);
					ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
					if (Rs.next()) {
						Info.put("status", Rs.getInt("status"));
						Info.put("gmtCreate", Rs.getLong("gmtCreate"));
						Info.put("longitude", Rs.getLong("longitude"));
						Info.put("latitude", Rs.getLong("latitude"));
					}
					signinInfo.add(Info);
				}
				this.res.put("signinInfo", signinInfo);
				this.res.put("errCode", 0);
				this.res.put("msg", "get stuSigninList success!");
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 管理签到
	 * @param idSignin
	 */
	public void manageSignin() {
		int idSignin = this.Req.getInt("idSignin");
		int radius = this.Req.getInt("radius");
//		long gmtStart = this.Req.getLong("gmtStart");
		long gmtEnd = this.Req.getLong("gmtEnd");
		this.sqlmgr = new SQLManager();
		String sql = "update pf_signin set radius = ?, gmtEnd = ?, gmtModify = ? where idSignin = ?;";

		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, radius);
			this.sqlmgr.preparedStmt.setLong(2, gmtEnd);
			this.sqlmgr.preparedStmt.setLong(3, System.currentTimeMillis() / 1000);
			this.sqlmgr.preparedStmt.setInt(4, idSignin);

			this.sqlmgr.preparedStmt.execute();

			this.res.put("errCode", 0);
			this.res.put("msg", "manange signin success!");
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 教师删除学生签到
	 */
	public void delSigninRecord() {
		int idSignin = this.Req.getInt("idSignin");
		int idUser = this.Req.getInt("idUser");
		this.sqlmgr = new SQLManager();
		String sql = "update pf_signinRecord set status = 0, gmtModify = ? where idUser = ? and idSignin = ?;";

		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setLong(1, System.currentTimeMillis() / 1000);
			this.sqlmgr.preparedStmt.setInt(2, idUser);
			this.sqlmgr.preparedStmt.setInt(3, idSignin);

			this.sqlmgr.preparedStmt.execute();

			this.res.put("errCode", 0);
			this.res.put("msg", "delSigninRecord success!");
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 老师帮助学生代签
	 */
	public void substitudeSignin() {
		int idSignin = this.Req.getInt("idSignin");
		int idUser = this.Req.getInt("idUser");
		this.sqlmgr = new SQLManager();
		String Sql = "select * from pf_signin where idSignin = ?";
		String sql = "select * from pf_signinRecord where idSignin = ? and idUser = ?;";
		try {
			this.sqlmgr.prepare(Sql);
			this.sqlmgr.preparedStmt.setInt(1, idSignin);
			ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
			if (Rs.next()) {
				this.sqlmgr.prepare(sql);
				BigDecimal latitude = Rs.getBigDecimal("latitude");
				BigDecimal longitude = Rs.getBigDecimal("longitude");
				int horizontalAccuracy = Rs.getInt("horizontalAccuracy");

				this.sqlmgr.preparedStmt.setInt(1, idSignin);
				this.sqlmgr.preparedStmt.setInt(2, idUser);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
				if (rs.next()) {
					if (rs.getInt("status") == 1) {
						this.res.put("errCode", 1);
						this.res.put("msg", "user has already doSignin!");
					} else {
						sql = "update pf_signinRecord set status = 2, gmtModify = ? where idSignin = ? and idUser  = ?;";

						this.sqlmgr.prepare(sql);
						this.sqlmgr.preparedStmt.setLong(1, System.currentTimeMillis() / 1000);
						this.sqlmgr.preparedStmt.setInt(2, idSignin);
						this.sqlmgr.preparedStmt.setInt(3, idUser);

						this.sqlmgr.preparedStmt.execute();
						this.res.put("errCode", 0);
						this.res.put("msg", "doSignin success!");
					}
				} else {
					sql = "insert into pf_signinRecord (idUser, longitude, latitude, horizontalAccuracy, status, gmtCreate, idSignin) values (?, ?, ?, ?, ?, ?, ?);";
					this.sqlmgr.prepare(sql);
					this.sqlmgr.preparedStmt.setInt(1, idUser);
					this.sqlmgr.preparedStmt.setBigDecimal(2, longitude);
					this.sqlmgr.preparedStmt.setBigDecimal(3, latitude);
					this.sqlmgr.preparedStmt.setInt(4, horizontalAccuracy);
					this.sqlmgr.preparedStmt.setInt(5, 2);
					this.sqlmgr.preparedStmt.setLong(6, System.currentTimeMillis() / 1000);
					this.sqlmgr.preparedStmt.setInt(7, idSignin);

					this.sqlmgr.preparedStmt.execute();
					this.res.put("errCode", 0);
					this.res.put("msg", "doSignin success!");
				}

			} else {
				this.res.put("errCode", 4003);
				this.res.put("msg", "no such signin!");
			}
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 获取进行中的签到列表
	 */
	public void getDoingSigninList() {
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int idCourse = this.Req.getInt("idCourse");
			int idUser = Integer.parseInt(this.session.getAttribute("idUser").toString());
			this.sqlmgr = new SQLManager();
			String sql = "select * from pf_signin where idCourse = ? and status = 1;";

			this.sqlmgr.prepare(sql);
			try {
				List<HashMap> signinInfo = new ArrayList<HashMap>();
				this.sqlmgr.preparedStmt.setInt(1, idCourse);
				ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();

				while (rs.next()) {
					if (rs.getLong("gmtEnd") > System.currentTimeMillis() / 1000) {
						HashMap<Object, Object> Info = new HashMap<>();
						int idSignin = rs.getInt("idSignin");
						Info.put("gmtEnd", rs.getLong("gmtEnd"));
						Info.put("idSignin", idSignin);
						Info.put("signinName", rs.getString("signinName"));

						String Sql = "select * from pf_signinRecord where idSignin = ? and idUser = ?;";
						this.sqlmgr.prepare(Sql);
						this.sqlmgr.preparedStmt.setInt(1, idSignin);
						this.sqlmgr.preparedStmt.setInt(2, idUser);
						ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
						if (Rs.next()) {
							Info.put("status", Rs.getInt("status"));
//							Info.put("gmtCreate", Rs.getLong("gmtCreate"));
//							Info.put("longitude", Rs.getLong("longitude"));
//							Info.put("latitude", Rs.getLong("latitude"));
						}
						signinInfo.add(Info);
					}
				}
				this.res.put("signinInfo", signinInfo);
				this.res.put("errCode", 0);
				this.res.put("msg", "get stuSigninList success!");
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 获取课程的学生信息列表
	 */
	public void getStuList() {
		int idCourse = this.Req.getInt("idCourse");
		this.sqlmgr = new SQLManager();
		String sql = "SELECT * FROM physicalFlower.pf_courseAdd,physicalFlower.pf_user where pf_user.idUser=pf_courseAdd.idUser and pf_user.status =1 and pf_courseAdd.`status`=1 and idCourse = ?;";

		this.sqlmgr.prepare(sql);
		try {
			List<HashMap> classStuInfo = new ArrayList<HashMap>();
			this.sqlmgr.preparedStmt.setInt(1, idCourse);
			int count = 0;
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();

			while (rs.next()) {
				HashMap<Object, Object> Info = new HashMap<>();
				Info.put("idUser", rs.getInt("idUser"));
				Info.put("idCourse", rs.getInt("idCourse"));
				Info.put("name", rs.getString("name"));
				Info.put("stunume", rs.getString("stunum"));
				count = count + 1;
				classStuInfo.add(Info);
			}
			this.res.put("count", count);
			this.res.put("classStuInfo", classStuInfo);
			this.res.put("errCode", 0);
			this.res.put("msg", "get information success!");
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());

		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki
	 * @param check 0为已签到，1为未签到
	 */
	public void getClassSigninInfo() {
		int idSignin = this.Req.getInt("idSignin");
		int idCourse = this.Req.getInt("idCourse");
		this.sqlmgr = new SQLManager();
		String sql = "SELECT * FROM physicalFlower.pf_courseAdd,physicalFlower.pf_user where pf_user.idUser=pf_courseAdd.idUser and pf_user.status =1 and pf_courseAdd.`status`=1 and idCourse = ?;";

		this.sqlmgr.prepare(sql);
		try {
			int count = 0;
			List<HashMap> classStuSigninInfo = new ArrayList<HashMap>();
			this.sqlmgr.preparedStmt.setInt(1, idCourse);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			while (rs.next()) {
				HashMap<Object, Object> Info = new HashMap<>();
				int check = 0;
				int idUser = rs.getInt("idUser");
				Info.put("name", rs.getString("name"));
				Info.put("idUser", idUser);
				Info.put("stunum", rs.getString("stunum"));
				String Sql = "select * from pf_signinRecord where idUser = ? and idSignin = ? and (status = 1 or status = 2);";
				this.sqlmgr.prepare(Sql);
				this.sqlmgr.preparedStmt.setInt(1, idUser);
				this.sqlmgr.preparedStmt.setInt(2, idSignin);
				ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
				if (Rs.next()) {
					check = 1;
					count = count + 1;
				}
				Info.put("check", check);
				classStuSigninInfo.add(Info);
			}
			this.res.put("errCode", 0);
			this.res.put("msg", "get information success!");
			this.res.put("count", count);
			this.res.put("classStuSigninInfo", classStuSigninInfo);
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 老师删除某次签到
	 */
	public void delSignin() {
		int idSignin = this.Req.getInt("idSignin");
		this.sqlmgr = new SQLManager();
		String sql = "update pf_signin set status = 0 , gmtModify = ? where idSignin = ?;";

		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setLong(1, System.currentTimeMillis() / 1000);
			this.sqlmgr.preparedStmt.setInt(2, idSignin);
			this.sqlmgr.preparedStmt.execute();
			this.res.put("errCode", 0);
			this.res.put("msg", "delete signin success!");
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 获取一个章节的所有题目
	 */
	public void getChapterQuestionList() {
		int idQuestionChapter = this.Req.getInt("idQuestionChapter");
		String sql = "select * from pf_questionBank where idQuestionChapter = ? and status = 1 and type = 4";
		this.sqlmgr = new SQLManager();
		this.sqlmgr.prepare(sql);

		try {
			List<HashMap> questionInfo = new ArrayList<HashMap>();
			this.sqlmgr.preparedStmt.setInt(1, idQuestionChapter);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();

			while (rs.next()) {
				HashMap<Object, Object> Info = new HashMap<>();
				Info.put("idQues", rs.getInt("idQues"));
				Info.put("img", imgUrl + rs.getString("img"));
				questionInfo.add(Info);
			}
			this.res.put("errCode", 0);
			this.res.put("msg", "get list success!");
			this.res.put("questionInfo", questionInfo);
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 老师布置课后作业作业
	 */
	public void arrangeHomework() {
		int idCourse = this.Req.getInt("idCourse");
		Long gmtEnd = this.Req.getLong("gmtEnd");
		Long gmtStart = System.currentTimeMillis() / 1000;
		int length = this.Req.getInt("length");

		if (gmtStart <= gmtEnd) {
			this.res.put("errCode", 4003);
			this.res.put("msg", "sorry, please reset time!");
			return;
		} else {
			String sql = "insert into pf_homework (idCourse, gmtStart, gmtEnd, status, gmtCreate) values (?, ?, ?, ?, ?)";
			this.sqlmgr = new SQLManager();
			this.sqlmgr.prepare(sql);

			try {
				this.sqlmgr.preparedStmt = this.sqlmgr.conn.prepareStatement(sql,
						this.sqlmgr.stmt.RETURN_GENERATED_KEYS);
				this.sqlmgr.preparedStmt.setInt(1, idCourse);
				this.sqlmgr.preparedStmt.setLong(2, gmtStart);
				this.sqlmgr.preparedStmt.setLong(3, gmtEnd);
				this.sqlmgr.preparedStmt.setInt(4, 1);
				this.sqlmgr.preparedStmt.setLong(5, System.currentTimeMillis() / 1000);

				this.sqlmgr.preparedStmt.executeUpdate();
				ResultSet Rs = this.sqlmgr.preparedStmt.getGeneratedKeys();
				if (Rs.next()) {
					this.res.put("idHomework", Rs.getInt(1));
					int idHomework = Rs.getInt(1);
					JSONObject idQues = this.Req.getJSONObject("idQues");
					String Sql = "insert into pf_homeworkDetail (idHomework, idQues, status, gmtCreate) values (?, ?, ?, ?)";
					for (int i = 0; i < length; i++) {
						this.sqlmgr.prepare(Sql);
						this.sqlmgr.preparedStmt.setInt(1, idHomework);
						this.sqlmgr.preparedStmt.setInt(2, idQues.getInt(String.valueOf(i)));
						this.sqlmgr.preparedStmt.setInt(3, 1);
						this.sqlmgr.preparedStmt.setLong(4, System.currentTimeMillis() / 1000);
						this.sqlmgr.preparedStmt.execute();
					}
					this.res.put("errCode", 0);
					this.res.put("msg", "arrange homework success!");
				}
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}

		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 老师获取布置的作业的具体信息（包含的题目，截止时间）
	 */
	public void getHomeworkDetails() {
		int count = 0;
		int idHomework = this.Req.getInt("idHomework");
		this.sqlmgr = new SQLManager();
		String sql = "select * from physicalFlower.pf_homeworkDetail,physicalFlower.pf_homework where pf_homework.idHomework = pf_homeworkDetail.idHomework and pf_homeworkDetail.idHomework = ? and pf_homeworkDetail.status = 1 and pf_homework.status = 1;";

		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, idHomework);

			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			List<HashMap> homeworkInfo = new ArrayList<HashMap>();
			while (rs.next()) {
				count = count + 1;
				HashMap<Object, Object> Info = new HashMap<>();
				Info.put("idQues", rs.getInt("idQues"));
				Info.put("gmtEnd", rs.getLong("gmtEnd"));
				Info.put("gmtStart", rs.getLong("gmtStart"));
				homeworkInfo.add(Info);
			}
			this.res.put("errCode", 0);
			this.res.put("msg", "get information success!");
			this.res.put("count", count);
			this.res.put("homeworkInfo", homeworkInfo);
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 获取课程的作业列表
	 */
	public void getHomeworkList() {
		int idCourse = this.Req.getInt("idCourse");
		this.sqlmgr = new SQLManager();
		String sql = "select * from pf_homework where idCourse = ? and status = 1;";

		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, idCourse);

			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			List<HashMap> homeworkInfo = new ArrayList<HashMap>();
			while (rs.next()) {
				HashMap<Object, Object> Info = new HashMap<>();
				Info.put("idHomework", rs.getInt("idHomework"));
				Info.put("gmtStart", rs.getLong("gmtStart"));
				Info.put("gmtEnd", rs.getLong("gmtEnd"));
				homeworkInfo.add(Info);
			}
			this.res.put("errCode", 0);
			this.res.put("msg", "get the list success!");
			this.res.put("homeworkInfo", homeworkInfo);
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 获取章节列表
	 */
	public void getChapterList() {
		int idQuestionSet = this.Req.getInt("idQuestionSet");
		this.sqlmgr = new SQLManager();
		String sql = "select * from pf_questionChapter where idQuestionSet = ? and status = 1;";

		this.sqlmgr.prepare(sql);
		List<HashMap> chapterInfo = new ArrayList<HashMap>();
		try {
			this.sqlmgr.preparedStmt.setInt(1, idQuestionSet);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			while (rs.next()) {
				HashMap<Object, Object> Info = new HashMap<>();
				Info.put("idQuestionChapter", rs.getInt("idQuestionChapter"));
				Info.put("nameChapter", rs.getString("nameChapter"));
				chapterInfo.add(Info);
			}
			this.res.put("errCode", 0);
			this.res.put("msg", "get list success!");
			this.res.put("chapterInfo", chapterInfo);
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 提交作业
	 */
	public void submitHomework() {
		if (this.session.getAttribute("idUser") == null) {
			this.res.put("errCode", 4002);
			this.res.put("msg", "Login required.");
			this.out.println(new JSONObject(this.res).toString(2));
			return;
		} else {
			int idUser = Integer.parseInt(this.session.getAttribute("idUser").toString());

			int idHomeworkDetail = this.Req.getInt("idHomeworkDetail");
			String submitImg1 = "";
			String submitImg2 = "";
			String submitImg3 = "";
			this.sqlmgr = new SQLManager();
			String sql = "insert into pf_homeworkSubmit (idHomeworkDetail, idUser, submitImg1, submitImg2, submitImg3, status, gmtCreate) values (?, ?, ? ,? ,?, ?, ?);";
			if (this.Req.getString("submitImg1") != null) {
				submitImg1 = this.Req.getString("submitImg1");
			}
			if (this.Req.getString("submitImg2") != null) {
				submitImg1 = this.Req.getString("submitImg2");
			}
			if (this.Req.getString("submitImg3") != null) {
				submitImg1 = this.Req.getString("submitImg3");
			}

			this.sqlmgr.prepare(sql);
			try {
				this.sqlmgr.preparedStmt.setInt(1, idHomeworkDetail);
				this.sqlmgr.preparedStmt.setInt(2, idUser);
				this.sqlmgr.preparedStmt.setString(3, submitImg1);
				this.sqlmgr.preparedStmt.setString(4, submitImg2);
				this.sqlmgr.preparedStmt.setString(5, submitImg3);
				this.sqlmgr.preparedStmt.setInt(6, 1);
				this.sqlmgr.preparedStmt.setLong(7, System.currentTimeMillis() / 1000);

				this.sqlmgr.preparedStmt.execute();
				this.res.put("errCode", 0);
				this.res.put("msg", "submit success!");
			} catch (SQLException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
				this.res.put("msg", e.toString());
			}
			this.out.print(new JSONObject(this.res).toString(2));
		}

	}

	/**
	 * @author Mizuki 老师获得班级学生的作业提交情况
	 */
	public void getStuHomeworkList() {
		int check = 0;
		int idHomework = this.Req.getInt("idHomework");
		this.sqlmgr = new SQLManager();
		String sql = "select * from pf_homework where idHomework = ? and status = 1";

		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, idHomework);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			if (rs.next()) {
				int idCourse = rs.getInt("idCourse");
				String Sql = "SELECT * FROM physicalFlower.pf_courseAdd,physicalFlower.pf_user where pf_user.idUser=pf_courseAdd.idUser and pf_user.status =1 and pf_courseAdd.`status`=1 and idCourse = ?;";
				this.sqlmgr.prepare(Sql);
				this.sqlmgr.preparedStmt.setInt(1, idCourse);
				ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
				List<HashMap> homeworkInfo = new ArrayList<HashMap>();
				while (Rs.next()) {
					HashMap Info = new HashMap<>();
					int idUser = Rs.getInt("idUser");
					Info.put("idUser", idUser);
					Info.put("stunum", Rs.getString("stunum"));
					Info.put("name", Rs.getString("name"));

					String SQL = "SELECT * FROM physicalFlower.pf_homeworkDetail, physicalFlower.pf_homeworkSubmit WHERE pf_homeworkDetail.idHomeworkDetail = pf_homeworkSubmit.idHomeworkDetail AND pf_homeworkDetail.idHomework = ? AND pf_homeworkSubmit.idUser = ?;";
					this.sqlmgr.prepare(SQL);
					this.sqlmgr.preparedStmt.setInt(1, idHomework);
					this.sqlmgr.preparedStmt.setInt(2, idUser);
					ResultSet rS = this.sqlmgr.preparedStmt.executeQuery();
					if (rS.next()) {
						check = 1;
					} else {
						check = 0;
					}
					Info.put("check", check);
					homeworkInfo.add(Info);
				}
				this.res.put("homeworkInfo", homeworkInfo);
			} else {
				this.res.put("errCode", 4003);
				this.res.put("msg", "please set homework firse!");
			}
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 获得已经提交作业的学生的作业的详细情况
	 */
	public void getStuHomeworkInfo() {
		int idUser = this.Req.getInt("idUser");
		int idHomework = this.Req.getInt("idHomework");
		this.sqlmgr = new SQLManager();
		String sql = "SELECT * FROM physicalFlower.pf_homeworkDetail, physicalFlower.pf_homeworkSubmit WHERE pf_homeworkDetail.idHomeworkDetail = pf_homeworkSubmit.idHomeworkDetail AND pf_homeworkDetail.idHomework = ? AND pf_homeworkSubmit.idUser = ?;";
	
		this.sqlmgr.prepare(sql);
		try {
			this.sqlmgr.preparedStmt.setInt(1, idHomework);
			this.sqlmgr.preparedStmt.setInt(2, idUser);
			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
			List<HashMap> homeworkInfo = new ArrayList<HashMap>();
			while(rs.next()) {
				HashMap Info = new HashMap<>();
				Info.put("idQues", rs.getInt("idQues"));
				Info.put("submitImg1", rs.getString("submitImg1"));
				Info.put("submitImg2", rs.getString("submitImg2"));
				Info.put("submitImg3", rs.getString("submitImg3"));
				homeworkInfo.add(Info);
			}
			this.res.put("homeworkInfo", homeworkInfo);
		} catch (SQLException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
			this.res.put("msg", e.toString());
		}
		this.out.print(new JSONObject(this.res).toString(2));
	}

	/**
	 * @author Mizuki 老师获得班级学生的作业提交情况
	 */
//	public void getStuHomeworkList() {
//		int idHomework = this.Req.getInt("idHomework");
//		this.sqlmgr = new SQLManager();
//		String sql = "select * from pf_homework where idHomework = ? and status = 1;";
//		int count = 0;
//
//		this.sqlmgr.prepare(sql);
//		try {
//			this.sqlmgr.preparedStmt.setInt(1, idHomework);
//			ResultSet rs = this.sqlmgr.preparedStmt.executeQuery();
//			if (rs.next()) {
//				int idCourse = rs.getInt("idCourse");
//				String Sql = "SELECT * FROM physicalFlower.pf_courseAdd,physicalFlower.pf_user where pf_user.idUser=pf_courseAdd.idUser and pf_user.status =1 and pf_courseAdd.`status`=1 and idCourse = ?;";
//				this.sqlmgr.prepare(Sql);
//				this.sqlmgr.preparedStmt.setInt(1, idCourse);
//				ResultSet Rs = this.sqlmgr.preparedStmt.executeQuery();
//				while (Rs.next()) {
//					List<HashMap> stuHomework = new ArrayList<HashMap>();
//					HashMap user = new HashMap<>();
//					count = count + 1;
//					int check = 0;
//					user.put("name", Rs.getString("name"));
//					user.put("stunum", Rs.getString("stunum"));
//					user.put("check", check);
//					int idUser = Rs.getInt("idUser");
////					stuHomework.add(user);
//					String SQL = "select * from physicalFlower.pf_homeworkDetail, physicalFlower.pf_homeworkSubmit where pf_homeworkDetail.idHomeworkDetail = pf_homeworkSubmit.idHomeworkDetail and pf_homeworkDetail.idHomework = ? and pf_homeworkSubmit.idUser = ? and pf_homeworkDetail.status = 1 and pf_homeworkSubmit.`status`= 1;";
//					this.sqlmgr.prepare(SQL);
//					this.sqlmgr.preparedStmt.setInt(1, idHomework);
//					this.sqlmgr.preparedStmt.setInt(2, idUser);
//					ResultSet RS = this.sqlmgr.preparedStmt.executeQuery();
//					while (RS.next()) {
//						check = 1;
//						HashMap<Object, Object> Info = new HashMap<>();
//						Info.put("idQues", RS.getInt("idQues"));
//						Info.put("submitImg1", RS.getString("submitImg1"));
//						Info.put("submitImg2", RS.getString("submitImg2"));
//						Info.put("submitImg3", RS.getString("submitImg3"));
//						stuHomework.add(Info);
//					}
//					user.put("check", check);
//					stuHomework.add(user);
//					this.res.put(String.valueOf(count), stuHomework);
//				}
//			} else {
//				this.res.put("errCode", 4003);
//				this.res.put("msg", "sorry, no homework!");
//			}
//		} catch (SQLException e) {
//			// TODO 自动生成的 catch 块
//			e.printStackTrace();
//			this.res.put("msg", e.toString());
//		}
//		this.out.print(new JSONObject(this.res).toString(2));
//	}

}
