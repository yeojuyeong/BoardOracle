package com.board.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.board.dto.MemberDTO;
import com.board.service.BoardService;
import com.board.service.MemberService;

@Controller
public class MasterController {
	
	@Autowired
	MemberService service;
	@Autowired
	BoardService serviceb;
	
	//시스템 관리 페이지
	@GetMapping("/master/sysManage")
	public void getSysManage() {
		
	}
	
	// 전체 회원 수
    @GetMapping("/master/getTotalMembers")
    @ResponseBody
    public int getTotalMembers() {
        int totalMembers = service.getTotalMembers();
        return totalMembers;
    }
    
    //전체 게시물 갯수
    @GetMapping("/master/getTotalBoards")
    @ResponseBody
    public int getTotalBoards() throws Exception {
        int totalBoards = serviceb.getTotalBoards();
        return totalBoards;
    }
    
    //전체 댓글 갯수
    @GetMapping("/master/getTotalReplys")
    @ResponseBody
    public int getTotalReplys() throws Exception {
        int totalReplys = serviceb.getTotalReplys();
        return totalReplys;
    }
}
