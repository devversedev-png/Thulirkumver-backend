package com.thulirkumver.bookstore.repository;

import com.thulirkumver.bookstore.model.HomepageSlide;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface HomepageSlideRepository extends MongoRepository<HomepageSlide, String> {
    List<HomepageSlide> findAllByOrderByDisplayOrderAscIdAsc();
}
