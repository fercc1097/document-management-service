package com.clara.ops.challenge.document_management_service_challenge.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidDocumentException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SortCriteriaParserTest {

  private final SortCriteriaParser parser = new SortCriteriaParser();

  @Test
  void defaultsToCreatedAtDescWhenNull() {
    Sort sort = parser.parse(null);
    Sort.Order order = sort.getOrderFor("createdAt");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void defaultsToCreatedAtDescWhenEmpty() {
    assertThat(parser.parse(List.of()).getOrderFor("createdAt")).isNotNull();
  }

  @Test
  void parsesPropertyAndDirection() {
    Sort.Order order = parser.parse(List.of("name,asc")).getOrderFor("name");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void defaultsDirectionToAscWhenOmitted() {
    Sort.Order order = parser.parse(List.of("name")).getOrderFor("name");
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
  }

  @Test
  void rejectsInvalidDirection() {
    assertThatThrownBy(() -> parser.parse(List.of("name,sideways")))
        .isInstanceOf(InvalidDocumentException.class)
        .hasMessageContaining("Invalid sort direction");
  }
}
