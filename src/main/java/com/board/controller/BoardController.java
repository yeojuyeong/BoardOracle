package com.board.controller;

import java.io.File;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.board.dto.BoardDTO;
import com.board.dto.FileDTO;
import com.board.dto.LikeDTO;
import com.board.dto.ReplyDTO;
import com.board.service.BoardService;
import com.board.util.Page;

@Controller
@RequiredArgsConstructor 
public class BoardController {

	/*
	    1. @Autowired를 이용한 DI(Dependency Injection.의존성 주입) 
		@Autowired
		BoardService service; 
	*/
	
	/*
	    2. 생성자를 이용한 의존성 주입 
	    BoardService service;
	  	public BoardController(BoardService service) {
			this.service = service;
		}
    */

	//Lombok에 의한 생성자 생성으로 의존성 주입 
	private final BoardService service;
	
	//게시물 목록 보기
	@GetMapping("/board/list")
	public void getList(Model model,@RequestParam("page") int pageNum,
			@RequestParam(name="keyword",defaultValue="",required=false) String keyword) throws Exception {
		
		int postNum = 5; //한 화면에 보여지는 게시물 행의 갯수
		int startPoint = (pageNum-1) * postNum + 1; //페이지 시작 게시물 번호
		int endPoint = pageNum * postNum;
		int pageListCount = 5; //화면 하단에 보여지는 페이지리스트의 페이지 갯수		
		int totalCount = service.getTotalCount(keyword); //전체 게시물 갯수	
		
		Page page = new Page();

		model.addAttribute("list", service.list(startPoint,endPoint,keyword));
		model.addAttribute("totalElement", service.getTotalCount(keyword));
		model.addAttribute("postNum", postNum);
		model.addAttribute("page", pageNum);
		model.addAttribute("keyword", keyword);
		model.addAttribute("pageList", page.getPageList(pageNum, postNum, pageListCount,totalCount,keyword));
	}
	
	//게시물 등록 화면 보기
	@GetMapping("/board/write")
	public void getWrite() { }
	
	//첨부 파일 없는 게시물 등록 하기
	@ResponseBody
	@PostMapping("/board/write")
	public String postWrite(BoardDTO board) throws Exception{		
		int seqno = service.getSeqnoWithNextval();
		board.setSeqno(seqno);
		service.write(board);		
		return "{\"message\":\"GOOD\"}";		
	}
	
	//파일 업로드
	@ResponseBody
	@PostMapping("/board/fileUpload")
	public String postFileUpload(BoardDTO board,@RequestParam("kind") String kind,
			@RequestParam("sendToFileList") List<MultipartFile> multipartFile,
			@RequestParam(name="deleteFileList",required=false) int[] deleteFileList) throws Exception {		
		
		int seqno = 0;
		
		String path = "c:\\Repository\\file\\";
		if(kind.equals("I")) { //게시물 등록
			seqno = service.getSeqnoWithNextval();
			//System.out.println("seqno = " + seqno);
			board.setSeqno(seqno);
			service.write(board);
		}
		
		if(kind.equals("U")) { //게시물 수정
			service.modify(board);
			seqno = board.getSeqno();
			
			if(deleteFileList != null) {				
				for(int i=0; i<deleteFileList.length; i++) {
					service.deleteFileList(deleteFileList[i]);
				}				
			}			
		}
		
		if(!multipartFile.isEmpty()) {			
			File targetFile = null;
			Map<String,Object> fileInfo = null; 
			
			for(MultipartFile mpr:multipartFile) {
				
				String org_filename = mpr.getOriginalFilename();
				String org_fileExtension = org_filename.substring(org_filename.lastIndexOf("."));			
				String stored_filename = UUID.randomUUID().toString().replaceAll("-", "") + org_fileExtension;
				long filesize = mpr.getSize();
				
				try {
					targetFile = new File(path + stored_filename);				
					mpr.transferTo(targetFile);
					
					fileInfo = new HashMap<>();
					fileInfo.put("seqno", seqno);
					fileInfo.put("org_filename", org_filename);
					fileInfo.put("stored_filename", stored_filename);
					fileInfo.put("filesize", filesize);
					fileInfo.put("userid", board.getUserid());
					fileInfo.put("checkfile", "Y");//Y : 파일 존재, N : 파일 삭제
					
					service.fileInfoRegistry(fileInfo);
					
				}catch(Exception e) {
					e.printStackTrace();
				}
				
			}
		}
		
		return "{\"message\":\"GOOD\"}";
	}
	
	//파일 다운로드
	@GetMapping("/board/fileDownload")
	public void fileDownload(@RequestParam("fileseqno") int fileseqno, HttpServletResponse rs) throws Exception {
		
		String path = "c:\\Repository\\file\\";
		
		FileDTO fileInfo = service.fileInfo(fileseqno);
		String org_filename = fileInfo.getOrg_filename();
		String stored_filename = fileInfo.getStored_filename();
		byte fileByte[] = FileUtils.readFileToByteArray(new File(path+stored_filename));
		
		//Content-Disposition 헤드를 구성하여 바이트 타입으로 변환된 Http Response 메세지를 전송 한다는 것은   
		//이 파일을 다운받게끔 하는 것임
		//Content-Disposition 헤드 구성 예) Content-Disposition: attachment; filename="hello.jpg";
		
		rs.setContentType("application/octet-stream");
		rs.setContentLength(fileByte.length);
		rs.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(org_filename,"UTF-8") + "\";");
		rs.getOutputStream().write(fileByte);
		rs.getOutputStream().flush();
		rs.getOutputStream().close();		
	}
	
	//게시물 내용 보기
	@GetMapping("/board/view")
	public void getView(@RequestParam("seqno") int seqno, @RequestParam("page") int pageNum,
			@RequestParam(name="keyword",defaultValue="",required=false) String keyword,
			Model model, HttpSession session) throws Exception {
		
		//좋아요/싫어요 처리
		LikeDTO likeDTO = new LikeDTO();
		String session_userid = (String)session.getAttribute("userid"); 
		likeDTO.setSeqno(seqno);
		likeDTO.setUserid(session_userid);
		LikeDTO likeCheckView = service.likeCheckView(likeDTO);
		
		//초기에 좋아요/싫어요 등록이 안되어져 있을 경우 "N"으로 초기화
		if(likeCheckView == null) {
			model.addAttribute("myLikeCheck", "N");
			model.addAttribute("myDislikeCheck", "N");
		} else if(likeCheckView != null) {
			model.addAttribute("myLikeCheck", likeCheckView.getMylikecheck());
			model.addAttribute("myDislikeCheck", likeCheckView.getMydislikecheck()); 
		}
		
		model.addAttribute("view", service.view(seqno));
		model.addAttribute("page", pageNum);
		model.addAttribute("keyword", keyword);
		model.addAttribute("pre_seqno", service.pre_seqno(seqno,keyword));		
		model.addAttribute("next_seqno", service.next_seqno(seqno,keyword));
		model.addAttribute("fileInfoView", service.fileInfoView(seqno));
		
		//세션 userid값 가져 오기
		String sessionUserid = (String)session.getAttribute("userid");
		//조회수 증가
		if(!sessionUserid.equals(service.view(seqno).getUserid())){
			service.hitno(seqno);
		}
		
	}
	
	//게시물 수정 화면 보기
	@GetMapping("/board/modify")
	public void getModify(@RequestParam("seqno") int seqno, @RequestParam("page") int pageNum,
			@RequestParam(name="keyword",defaultValue="",required=false) String keyword,
			Model model) throws Exception {
		model.addAttribute("page", pageNum);
		model.addAttribute("keyword", keyword);
		model.addAttribute("view", service.view(seqno));
		model.addAttribute("fileInfoView", service.fileInfoView(seqno));
	}
	
	//게시물 수정 하기
	@ResponseBody
	@Transactional
	@PostMapping("/board/modify")
	public String postModify(BoardDTO board, @RequestParam("page") int pageNum,
			@RequestParam(name="keyword",defaultValue="",required=false) String keyword,
			@RequestParam(name="deleteFileList",required=false) int[] deleteFileList
			) throws Exception {
		
		service.modify(board);
		
		if(deleteFileList != null) {				
			for(int i=0; i<deleteFileList.length; i++) {
				service.deleteFileList(deleteFileList[i]);
			}				
		}		
		
		return "{\"message\":\"GOOD\"}";
	}
	
	//게시물 삭제하기
	@Transactional
	@GetMapping("/board/delete")
	public String getDelete(@RequestParam("seqno") int seqno) throws Exception {
		//transaction 시작
		service.fileInfoUpdate(seqno); //tbl_file 테이블의 checkfile column 값을 'X'로 변환
		service.delete(seqno); //게시물 행 삭제
		//transaction의 끝
		return "redirect:/board/list?page=1";
	}
	
	//좋아요/싫어요 관리
	@ResponseBody
	@PostMapping("/board/likeCheck")
	public String postLikeCheck(@RequestBody Map<String,Object> likeCheckData) throws Exception {
		
		int seqno = (int)likeCheckData.get("seqno");
		String userid = (String)likeCheckData.get("userid");
		String mylikecheck = (String)likeCheckData.get("mylikecheck");
		String mydislikecheck = (String)likeCheckData.get("mydislikecheck");
		int checkCnt = (int)likeCheckData.get("checkCnt");		
		
		//현재 날짜, 시간 구해서 좋아요/싫어요 한 날짜/시간 입력 및 수정
		String likeDate = "";
		String dislikeDate = "";
		LocalDateTime now = LocalDateTime.now();
		if(likeCheckData.get("mylikecheck").equals("Y")) {
			likeDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));			
		}else if(likeCheckData.get("mydislikecheck").equals("Y")){
			dislikeDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));		
		}		

		LikeDTO likeData = new LikeDTO();
		likeData.setSeqno(seqno);
		likeData.setUserid(userid);
		likeData.setMylikecheck(mylikecheck);
		likeData.setMydislikecheck(mydislikecheck);
		likeData.setLikedate(likeDate);
		likeData.setDislikedate(dislikeDate);
		
		//tbl_like 테이블 입력/수정
		LikeDTO likeCheckView = service.likeCheckView(likeData);
		if(likeCheckView == null) service.likeCheckRegistry(likeData);
			else service.likeCheckUpdate(likeData); 
		
		int likeCnt = service.view(seqno).getLikecnt();
		int dislikeCnt = service.view(seqno).getDislikecnt();
		
		switch(checkCnt) {
		case 1: likeCnt--; break;
		case 2: likeCnt++; dislikeCnt--; break;
		case 3: likeCnt++; break;
		case 4: dislikeCnt--; break;
		case 5: likeCnt--; dislikeCnt++; break;
		case 6: dislikeCnt++; break;
		}
		
		BoardDTO board = new BoardDTO();
		board.setSeqno(seqno);
		board.setLikecnt(likeCnt);
		board.setDislikecnt(dislikeCnt);		
		service.boardLikeUpdate(board);
		
		return "{\"likecnt\":\"" + likeCnt + "\",\"dislikecnt\": \"" + dislikeCnt + "\"}";
	}
	
	//댓글 처리
	@ResponseBody
	@PostMapping("/board/reply")
	public List<ReplyDTO> postReply(@RequestBody ReplyDTO reply, @RequestParam("option") String option) throws Exception{

		switch(option) {
			
			case "I" : service.replyRegistry(reply); //댓글 등록
						break;
			case "U" : service.replyUpdate(reply); //댓글 수정
						break;
			case "D" : service.replyDelete(reply); //댓글 삭제
					    break;
		}

		return service.replyView(reply);
	}
	
}
