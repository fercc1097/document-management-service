package com.clara.ops.challenge.document_management_service_challenge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clara.ops.challenge.document_management_service_challenge.service.DocumentService;
import com.clara.ops.challenge.document_management_service_challenge.web.error.GlobalExceptionHandler;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    mvc.perform(
            post("/document-management/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"user\":\"bob\",\"name\":\"f.pdf\",\"tags\":[\"x\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.uploadUrl").value("http://put"));
  }

  @Test
  void uploadRejectsNonPdfName() throws Exception {
    mvc.perform(
            post("/document-management/upload")
                .contentType(MediaType.APPLICATION_JSON)
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
    mvc.perform(
            post("/document-management/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metadata.totalItems").value(0));
  }

  @Test
  void searchHonorsProvidedSort() throws Exception {
    when(service.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
    mvc.perform(
            post("/document-management/search?sort=name,asc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(service).search(any(), any(), any(), pageableCaptor.capture());
    Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("name");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void completeWithMalformedUuidMapsTo400() throws Exception {
    mvc.perform(post("/document-management/upload/not-a-uuid/complete"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void notFoundMapsTo404() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.downloadUrl(id))
        .thenThrow(
            new com.clara.ops.challenge.document_management_service_challenge.exception
                .DocumentNotFoundException("nope"));
    mvc.perform(get("/document-management/download/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
