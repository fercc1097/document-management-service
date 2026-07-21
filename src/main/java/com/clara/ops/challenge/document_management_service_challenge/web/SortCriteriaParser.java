package com.clara.ops.challenge.document_management_service_challenge.web;

import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidDocumentException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Parses Spring-Data sort criteria ({@code property[,direction]}) from the request. Defaults to
 * {@code createdAt} descending when no criteria are supplied; an unparseable direction is a client
 * error and surfaces as {@link InvalidDocumentException} (HTTP 400).
 */
@Component
public class SortCriteriaParser {

  public Sort parse(List<String> sort) {
    if (sort == null || sort.isEmpty()) {
      return Sort.by(Sort.Direction.DESC, "createdAt");
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (String criterion : sort) {
      String[] parts = criterion.split(",", 2);
      String property = parts[0];
      Sort.Direction direction = Sort.Direction.ASC;
      if (parts.length > 1) {
        String dir = parts[1].trim();
        direction =
            Sort.Direction.fromOptionalString(dir)
                .orElseThrow(() -> new InvalidDocumentException("Invalid sort direction: " + dir));
      }
      orders.add(new Sort.Order(direction, property.trim()));
    }
    return Sort.by(orders);
  }
}
