package com.TeslaProject.TeslaProject.repository;

import com.TeslaProject.TeslaProject.models.ImageAnalysisResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageAnalysisRepository extends MongoRepository<ImageAnalysisResult, String> {
}