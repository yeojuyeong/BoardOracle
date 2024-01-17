package com.board.controller;

import java.io.File;
import java.lang.reflect.Member;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.board.dto.AddressDTO;
import com.board.dto.MemberDTO;
import com.board.service.MemberService;
import com.board.util.Page;

@Controller
public class MemberController {

	@Autowired
	MemberService service;
	
	@Autowired
	private BCryptPasswordEncoder pwdEncoder;
	
	//로그인 화면 보기
	@GetMapping("/member/login")
	public void getLogin() { }
	
	//로그인 
	@ResponseBody
	@PostMapping("/member/login")
	public String postLogin(MemberDTO member,HttpSession session,@RequestParam("autologin") String autologin) {
		
		String authkey = "";
		
		//로그인 시 authkey 생성  
		if(autologin.equals("NEW")) {
			authkey = UUID.randomUUID().toString().replaceAll("-", "");
			member.setAuthkey(authkey);
			service.authkeyUpdate(member);			
		}
		
		//authkey가 클라이언트에 쿠키로 존재할 경우 로그인 과정 없이 세션 생성 후 게시판 목록 페이지로 이동
		if(autologin.equals("PASS")) {
			MemberDTO memberInfo = service.memberInfoByAuthkey(member);
			if(memberInfo != null) {
				//세션 생성
				session.setMaxInactiveInterval(3600*24*7);//세션 유지 기간 설정
				session.setAttribute("userid", memberInfo.getUserid());
				session.setAttribute("username", memberInfo.getUsername());
				session.setAttribute("role", memberInfo.getRole());
				
				return "{\"message\":\"GOOD\"}";
			}
		}		
		
		//아이디 존재 여부 확인
		if(service.idCheck(member.getUserid()) == 0) {
			return "{\"message\":\"ID_NOT_FOUND\"}";
		}
		
		//패스워드가 올바르게 들어 왔는지 확인
		if(!pwdEncoder.matches(member.getPassword(), service.memberInfo(member.getUserid()).getPassword())) {
			//잘못된 패스워드 일때
			return "{\"message\":\"PASSWORD_NOT_FOUND\"}";
		}else {
			//제대로 된 아이디와 패스워드가 입력되었을 때
			
			//마지막 로그인 날짜 등록
			member.setLastlogindate(LocalDate.now());
			service.lastlogindateUpdate(member);	
			
			//패스워드 확인 후 마지막 패스워드 변경일이 30일이 경과 되었을 경우 ...
			
			
			//세션 생성
			session.setMaxInactiveInterval(3600*24*7);//세션 유지 기간 설정
			session.setAttribute("userid", service.memberInfo(member.getUserid()).getUserid());
			session.setAttribute("username", service.memberInfo(member.getUserid()).getUsername());
			session.setAttribute("role", service.memberInfo(member.getUserid()).getRole());
			
			return "{\"message\":\"GOOD\",\"authkey\":\"" + member.getAuthkey() + "\"}";
		}		
	}
	
	//회원 등록 화면 보기
	@GetMapping("/member/signup")
	public void getSignup() {	}
	
	//회원 등록 하기
	@ResponseBody
	@PostMapping("/member/signup")
	public Map<String,String> postSignup(MemberDTO member, @RequestParam("fileUpload") MultipartFile multipartFile) throws Exception {
		
		String path = "c:\\Repository\\profile\\";
		File targetFile;
		
		if(!multipartFile.isEmpty()) {
			
			String org_filename = multipartFile.getOriginalFilename();
			String org_fileExtension = org_filename.substring(org_filename.lastIndexOf("."));			
			String stored_filename = UUID.randomUUID().toString().replaceAll("-", "") + org_fileExtension;
			
			try {
				targetFile = new File(path + stored_filename);				
				multipartFile.transferTo(targetFile);
				
				member.setOrg_filename(org_filename);
				member.setStored_filename(stored_filename);
				member.setFilesize(multipartFile.getSize());				
				
			}catch(Exception e) {
				e.printStackTrace();
			}
			
			String inputPassword = member.getPassword();
			String pwd = pwdEncoder.encode(inputPassword); //단방향 암호화 
			member.setPassword(pwd);
			member.setLastpwdate(LocalDate.now());
			
		}
		
		service.memberInfoRegistry(member);
		
		Map<String,String> data = new HashMap<>();
		data.put("message", "GOOD");
		data.put("username", URLEncoder.encode(member.getUsername(),"UTF-8"));
		
		return data;
		
		//return "{\"message\":\"GOOD\",\"username\":\"" + URLEncoder.encode(member.getUsername(),"UTF-8") + "\"}";
	}
	
	//회원 가입 시 아이디 중복 확인
	@ResponseBody
	@PostMapping("/member/idCheck")
	public int postIdCheck(@RequestBody String userid) throws Exception{
		
		int result = service.idCheck(userid);
		return result;
	}
	
	//회원 정보 보기
	@GetMapping("/member/memberInfo")
	public void getMemberInfo(HttpSession session, Model model) throws Exception {
		String userid = (String)session.getAttribute("userid");
		model.addAttribute("memberInfo", service.memberInfo(userid));
	}
	
	//회원 정보 수정 화면 보기
    @GetMapping("/member/memberInfoModify")
    public void getMemberInfoModify(Model model, HttpSession session) throws Exception{
        String userid = (String) session.getAttribute("userid");
        model.addAttribute("memberInfo", service.memberInfo(userid));
       // return "{\"message\":\"GOOD\"}"; // 회원 정보 수정 페이지의 HTML 파일명
		//return "/member/memberInfoModify";
    }

    //회원 정보 수정
    @ResponseBody
    @PostMapping("/member/memberInfoModify")
    public String postMemberInfoModify(@RequestBody MemberDTO member) throws Exception {
        // 회원 정보 수정 로직
        try {
            // MemberDTO를 사용하여 회원 정보를 업데이트하는 서비스 메소드를 호출합니다.
            service.updateMemberInfo(member);
            // 성공한 경우 JSON 응답을 반환합니다.
            return "{\"message\":\"SUCCESS\"}";
        } catch (Exception e) {
            // 실패한 경우 예외 처리를 수행하고 에러 메시지를 반환합니다.
            e.printStackTrace();
            return "{\"message\":\"ERROR\", \"errorMessage\":\"회원 정보 수정에 실패했습니다.\"}";
        }
    }
	
	
	//회원 패스워드 변경
	@GetMapping("/member/memberPasswordModify")
	public void getMemberPasswordModify() throws Exception { }
	
	//회원 패스워드 변경
	@ResponseBody
	@PostMapping("/member/memberPasswordModify")
	public String postMemberPasswordModify(@RequestParam("old_password") String old_password, 
				@RequestParam("new_password") String new_password, HttpSession session) throws Exception { 
		
		String userid = (String)session.getAttribute("userid");		
		
		//패스워드가 올바르게 들어 왔는지 확인
		if(!pwdEncoder.matches(old_password, service.memberInfo(userid).getPassword())) {
			return "{\"message\":\"PASSWORD_NOT_FOUND\"}";
		}
		
		//신규 패스워드로 업데이트
		MemberDTO member = new MemberDTO();
		member.setUserid(userid);		
		member.setPassword(pwdEncoder.encode(new_password));
		member.setLastpwdate(LocalDate.now());
		service.memberPasswordModify(member);
		
		return "{\"message\":\"GOOD\"}";
	}
	
	//로그아웃
	@GetMapping("/member/logout")
	public void getLogout(HttpSession session,Model model) {
		String userid = (String)session.getAttribute("userid");
		String username = (String)session.getAttribute("username");
		
		MemberDTO member = new MemberDTO();
		member.setUserid(userid);
		member.setLastlogoutdate(LocalDate.now());
		
		service.lastlogoutdateUpdate(member);
		
		model.addAttribute("userid", userid);
		model.addAttribute("username", username);
		session.invalidate(); //모든 세션 종료 --> 로그아웃...
	}
	
	//패스워드 변경 후 세션 종료
	@GetMapping("/member/memberSessionOut")
	public String getMemberSessionOut(HttpSession session) {
		
		MemberDTO member = new MemberDTO();
		member.setUserid((String)session.getAttribute("userid"));
		member.setLastlogoutdate(LocalDate.now());
		service.lastlogoutdateUpdate(member);
		session.invalidate();	
		
		return "redirect:/";
	}
	
	//아이디 찾기
	@GetMapping("/member/searchID")
	public void getSearchID() {}
	
	//아이디 찾기
	@ResponseBody
	@PostMapping("/member/searchID")
	public String postSearchID(MemberDTO member) {
		
		String userid = service.searchID(member) == null?"ID_NOT_FOUND":service.searchID(member);		
		return "{\"message\":\"" + userid + "\"}";
	}
	
	//임시 패스워드 생성
	@GetMapping("/member/searchPassword")
	public void getSearchPassword() {}
	
	//임시 패스워드 생성
	@ResponseBody
	@PostMapping("/member/searchPassword")
	public String postSearchPassword(MemberDTO member) throws Exception{
		//아이디 존재 여부 확인
		if(service.idCheck(member.getUserid()) == 0)
			return "{\"status\":\"ID_NOT_FOUND\"}";
		//TELNO 확인
		if(!service.memberInfo(member.getUserid()).getTelno().equals(member.getTelno()))
			return "{\"status\":\"TELNO_NOT_FOUND\"}";
		
		//임시 패스워드 생성	
		String rawTempPW = service.tempPasswordMaker();
		member.setPassword(pwdEncoder.encode(rawTempPW));
		member.setLastpwdate(LocalDate.now());
		service.memberPasswordModify(member);

		return "{\"status\":\"GOOD\",\"password\":\"" + rawTempPW + "\"}";
	}
	
	//로그인 시 패스워드 변경 여부 확인
	@GetMapping("/member/pwCheckNotice")
	public void getPWCheckNotice() throws Exception {
		
	}
	
	//주소 검색
	@GetMapping("/member/addrSearch")
	public void getAddrsearch(@RequestParam("addrSearch") String addrSearch, 
			@RequestParam("page") int pageNum, Model model) {
				
		int postNum = 5; //한 화면에 보여지는 게시물 행의 갯수
		int startPoint = (pageNum-1) * postNum + 1; //페이지 시작 게시물 번호
		int endPoint = pageNum * postNum;
		int pageListCount = 5; //화면 하단에 보여지는 페이지리스트의 페이지 갯수		
		int totalCount = service.addrTotalCount(addrSearch); //전체 게시물 갯수
		
		Page page = new Page();
		model.addAttribute("list", service.addrSearch(startPoint, endPoint, addrSearch));
		model.addAttribute("pageList", page.getPageAddress(pageNum, postNum, pageListCount, totalCount, addrSearch));
		
	}
		
		//회원 탈퇴 처리
		@PostMapping("/member/delete")
		public String deleteMember(@RequestParam("userid") String userid, HttpSession session, HttpServletResponse response) {
	    service.deleteMember(userid);
	    session.removeAttribute("userid");
	    
	    //캐시 방지 헤더 추가
	    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); 
	    response.setHeader("Pragma", "no-cache");
	    response.setHeader("Expires", "0");
	    
	    //홈페이지로 리다이렉트
	    return "redirect:/";
	}

}
