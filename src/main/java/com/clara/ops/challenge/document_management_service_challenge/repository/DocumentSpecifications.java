package com.clara.ops.challenge.document_management_service_challenge.repository;

import com.clara.ops.challenge.document_management_service_challenge.domain.*;
import jakarta.persistence.criteria.*;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;

public final class DocumentSpecifications {
  private DocumentSpecifications() {}

  public static Specification<DocumentEntity> filter(String user, String name, List<String> tags) {
    return (root, query, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      ps.add(cb.equal(root.get("status"), DocumentStatus.COMPLETED));
      if (user != null && !user.isBlank()) {
        ps.add(cb.equal(root.get("user"), user));
      }
      if (name != null && !name.isBlank()) {
        ps.add(cb.like(cb.lower(root.get("name")), name.toLowerCase() + "%"));
      }
      if (tags != null && !tags.isEmpty()) {
        Join<DocumentEntity, TagEntity> join = root.join("tags", JoinType.INNER);
        ps.add(join.get("name").in(tags));
        query.distinct(true);
      }
      return cb.and(ps.toArray(new Predicate[0]));
    };
  }
}
