package com.coherentsolutions.coursecrafter.domain.tag.repository;

import com.coherentsolutions.coursecrafter.domain.tag.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link Tag} entity operations.
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Find a tag by its name.
     *
     * @param name the name of the tag
     * @return the tag if found, or null
     */
    Tag findByName(String name);

    /**
     * Check if a tag with the given name exists.
     *
     * @param name the name to check
     * @return true if a tag with the given name exists
     */
    boolean existsByName(String name);

    /**
     * Find all tags in a specific category.
     *
     * @param category the category to filter by
     * @return list of tags in the specified category
     */
    List<Tag> findByCategory(String category);
}