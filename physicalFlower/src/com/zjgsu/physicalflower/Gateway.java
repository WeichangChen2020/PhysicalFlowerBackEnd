package com.zjgsu.physicalflower;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;

public class Gateway extends HttpServlet {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 * @param Req JSONObject格式化请求体
	 * @param out PrintWriter
	 * @param pf  平台
	 */
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws IOException, IllegalArgumentException {

		request.setCharacterEncoding("UTF-8");
		response.setCharacterEncoding("UTF-8");
		response.addHeader("Content-Type", "application/json");

		JSONObject Req = this.getReq(request);
		PrintWriter out = response.getWriter();
		HttpSession session = request.getSession();
    
		String pf = Req.getString("pf");
		String tag = Req.getString("tag");
		if (pf.equals("wx")) {
//			实例化WxHandler
			WxHandler wxhdr = new WxHandler(Req, out, session);

			System.out.println(tag);
			switch (tag) {
			case "userLogin":
				wxhdr.userLogin();
				break;
			case "updateInfo":
				wxhdr.updateInfo();
				break;
			case "addToCourse":
				wxhdr.addToCourse();
				break;
			case "delUser":
				wxhdr.delUser();
				break;
			case "courseCreate":
				wxhdr.courseCreate();
				break;
			case "delCourse":
				wxhdr.delCourse();
				break;
			case"courseDetail":
				wxhdr.courseDetail();
				break;
			case "queReport":
				wxhdr.queReport();
				break;
			case"signinCreate":
				wxhdr.signinCreate();
				break;
			case"doSignin":
				wxhdr.doSignin();
				break;
			case"getSigninList":
				wxhdr.getSigninList();
				break;
			case"getCreatecourselist":
				wxhdr.getCreatecourselist();
				break;
			case"getJoincourselist":
				wxhdr.getJoincourselist();
				break;
			case"getStuAllSigninList":
				wxhdr.getStuAllSigninList();
				break;
			case"manageSignin":
				wxhdr. manageSignin();
				break;
			case"delSigninRecord":
				wxhdr.delSigninRecord();
				break;
			case"substitudeSignin":
				wxhdr.substitudeSignin();
				break;
			case"getDoingSigninList":
				wxhdr.getDoingSigninList();
				break;
			case"getStuList":
				wxhdr.getStuList();
				break;
			case"getClassSigninInfo":
				wxhdr.getClassSigninInfo();
				break;
			case"delSignin":
				wxhdr.delSignin();
				break;
			case"getChapterQuestionList":
				wxhdr.getChapterQuestionList();
				break;
			case"arrangeHomework":
				wxhdr.arrangeHomework();
				break;
			case"getHomeworkDetails":
				wxhdr.getHomeworkDetails();
				break;
			case"getHomeworkList":
				wxhdr.getHomeworkList();
				break;
			case"getChapterList":
				wxhdr.getChapterList();
				break;
			case"submitHomework":
				wxhdr.submitHomework();
				break;
			case"getStuHomeworkList":
				wxhdr.getStuHomeworkList();
				break;
			case"getStuHomeworkInfo":
				wxhdr.getStuHomeworkInfo();
				break;
			}

		} 
	}

	/**
	 * 获取POST请求中Body参数
	 * 
	 * @param request
	 * @return JSONObject
	 */
	public JSONObject getReq(HttpServletRequest request) {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(request.getInputStream(), "UTF-8"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String line = null;
		StringBuilder sb = new StringBuilder();
		try {
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(sb.toString());
		return new JSONObject(sb.toString());

	}
	
	
}