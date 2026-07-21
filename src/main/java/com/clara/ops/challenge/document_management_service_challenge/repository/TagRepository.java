package com.clara.ops.challenge.document_management_service_challenge.repository;

import com.clara.ops.challenge.document_management_service_challenge.domain.TagEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<TagEntity, Long> {
  Optional<TagEntity> findByName(String name);

  /**
   * Inserts a tag by name if it does not already exist, atomically ignoring a concurrent insert of
   * the same name. This avoids the unique-constraint race on {@code tags.name} when parallel
   * uploads share a brand-new tag (the 10-concurrent-uploads requirement).
   */
  // {h-schema} resolves to the configured hibernate.default_schema, so this native query follows
  // the same schema as the JPA-mapped queries instead of hardcoding it and silently diverging.
  @Modifying
  @Query(
      value = "INSERT INTO {h-schema}tags(name) VALUES (:name) ON CONFLICT (name) DO NOTHING",
      nativeQuery = true)
  void insertIfAbsent(@Param("name") String name);
}
