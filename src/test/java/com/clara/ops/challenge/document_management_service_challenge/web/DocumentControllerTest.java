package com.clara.ops.challenge.document_management_service_challenge.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clara.ops.challenge.document_management_service_challenge.service.DocumentService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.clara.ops.challenge.document_management_service_challenge.web.error.GlobalExceptionHandler;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
class DocumentControllerTest {

  @Autowired MockMvc mvc;
  @MockitoBean DocumentService service;

  @Test
  void uploadReturns201WithIdAndUrl() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.register(eq("bob"), eq("f.pdf"), anyList()))
        .thenReturn(new DocumentService.RegisterResult(id, "http://put"));
    mvc.perform(post("/document-management/upload").contentType(MediaType.APPLICATION_JSON)
            .content("{\"user\":\"bob\",\"name\":\"f.pdf\",\"tags\":[\"x\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uploadUrl").value("http://put"));
  }

  @Test
  void uploadRejectsNonPdfName() throws Exception {
    mvc.perform(post("/document-management/upload").contentType(MediaType.APPLICATION_JSON)
            .content("{\"user\":\"bob\",\"name\":\"f.txt\",\"tags\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void downloadReturnsUrl() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.downloadUrl(id)).thenReturn("http://get");
    mvc.perform(get("/document-management/download/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("http://get"));
  }

  @Test
  void searchReturnsPaginated() throws Exception {
    when(service.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
    mvc.perform(post("/document-management/search").contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.totalItems").value(0));
  }

  @Test
  void notFoundMapsTo404() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.downloadUrl(id)).thenThrow(
        new com.clara.ops.challenge.document_management_service_challenge.exception
            .DocumentNotFoundException("nope"));
    mvc.perform(get("/document-management/download/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
