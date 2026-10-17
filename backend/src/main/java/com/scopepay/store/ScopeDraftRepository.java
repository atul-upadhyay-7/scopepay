package com.scopepay.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScopeDraftRepository extends JpaRepository<ScopeDraft, String> {
  List<ScopeDraft> findTop20ByOwnerHashOrderByCreatedAtDesc(String ownerHash);
}
