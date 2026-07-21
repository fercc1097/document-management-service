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
        // Case-insensitive prefix match. Escape LIKE metacharacters (% and _) in the user input
        // so a value like "a%" or "a_c" matches literally instead of acting as a wildcard.
        String prefix =
            name.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        ps.add(cb.like(cb.lower(root.get("name")), prefix + "%", '\\'));
      }
      if (tags != null) {
        // AND semantics: a document must carry EVERY requested tag. Each tag becomes a correlated
        // EXISTS subquery, which keeps the main query free of joins/DISTINCT and safe to paginate.
        for (String tag : tags) {
          if (tag == null || tag.isBlank()) {
            continue;
          }
          Subquery<Long> sub = query.subquery(Long.class);
          Root<DocumentEntity> subDoc = sub.from(DocumentEntity.class);
          Join<DocumentEntity, TagEntity> subTags = subDoc.join("tags", JoinType.INNER);
          sub.select(cb.literal(1L));
          sub.where(cb.equal(subDoc, root), cb.equal(subTags.get("name"), tag));
          ps.add(cb.exists(sub));
        }
      }
      return cb.and(ps.toArray(new Predicate[0]));
    };
  }
}
