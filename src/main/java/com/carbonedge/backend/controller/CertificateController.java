package com.carbonedge.backend.controller;
import com.carbonedge.backend.service.CertificateService; import java.util.*; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/certificates") @RequiredArgsConstructor public class CertificateController { private final CertificateService service;
 @PostMapping("/courses/{courseId}/mine") public Map<String,Object> issue(@RequestHeader("Authorization")String auth,@PathVariable Long courseId){return service.issueMine(auth,courseId);}
 @GetMapping("/verify/{code}") public Map<String,Object> verify(@PathVariable String code){return service.verify(code);}
 @GetMapping(value="/{code}/pdf",produces="application/pdf") public ResponseEntity<byte[]> pdf(@PathVariable String code){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=certificate-"+code+".pdf").body(service.pdf(code));}}
